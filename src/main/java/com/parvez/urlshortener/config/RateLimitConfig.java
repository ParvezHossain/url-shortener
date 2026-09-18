package com.parvez.urlshortener.config;

import com.parvez.urlshortener.security.RateLimitInterceptor;
import com.parvez.urlshortener.service.RateLimitService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

/** Enables distributed quotas only when an operator supplies deployment policy. */
@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true")
public class RateLimitConfig implements WebMvcConfigurer {
    private final RateLimitInterceptor interceptor;

    /** Fails startup when enabled without valid limits or a shared privacy secret. */
    public RateLimitConfig(StringRedisTemplate redis, MeterRegistry metrics, ObjectMapper mapper,
                           @Value("${app.rate-limit.secret:}") String secret,
                           @Value("${app.rate-limit.management:0}") long management,
                           @Value("${app.rate-limit.redirect:0}") long redirect,
                           @Value("${app.rate-limit.window-seconds:0}") long window) {
        interceptor = new RateLimitInterceptor(new RateLimitService(redis, metrics, secret), mapper,
                management, redirect, window);
    }

    /** Applies admission control to mapped application endpoints. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
