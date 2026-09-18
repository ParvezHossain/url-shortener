package com.parvez.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Tests quota decisions, privacy, and outage policy without a Redis server. */
@Tag("unit")
class RateLimitServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final RateLimitService service = new RateLimitService(redis, metrics, "a".repeat(32));

    @Test
    void consume_withinLimit_returnsRemainingQuota() {
        when(redis.execute(any(RedisScript.class), anyList(), eq("2"), eq("60")))
                .thenReturn(List.of(1L, 60L));
        var result = service.consume(true, "owner:one", 2, 60);
        assertThat(result).isEqualTo(new RateLimitService.Decision(true, false, 1, 60));
        assertThat(metrics.get("rate.limit.requests").tag("outcome", "allowed").counter().count()).isEqualTo(1);
    }

    @Test
    void consume_exhausted_deniesWithoutNegativeRemaining() {
        when(redis.execute(any(RedisScript.class), anyList(), eq("2"), eq("60")))
                .thenReturn(List.of(-1L, 12L));
        assertThat(service.consume(true, "owner:one", 2, 60))
                .isEqualTo(new RateLimitService.Decision(false, false, 0, 12));
    }

    @Test
    void consume_storeUnavailable_appliesEndpointPolicy() {
        when(redis.execute(any(RedisScript.class), anyList(), eq("2"), eq("60")))
                .thenThrow(new IllegalStateException("offline"));
        assertThat(service.consume(true, "owner:one", 2, 60).allowed()).isFalse();
        assertThat(service.consume(false, "client:one", 2, 60).allowed()).isTrue();
        assertThat(metrics.get("rate.limit.requests").tag("category", "management")
                .tag("outcome", "unavailable").counter().count()).isEqualTo(1);
    }

    @Test
    void consume_missingResult_treatsStoreAsUnavailable() {
        assertThat(service.consume(true, "owner:one", 2, 60).unavailable()).isTrue();
    }

    @Test
    void identifier_sameSecret_isStableAndDoesNotExposeAddress() {
        assertThat(service.identifier("client:192.0.2.1")).hasSize(64)
                .isEqualTo(new RateLimitService(redis, metrics, "a".repeat(32)).identifier("client:192.0.2.1"))
                .isNotEqualTo(service.identifier("client:192.0.2.2"))
                .isNotEqualTo(new RateLimitService(redis, metrics, "b".repeat(32)).identifier("client:192.0.2.1"));
    }

    @Test
    void constructor_shortSecret_rejectsConfiguration() {
        assertThatThrownBy(() -> new RateLimitService(redis, metrics, "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consume_invalidPolicy_rejectsConfiguration() {
        assertThatThrownBy(() -> service.consume(true, "owner", 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.consume(true, "owner", 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
