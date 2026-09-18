package com.parvez.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies separate limiter instances share one atomic Redis quota under concurrency. */
@Tag("integration")
@Testcontainers
class RateLimitServiceIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    @Test
    void rateLimit_multipleInstances_shareDistributedLimit() throws Exception {
        var first = connection();
        var second = connection();
        try {
            var a = service(first);
            var b = service(second);
            String owner = "owner:" + UUID.randomUUID();
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 100; i++) {
                var instance = i % 2 == 0 ? a : b;
                tasks.add(() -> instance.consume(true, owner, 10, 60).allowed());
            }
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                int allowed = 0;
                for (var result : executor.invokeAll(tasks)) if (result.get()) allowed++;
                assertThat(allowed).isEqualTo(10);
            }
            var template = new StringRedisTemplate(first);
            String key = "rate-limit:v1:management:10:60:" + a.identifier(owner);
            assertThat(template.getExpire(key)).isBetween(1L, 60L);
            // Expire the window deterministically without sleeping.
            template.expire(key, Duration.ZERO);
            assertThat(b.consume(true, owner, 10, 60).allowed()).isTrue();
        } finally {
            first.destroy();
            second.destroy();
        }
    }

    @Test
    void rateLimit_differentOwners_haveIndependentBuckets() {
        var connection = connection();
        try {
            var limiter = service(connection);
            String first = "owner:" + UUID.randomUUID();
            String second = "owner:" + UUID.randomUUID();
            assertThat(limiter.consume(true, first, 1, 60).allowed()).isTrue();
            assertThat(limiter.consume(true, first, 1, 60).allowed()).isFalse();
            assertThat(limiter.consume(true, second, 1, 60).allowed()).isTrue();
            assertThat(limiter.consume(false, first, 1, 60).allowed()).isTrue();
        } finally {
            connection.destroy();
        }
    }

    private LettuceConnectionFactory connection() {
        var factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        return factory;
    }

    private RateLimitService service(LettuceConnectionFactory factory) {
        return new RateLimitService(new StringRedisTemplate(factory), new SimpleMeterRegistry(), "s".repeat(32));
    }
}
