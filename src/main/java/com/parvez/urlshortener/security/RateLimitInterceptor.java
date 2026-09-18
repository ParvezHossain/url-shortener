package com.parvez.urlshortener.security;

import com.parvez.urlshortener.controller.ApiKeyController;
import com.parvez.urlshortener.controller.QrCodeController;
import com.parvez.urlshortener.controller.RedirectController;
import com.parvez.urlshortener.controller.UrlController;
import com.parvez.urlshortener.controller.V2UrlController;
import com.parvez.urlshortener.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

/** Enforces quotas after authentication and routing, before controller execution. */
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitService service;
    private final ObjectMapper mapper;
    private final long managementLimit;
    private final long redirectLimit;
    private final long window;

    /** Supplies validated deployment policy and the application JSON serializer. */
    public RateLimitInterceptor(RateLimitService service, ObjectMapper mapper,
            long managementLimit, long redirectLimit, long window) {
        if (managementLimit < 1 || redirectLimit < 1 || window < 1) {
            throw new IllegalArgumentException("Rate limits and window must be positive");
        }
        this.service = service;
        this.mapper = mapper;
        this.managementLimit = managementLimit;
        this.redirectLimit = redirectLimit;
        this.window = window;
    }

    /** Adds quota headers or renders a safe ProblemDetail without invoking the controller. */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod method) || "OPTIONS".equals(request.getMethod())) return true;
        Class<?> type = method.getBeanType();
        boolean management = type == UrlController.class || type == V2UrlController.class || type == ApiKeyController.class || type == QrCodeController.class;
        if (!management && type != RedirectController.class) return true;
        OwnerPrincipal owner = (OwnerPrincipal) request.getAttribute("owner");
        String identity = management && owner != null ? "owner:" + owner.ownerId() : "client:" + request.getRemoteAddr();
        long limit = management ? managementLimit : redirectLimit;
        var decision = service.consume(management, identity, limit, window);
        if (!decision.unavailable()) {
            response.setHeader("RateLimit-Limit", Long.toString(limit));
            response.setHeader("RateLimit-Remaining", Long.toString(decision.remaining()));
            response.setHeader("RateLimit-Reset", Long.toString(decision.resetSeconds()));
        }
        if (decision.allowed()) return true;
        HttpStatus status = decision.unavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.TOO_MANY_REQUESTS;
        var problem = ProblemDetail.forStatusAndDetail(status, decision.unavailable()
                ? "Request quota service is temporarily unavailable." : "Request quota exceeded. Retry after the indicated delay.");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setHeader("Retry-After", Long.toString(decision.resetSeconds()));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/problem+json");
        mapper.writeValue(response.getOutputStream(), problem);
        return false;
    }
}
