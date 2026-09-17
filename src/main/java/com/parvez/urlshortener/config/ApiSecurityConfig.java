package com.parvez.urlshortener.config;

import com.parvez.urlshortener.security.ApiKeyFilter;
import com.parvez.urlshortener.service.ApiKeyService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Registers authentication for the complete v2 HTTP boundary. */
@Configuration
public class ApiSecurityConfig {
    /** Installs the filter using the shared error response renderer. */
    @Bean
    public ApiKeyFilter apiKeyFilter(ApiKeyService keys,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
        return new ApiKeyFilter(keys, errors);
    }
}
