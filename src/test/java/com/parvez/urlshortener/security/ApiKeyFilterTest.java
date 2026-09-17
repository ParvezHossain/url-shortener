package com.parvez.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.parvez.urlshortener.exception.ApiAuthenticationException;
import com.parvez.urlshortener.service.ApiKeyService;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Verifies v2 path normalization cannot bypass authentication. */
@Tag("unit")
class ApiKeyFilterTest {
    private final ApiKeyService keys = mock(ApiKeyService.class);
    private final HandlerExceptionResolver errors = mock(HandlerExceptionResolver.class);
    private final ApiKeyFilter filter = new ApiKeyFilter(keys, errors);

    @ParameterizedTest
    @ValueSource(strings = {"/api/v2", "/api/v2/urls", "/api/v2/keys/anything", "/api/v2;session=x/urls",
            "/%61pi/v2/urls", "/api//v2/urls"})
    void filter_missingCredentialsOnV2_rejectsBeforeDispatch(String path) throws Exception {
        var failure = new ApiAuthenticationException();
        when(keys.authenticate(null)).thenThrow(failure);
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNull();
        verify(errors).resolveException(any(), any(), isNull(), eq(failure));
    }

    @Test
    void filter_validCredential_passesOwnerOnly() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v2/urls");
        request.addHeader("X-API-Key", "credential");
        var owner = new OwnerPrincipal(UUID.randomUUID(), "prefix");
        when(keys.authenticate("credential")).thenReturn(owner);
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest().getAttribute("owner")).isEqualTo(owner);
    }

    @Test
    void filter_publicRedirect_doesNotAuthenticate() throws Exception {
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/code"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(keys);
    }
}
