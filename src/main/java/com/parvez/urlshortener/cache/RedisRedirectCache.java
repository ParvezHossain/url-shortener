package com.parvez.urlshortener.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** Stores redirect destinations in Redis while treating Redis as an optional accelerator. */
@Component
public class RedisRedirectCache implements RedirectCache {

    private static final String KEY_PREFIX = "url-shortener:redirect:";
    private final RedisTemplate<String, RedirectCacheEntry> redisTemplate;
    private final Counter hits;
    private final Counter misses;
    private final Counter evictions;
    private final Counter failures;

    /** Supplies Redis access and low-cardinality cache metrics. */
    public RedisRedirectCache(RedisTemplate<String, RedirectCacheEntry> redisTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.hits = counter(meterRegistry, "hits");
        this.misses = counter(meterRegistry, "misses");
        this.evictions = counter(meterRegistry, "evictions");
        this.failures = counter(meterRegistry, "failures");
    }

    @Override
    public Optional<RedirectCacheEntry> get(String shortCode) {
        try {
            var entry = redisTemplate.opsForValue().get(key(shortCode));
            if (entry == null) {
                misses.increment();
                return Optional.empty();
            }
            hits.increment();
            return Optional.of(entry);
        } catch (RuntimeException ex) {
            failures.increment();
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortCode, RedirectCacheEntry entry, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(shortCode), entry, ttl);
        } catch (RuntimeException ex) {
            failures.increment();
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redisTemplate.delete(key(shortCode));
            evictions.increment();
        } catch (RuntimeException ex) {
            failures.increment();
        }
    }

    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }

    private Counter counter(MeterRegistry registry, String operation) {
        return Counter.builder("url_shortener.redirect_cache." + operation)
                .description("Redirect cache " + operation)
                .register(registry);
    }
}