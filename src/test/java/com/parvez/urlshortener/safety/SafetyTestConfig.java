package com.parvez.urlshortener.safety;

import java.net.InetAddress;
import java.net.URI;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/** Supplies deterministic scanner and DNS fixtures only to explicitly importing tests. */
@TestConfiguration(proxyBeanMethods = false)
public class SafetyTestConfig {
    /** Avoids network reputation calls while preserving the real scan orchestration. */
    @Bean @Primary
    public SafetyProvider testSafetyProvider() {
        return new SafetyProvider() {
            public String name() { return "test-v1"; }
            public Verdict scan(URI destination) {
                if (destination.getPath().contains("timeout")) throw new IllegalStateException("provider secret");
                return destination.getPath().contains("malicious") ? Verdict.MALICIOUS : Verdict.SAFE;
            }
        };
    }

    /** Resolves fixtures to a public literal without relying on external DNS. */
    @Bean @Primary
    public AddressResolver testAddressResolver() {
        return host -> new InetAddress[] {InetAddress.getByName("8.8.8.8")};
    }

    /** Keeps scanner tests independent of a separately running Redis server. */
    @Bean @Primary
    public StringRedisTemplate testSafetyRedis() {
        var redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        return redis;
    }
}
