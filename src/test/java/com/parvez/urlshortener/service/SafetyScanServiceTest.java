package com.parvez.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

import com.parvez.urlshortener.domain.SafetyState;
import com.parvez.urlshortener.exception.InvalidUrlException;
import com.parvez.urlshortener.repository.SafetyAuditRepository;
import com.parvez.urlshortener.safety.SafetyProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Verifies verdict reuse, auditing and fail-closed behavior. */
@Tag("unit")
class SafetyScanServiceTest {
    private final DestinationPolicy policy = mock(DestinationPolicy.class);
    private final SafetyProvider provider = mock(SafetyProvider.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SafetyAuditRepository audit = mock(SafetyAuditRepository.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final URI destination = URI.create("https://example.com/");
    private final Duration ttl = Duration.ofMinutes(15);
    private SafetyScanService service;

    @BeforeEach
    void setUp() {
        when(policy.normalize("input")).thenReturn(destination);
        when(provider.name()).thenReturn("test-v1");
        when(redis.opsForValue()).thenReturn(values);
        service = new SafetyScanService(policy, provider, redis, audit, metrics, ttl);
    }

    @Test
    void inspect_safeVerdict_activatesCachesAndAudits() {
        when(provider.scan(destination)).thenReturn(SafetyProvider.Verdict.SAFE);
        var result = service.inspect("input");
        assertThat(result.state()).isEqualTo(SafetyState.ACTIVE);
        assertThat(result.destination()).isEqualTo(destination.toString());
        verify(values).set(matches("safety:v1:test-v1:[a-f0-9]{64}"), eq("ACTIVE|" + result.scannedAt()), eq(ttl));
        verify(audit).record(matches("[a-f0-9]{64}"), eq("test-v1"), eq(SafetyState.ACTIVE), eq(result.scannedAt()), eq(false));
    }

    @Test
    void inspect_maliciousVerdict_rejectsAndCaches() {
        when(provider.scan(destination)).thenReturn(SafetyProvider.Verdict.MALICIOUS);
        assertThat(service.inspect("input").state()).isEqualTo(SafetyState.REJECTED);
        verify(values).set(anyString(), startsWith("REJECTED|"), eq(ttl));
    }

    @Test
    void inspect_providerFailure_failsClosedWithoutCaching() {
        when(provider.scan(destination)).thenThrow(new IllegalStateException("provider secret"));
        var result = service.inspect("input");
        assertThat(result.state()).isEqualTo(SafetyState.SCAN_FAILED);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(audit).record(anyString(), eq("test-v1"), eq(SafetyState.SCAN_FAILED), eq(result.scannedAt()), eq(false));
        assertThat(metrics.get("safety.scans").tag("outcome", "SCAN_FAILED").counter().count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "REJECTED"})
    void inspect_cachedVerdict_preservesOriginalScanTime(String state) {
        var scannedAt = Instant.now().minusSeconds(30);
        when(values.get(anyString())).thenReturn(state + "|" + scannedAt);
        var result = service.inspect("input");
        assertThat(result.scannedAt()).isEqualTo(scannedAt);
        assertThat(result.state()).isEqualTo(SafetyState.valueOf(state));
        verify(provider, never()).scan(any());
        verify(policy).normalize("input");
        verify(audit).record(anyString(), eq("test-v1"), eq(result.state()), eq(scannedAt), eq(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "ACTIVE|2000-01-01T00:00:00Z", "ACTIVE|2999-01-01T00:00:00Z", "SCAN_FAILED|2000-01-01T00:00:00Z"})
    void inspect_invalidOrExpiredCache_rescans(String cached) {
        when(values.get(anyString())).thenReturn(cached);
        when(provider.scan(destination)).thenReturn(SafetyProvider.Verdict.SAFE);
        assertThat(service.inspect("input").state()).isEqualTo(SafetyState.ACTIVE);
        verify(provider).scan(destination);
    }

    @Test
    void inspect_cacheUnavailable_stillScansAndAudits() {
        when(values.get(anyString())).thenThrow(new IllegalStateException());
        doThrow(new IllegalStateException()).when(values).set(anyString(), anyString(), any(Duration.class));
        when(provider.scan(destination)).thenReturn(SafetyProvider.Verdict.SAFE);
        assertThat(service.inspect("input").state()).isEqualTo(SafetyState.ACTIVE);
        assertThat(metrics.get("safety.cache").counter().count()).isEqualTo(2);
        verify(audit).record(anyString(), anyString(), eq(SafetyState.ACTIVE), any(), eq(false));
    }

    @Test
    void inspect_policyRejection_neverCallsProviderOrCache() {
        when(policy.normalize("input")).thenThrow(new InvalidUrlException("blocked"));
        assertThatThrownBy(() -> service.inspect("input")).isInstanceOf(InvalidUrlException.class);
        verify(provider, never()).scan(any());
        verify(values, never()).get(anyString());
    }

    @Test
    void constructor_unboundedTtl_rejectsConfiguration() {
        for (var invalid : new Duration[] {Duration.ZERO, Duration.ofDays(2)}) {
            assertThatThrownBy(() -> new SafetyScanService(policy, provider, redis, audit, metrics, invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test
    void inspect_dnsUnavailable_recordsPolicyFailureWithoutProviderCall() {
        when(policy.normalize("input")).thenThrow(new com.parvez.urlshortener.exception.SafetyScanUnavailableException());
        assertThatThrownBy(() -> service.inspect("input"))
                .isInstanceOf(com.parvez.urlshortener.exception.SafetyScanUnavailableException.class);
        verify(provider, never()).scan(any());
        assertThat(metrics.get("safety.policy").tag("outcome", "unavailable").counter().count()).isEqualTo(1);
    }

    @Test
    void inspect_auditUnavailable_preventsActivation() {
        when(provider.scan(destination)).thenReturn(SafetyProvider.Verdict.SAFE);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"))
                .when(audit).record(anyString(), anyString(), any(), any(), anyBoolean());
        assertThatThrownBy(() -> service.inspect("input"))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }

}
