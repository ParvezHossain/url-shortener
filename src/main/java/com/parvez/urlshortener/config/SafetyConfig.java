package com.parvez.urlshortener.config;

import com.parvez.urlshortener.repository.SafetyAuditRepository;
import com.parvez.urlshortener.safety.AddressResolver;
import com.parvez.urlshortener.safety.HttpSafetyProvider;
import com.parvez.urlshortener.safety.SafetyProvider;
import com.parvez.urlshortener.service.DestinationPolicy;
import com.parvez.urlshortener.service.SafetyScanService;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Wires the scanner and address policy without any permissive production fallback. */
@Configuration
public class SafetyConfig {
    /** Uses a bounded DNS wait before any provider request. */
    @Bean public AddressResolver addressResolver() {
        return host -> {
            var task = new java.util.concurrent.FutureTask<InetAddress[]>(() -> InetAddress.getAllByName(host));
            Thread.ofVirtual().start(task);
            try { return task.get(2, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new java.net.UnknownHostException(); }
            catch (Exception ex) { throw new java.net.UnknownHostException(); }
            finally { task.cancel(true); }
        };
    }
    /** Normalizes URLs and blocks configured internal networks. */
    @Bean public DestinationPolicy destinationPolicy(AddressResolver resolver,
            @Value("${app.safety.blocked-cidrs:}") String cidrs) { return new DestinationPolicy(resolver, cidrs); }
    /** Installs the operator-configured JSON scanner; an absent endpoint fails closed. */
    @Bean public SafetyProvider safetyProvider(ObjectMapper json,
            @Value("${app.safety.endpoint:}") String endpoint, @Value("${app.safety.token:}") String token,
            @Value("${app.safety.provider:http-v1}") String name, @Value("${app.safety.timeout:PT2S}") Duration timeout) {
        return new HttpSafetyProvider(json, endpoint, token, name, timeout);
    }
    /** Applies shared verdict caching, durable audit and low-cardinality metrics. */
    @Bean public SafetyScanService safetyScanService(DestinationPolicy policy, SafetyProvider provider,
            StringRedisTemplate redis, SafetyAuditRepository audit, MeterRegistry metrics,
            @Value("${app.safety.cache-ttl:PT15M}") Duration ttl) {
        return new SafetyScanService(policy, provider, redis, audit, metrics, ttl);
    }
}
