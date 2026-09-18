package com.parvez.urlshortener.service;

import com.parvez.urlshortener.domain.SafetyState;
import com.parvez.urlshortener.exception.InvalidUrlException;
import com.parvez.urlshortener.exception.SafetyScanUnavailableException;
import com.parvez.urlshortener.repository.SafetyAuditRepository;
import com.parvez.urlshortener.safety.SafetyProvider;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Coordinates pre-scan policy, expiring verdict reuse, fail-closed decisions and audit. */
public class SafetyScanService {
    private final DestinationPolicy policy;
    private final SafetyProvider provider;
    private final StringRedisTemplate redis;
    private final SafetyAuditRepository audit;
    private final MeterRegistry metrics;
    private final Duration ttl;

    /** Bounds cached reputation freshness between one second and one day. */
    public SafetyScanService(DestinationPolicy policy, SafetyProvider provider, StringRedisTemplate redis,
            SafetyAuditRepository audit, MeterRegistry metrics, Duration ttl) {
        if (ttl.compareTo(Duration.ofSeconds(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Invalid scan cache TTL");
        }
        this.policy = policy;
        this.provider = provider;
        this.redis = redis;
        this.audit = audit;
        this.metrics = metrics;
        this.ttl = ttl;
    }

    /** Validates even on cache hits; failures never become active or enter the verdict cache. */
    public Assessment inspect(String input) {
        URI destination;
        try {
            destination = policy.normalize(input);
        } catch (InvalidUrlException ex) {
            metrics.counter("safety.policy", "outcome", "rejected").increment();
            throw ex;
        } catch (SafetyScanUnavailableException ex) {
            metrics.counter("safety.policy", "outcome", "unavailable").increment();
            throw ex;
        }
        String hash = hash(destination);
        String key = "safety:v1:" + provider.name() + ":" + hash;
        var cached = readCached(key, destination);
        Assessment result = cached.orElseGet(() -> scan(key, destination));
        metrics.counter("safety.scans", "outcome", result.state().name(),
                "source", cached.isPresent() ? "cache" : "provider").increment();
        // Failure to persist an audit must prevent link activation.
        audit.record(hash, result.provider(), result.state(), result.scannedAt(), cached.isPresent());
        return result;
    }

    private Optional<Assessment> readCached(String key, URI destination) {
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                String[] parts = value.split("\\|", 2);
                var state = SafetyState.valueOf(parts[0]);
                var time = Instant.parse(parts[1]);
                var now = Instant.now();
                if ((state == SafetyState.ACTIVE || state == SafetyState.REJECTED)
                        && !time.isAfter(now) && time.plus(ttl).isAfter(now)) {
                    return Optional.of(new Assessment(destination.toASCIIString(), state, provider.name(), time));
                }
            }
        } catch (RuntimeException ex) {
            metrics.counter("safety.cache", "outcome", "failure").increment();
        }
        return Optional.empty();
    }

    private Assessment scan(String key, URI destination) {
        SafetyState state;
        try {
            state = switch (provider.scan(destination)) {
                case SAFE -> SafetyState.ACTIVE;
                case MALICIOUS -> SafetyState.REJECTED;
            };
        } catch (RuntimeException ex) {
            state = SafetyState.SCAN_FAILED;
        }
        var result = new Assessment(destination.toASCIIString(), state, provider.name(), Instant.now());
        if (state != SafetyState.SCAN_FAILED) {
            try {
                redis.opsForValue().set(key, state.name() + "|" + result.scannedAt(), ttl);
            } catch (RuntimeException ex) {
                metrics.counter("safety.cache", "outcome", "failure").increment();
            }
        }
        return result;
    }

    private String hash(URI destination) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(destination.toASCIIString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Carries normalized input and auditable activation metadata. */
    public record Assessment(String destination, SafetyState state, String provider, Instant scannedAt) {}
}
