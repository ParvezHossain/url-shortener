package com.parvez.urlshortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.parvez.urlshortener.controller.RedirectController;
import com.parvez.urlshortener.service.UrlShortenerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/** Tests policy validation and registration of the actual request interceptor. */
@Tag("unit")
class RateLimitConfigTest {
    @Test
    void addInterceptors_validPolicy_installsAdmissionControl() throws Exception {
        var registry = new ExposedRegistry();
        config(1, 1, 60).addInterceptors(registry);
        assertThat(registry.entries()).hasSize(1);
        var interceptor = (org.springframework.web.servlet.HandlerInterceptor) registry.entries().getFirst();
        var handler = new HandlerMethod(new RedirectController(mock(UrlShortenerService.class)),
                RedirectController.class.getMethod("redirect", String.class));
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler)).isTrue();
    }

    @Test
    void constructor_nonPositivePolicy_rejectsStartup() {
        assertThatThrownBy(() -> config(0, 1, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(1, -1, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private RateLimitConfig config(long management, long redirect, long window) {
        return new RateLimitConfig(mock(StringRedisTemplate.class), new SimpleMeterRegistry(),
                new JacksonJsonHttpMessageConverter().getMapper(), "s".repeat(32), management, redirect, window);
    }

    private static class ExposedRegistry extends InterceptorRegistry {
        java.util.List<Object> entries() { return getInterceptors(); }
    }
}
