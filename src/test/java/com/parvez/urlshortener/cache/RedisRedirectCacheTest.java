package com.parvez.urlshortener.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Verifies Redis failures remain transparent to redirect resolution. */
class RedisRedirectCacheTest {

    @Test
    void get_redisUnavailable_returnsEmpty() {
        var template = mock(RedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis is down"));
        var cache = new RedisRedirectCache(template, new SimpleMeterRegistry());

        assertThat(cache.get("code")).isEqualTo(Optional.empty());
    }

    @Test
    void put_redisUnavailable_doesNotThrow() {
        var template = mock(RedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("Redis is down"))
            .when(values).set(anyString(), any(), any(Duration.class));
        var cache = new RedisRedirectCache(template, new SimpleMeterRegistry());

        cache.put("code", new RedirectCacheEntry("https://example.com", null), Duration.ofMinutes(1));
    }

    @Test
    void evict_redisUnavailable_doesNotThrow() {
        var template = mock(RedisTemplate.class);
        when(template.delete(anyString())).thenThrow(new IllegalStateException("Redis is down"));
        var cache = new RedisRedirectCache(template, new SimpleMeterRegistry());

        cache.evict("code");
    }
}