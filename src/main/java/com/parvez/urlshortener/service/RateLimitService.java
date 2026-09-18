package com.parvez.urlshortener.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Applies atomic, expiring request quotas shared by all application instances. */
public class RateLimitService {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
        local count = tonumber(redis.call('GET', KEYS[1]) or '0')
        if count == 0 then
            redis.call('SET', KEYS[1], 1, 'EX', ARGV[2])
            return {1, tonumber(ARGV[2])}
        end
        local ttl = redis.call('PTTL', KEYS[1])
        if ttl < 0 then
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            ttl = tonumber(ARGV[2]) * 1000
        end
        if count >= tonumber(ARGV[1]) then return {-1, math.max(1, math.ceil(ttl / 1000))} end
        return {redis.call('INCR', KEYS[1]), math.max(1, math.ceil(ttl / 1000))}
        """, List.class);
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final byte[] secret;

    /** Requires a shared secret of at least 32 bytes to pseudonymize client identifiers. */
    public RateLimitService(StringRedisTemplate redis, MeterRegistry metrics, String secret) {
        this.redis = redis;
        this.metrics = metrics;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalArgumentException("Rate-limit secret must contain at least 32 bytes");
    }

    /** Returns a keyed digest; raw client addresses and owner IDs never enter Redis keys. */
    public String identifier(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }

    /** Consumes one request; management fails closed and redirects fail open on store failure. */
    public Decision consume(boolean management, String identity, long limit, long windowSeconds) {
        if (limit < 1 || windowSeconds < 1) throw new IllegalArgumentException("Quota and window must be positive");
        String category = management ? "management" : "redirect";
        try {
            List<?> values = redis.execute(SCRIPT,
                    List.of("rate-limit:v1:" + category + ":" + limit + ":" + windowSeconds + ":" + identifier(identity)),
                    Long.toString(limit), Long.toString(windowSeconds));
            if (values == null || values.size() != 2) throw new IllegalStateException("Missing quota result");
            long count = ((Number) values.get(0)).longValue();
            long reset = ((Number) values.get(1)).longValue();
            boolean allowed = count != -1;
            metrics.counter("rate.limit.requests", "category", category, "outcome", allowed ? "allowed" : "denied").increment();
            return new Decision(allowed, false, allowed ? Math.max(0, limit - count) : 0, reset);
        } catch (RuntimeException ex) {
            metrics.counter("rate.limit.requests", "category", category, "outcome", "unavailable").increment();
            return new Decision(!management, true, 0, 1);
        }
    }

    /** Describes admission and quota metadata without exposing the bucket identity. */
    public record Decision(boolean allowed, boolean unavailable, long remaining, long resetSeconds) {}
}
