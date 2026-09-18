package com.parvez.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.parvez.urlshortener.exception.SafetyScanUnavailableException;
import com.parvez.urlshortener.repository.SafetyAuditRepository;
import com.parvez.urlshortener.safety.AddressResolver;
import com.parvez.urlshortener.safety.SafetyProvider;
import com.parvez.urlshortener.service.DestinationPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Verifies safe defaults and bounded resolver configuration without external services. */
@Tag("unit")
class SafetyConfigTest {
    private final SafetyConfig config = new SafetyConfig();

    @Test
    void addressResolver_literalAddress_returnsResolvedAddress() throws Exception {
        assertThat(config.addressResolver().resolve("127.0.0.1")).hasSize(1)
                .allSatisfy(address -> assertThat(address.isLoopbackAddress()).isTrue());
    }

    @Test
    void destinationPolicy_invalidCidr_rejectsStartup() {
        assertThatThrownBy(() -> config.destinationPolicy(mock(AddressResolver.class), "not-a-cidr"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void safetyProvider_absentEndpoint_neverPermitsActivation() {
        var provider = config.safetyProvider(new ObjectMapper(), "", "", "test", Duration.ofSeconds(2));
        assertThatThrownBy(() -> provider.scan(URI.create("https://example.com/")))
                .isInstanceOf(SafetyScanUnavailableException.class);
    }

    @Test
    void safetyScanService_unboundedTtl_rejectsStartup() {
        assertThatThrownBy(() -> config.safetyScanService(mock(DestinationPolicy.class), mock(SafetyProvider.class),
                mock(StringRedisTemplate.class), mock(SafetyAuditRepository.class),
                new SimpleMeterRegistry(), Duration.ofDays(2))).isInstanceOf(IllegalArgumentException.class);
    }
}
