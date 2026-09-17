package com.parvez.urlshortener.security;

import com.parvez.urlshortener.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Authenticates every v2 request before controller routing, including unknown routes. */
@Order(1)
public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyService keys;
    private final HandlerExceptionResolver errors;

    /** Supplies authentication and the shared ProblemDetail renderer. */
    public ApiKeyFilter(ApiKeyService keys,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
        this.keys = keys;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = org.springframework.web.util.UrlPathHelper.defaultInstance
                .getPathWithinApplication(request).replaceAll("/{2,}", "/");
        if (path.equals("/api/v2") || path.startsWith("/api/v2/")) {
            response.setHeader("Cache-Control", "no-store");

            // Let CORS preflight through without authentication
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                chain.doFilter(request, response);
                return;
            }

            try {
                var headers = request.getHeaders("X-API-Key");
                String raw = headers.hasMoreElements() ? headers.nextElement() : null;

                if (headers.hasMoreElements()) raw = null;
                request.setAttribute("owner", keys.authenticate(raw));
            } catch (RuntimeException ex) {
                errors.resolveException(request, response, null, ex);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
