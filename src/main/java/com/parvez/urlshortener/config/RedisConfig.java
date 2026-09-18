package com.parvez.urlshortener.config;

import com.parvez.urlshortener.cache.RedirectCacheEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Configures typed, JSON-serialized redirect cache entries. */
@Configuration
public class RedisConfig {

    /** Creates a Redis template with stable string keys and JSON values. */
    @Bean
    RedisTemplate<String, RedirectCacheEntry> redirectRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        var template = new RedisTemplate<String, RedirectCacheEntry>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        var valueSerializer = new JacksonJsonRedisSerializer<>(RedirectCacheEntry.class);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}