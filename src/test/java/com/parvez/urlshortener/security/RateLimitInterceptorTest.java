package com.parvez.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.parvez.urlshortener.controller.RedirectController;
import com.parvez.urlshortener.controller.UrlController;
import com.parvez.urlshortener.controller.V2UrlController;
import com.parvez.urlshortener.dto.request.CreateShortUrlRequest;
import com.parvez.urlshortener.service.RateLimitService;
import com.parvez.urlshortener.service.UrlShortenerService;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;

/** Verifies HTTP admission and safe response metadata before business execution. */
@Tag("unit")
class RateLimitInterceptorTest {
    private final RateLimitService service = mock(RateLimitService.class);
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(service,
            new JacksonJsonHttpMessageConverter().getMapper(), 2, 10, 60);

    @Test
    void create_withinLimit_succeeds() throws Exception {
        var owner = new OwnerPrincipal(UUID.randomUUID(), "prefix");
        when(service.consume(true, "owner:" + owner.ownerId(), 2, 60))
                .thenReturn(new RateLimitService.Decision(true, false, 1, 60));
        var urls = mock(UrlShortenerService.class);
        when(urls.create(any(), eq(owner))).thenReturn(new com.parvez.urlshortener.dto.response.ShortUrlResponse(
                "abc", "http://localhost/abc", "https://example.com", java.time.Instant.now(), null));
        var mvc = MockMvcBuilders.standaloneSetup(new V2UrlController(urls))
                .addInterceptors(interceptor).build();
        mvc.perform(post("/api/v2/urls").requestAttr("owner", owner).contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("RateLimit-Remaining", "1"));
    }

    @Test
    void create_limitExceeded_returns429WithRetryAfter() throws Exception {
        when(service.consume(eq(true), any(), eq(2L), eq(60L)))
                .thenReturn(new RateLimitService.Decision(false, false, 0, 35));
        var urls = mock(UrlShortenerService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UrlController(urls)).addInterceptors(interceptor).build();
        mvc.perform(post("/api/v1/urls").contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "35"))
                .andExpect(header().string("RateLimit-Limit", "2"))
                .andExpect(header().string("RateLimit-Remaining", "0"))
                .andExpect(header().string("RateLimit-Reset", "35"))
                .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.instance").value("/api/v1/urls"));
        verifyNoInteractions(urls);
    }

    @Test
    void preHandle_ownerPresent_usesOwnerAcrossKeys() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.consume(true, "owner:" + id, 2, 60))
                .thenReturn(new RateLimitService.Decision(true, false, 1, 60));
        var controller = new V2UrlController(mock(UrlShortenerService.class));
        var handler = new HandlerMethod(controller, V2UrlController.class.getMethod("create",
                OwnerPrincipal.class, CreateShortUrlRequest.class));
        var request = new MockHttpServletRequest();
        request.setAttribute("owner", new OwnerPrincipal(id, "key-one"));
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        request.setAttribute("owner", new OwnerPrincipal(id, "key-two"));
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        verify(service, org.mockito.Mockito.times(2)).consume(true, "owner:" + id, 2, 60);
    }

    @Test
    void preHandle_redirect_ignoresSpoofedForwardedAddress() throws Exception {
        when(service.consume(false, "client:192.0.2.1", 10, 60))
                .thenReturn(new RateLimitService.Decision(true, true, 0, 1));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        var handler = new HandlerMethod(new RedirectController(mock(UrlShortenerService.class)),
                RedirectController.class.getMethod("redirect", String.class));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, handler)).isTrue();
        assertThat(response.getHeader("RateLimit-Limit")).isNull();
        verify(service).consume(false, "client:192.0.2.1", 10, 60);
    }

    @Test
    void preHandle_managementStoreUnavailable_returns503() throws Exception {
        when(service.consume(eq(true), any(), eq(2L), eq(60L)))
                .thenReturn(new RateLimitService.Decision(false, true, 0, 1));
        var mvc = MockMvcBuilders.standaloneSetup(new UrlController(mock(UrlShortenerService.class)))
                .addInterceptors(interceptor).build();
        mvc.perform(post("/api/v1/urls")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1")).andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void preHandle_staticResources_areExcluded() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void create_authenticatedV2_usesOwnerQuotaAfterAuthentication() throws Exception {
        var owner = new OwnerPrincipal(UUID.randomUUID(), "prefix");
        var keys = mock(com.parvez.urlshortener.service.ApiKeyService.class);
        when(keys.authenticate("credential")).thenReturn(owner);
        when(service.consume(true, "owner:" + owner.ownerId(), 2, 60))
                .thenReturn(new RateLimitService.Decision(false, false, 0, 10));
        var urls = mock(UrlShortenerService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new V2UrlController(urls))
                .addFilters(new ApiKeyFilter(keys, mock(org.springframework.web.servlet.HandlerExceptionResolver.class)))
                .addInterceptors(interceptor).build();
        mvc.perform(post("/api/v2/urls").header("X-API-Key", "credential")
                        .contentType("application/json").content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "10"));
        verifyNoInteractions(urls);
    }

    @Test
    void preHandle_preflight_doesNotConsumeQuota() throws Exception {
        var handler = new HandlerMethod(new RedirectController(mock(UrlShortenerService.class)),
                RedirectController.class.getMethod("redirect", String.class));
        assertThat(interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/abc"),
                new MockHttpServletResponse(), handler)).isTrue();
        verifyNoInteractions(service);
    }


    @Test
    void preHandle_qrGeneration_consumesOwnerManagementQuota() throws Exception {
        var owner = new OwnerPrincipal(UUID.randomUUID(), "key");
        when(service.consume(true, "owner:" + owner.ownerId(), 2, 60))
                .thenReturn(new RateLimitService.Decision(false, false, 0, 10));
        var request = new MockHttpServletRequest("GET", "/api/v2/urls/owned/qr.png");
        request.setAttribute("owner", owner);
        var controller = new com.parvez.urlshortener.controller.QrCodeController(
                mock(com.parvez.urlshortener.service.QrCodeService.class));
        var handler = new HandlerMethod(controller, controller.getClass().getMethod("png", String.class,
                OwnerPrincipal.class, com.parvez.urlshortener.dto.request.QrOptionsRequest.class));
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, handler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }

}
