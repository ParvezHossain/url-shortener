package com.parvez.urlshortener.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Advertises v1 deprecation and enforces an optional operator-configured sunset. */
@Component
public class V1DeprecationFilter extends OncePerRequestFilter {
    private static final tools.jackson.databind.json.JsonMapper JSON = new tools.jackson.databind.json.JsonMapper();
    private final Instant sunset;
    /** Parses an optional ISO-8601 retirement timestamp; empty leaves legacy access enabled. */
    public V1DeprecationFilter(@Value("${app.v1-sunset:}") String sunset) {
        this.sunset = sunset.isBlank() ? Instant.MAX : Instant.parse(sunset);
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = org.springframework.web.util.UrlPathHelper.defaultInstance
                .getPathWithinApplication(request).replaceAll("/{2,}", "/");
        if (path.equals("/api/v1/urls") || path.startsWith("/api/v1/urls/")) {
            response.setHeader("Deprecation", "@1789603200");
            response.setHeader("Cache-Control", "no-store");
            if (!sunset.equals(Instant.MAX)) {
                response.setHeader("Sunset", DateTimeFormatter.RFC_1123_DATE_TIME.format(sunset.atOffset(ZoneOffset.UTC)));
            }
            if (!Instant.now().isBefore(sunset)) {
                response.setStatus(410);
                response.setContentType("application/problem+json");
                response.getWriter().write(JSON.writeValueAsString(java.util.Map.of(
                        "type", "about:blank", "title", "Gone", "status", 410,
                        "detail", "API v1 management has retired; migrate to API v2",
                        "instance", request.getRequestURI())));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
