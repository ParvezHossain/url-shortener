package com.parvez.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Verifies legacy deprecation and configured retirement leave redirects available. */
@Tag("unit")
class V1DeprecationFilterTest {
    @Test
    void filter_noSunset_advertisesDeprecationAndContinues() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new V1DeprecationFilter("").doFilter(new MockHttpServletRequest("GET", "/api/v1/urls/code"), response, chain);
        assertThat(response.getHeader("Deprecation")).startsWith("@");
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
    @Test
    void filter_pastSunset_returns410WithoutCallingController() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new V1DeprecationFilter("2020-01-01T00:00:00Z").doFilter(
                new MockHttpServletRequest("POST", "/api/v1/urls"), response, chain);
        assertThat(response.getStatus()).isEqualTo(410);
        assertThat(response.getHeader("Sunset")).contains("2020");
        assertThat(response.getContentAsString()).contains("migrate to API v2");
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(response.getContentAsString(), "$.instance"))
                .isEqualTo("/api/v1/urls");
        assertThat(chain.getRequest()).isNull();
    }
    @Test
    void filter_publicRedirectAfterSunset_continues() throws Exception {
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();
        new V1DeprecationFilter("2020-01-01T00:00:00Z").doFilter(
                new MockHttpServletRequest("GET", "/code"), response, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader("Deprecation")).isNull();
    }
}
