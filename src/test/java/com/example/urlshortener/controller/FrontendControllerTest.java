package com.example.urlshortener.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Checks the entry-point response contract independently of the web server. */
@Tag("unit")
class FrontendControllerTest {

    @Test
    void config_configuredPublicUrl_returnsDeploymentAddress() {
        var response = new FrontendController("https://links.example.test").config();

        assertThat(response.publicBaseUrl()).isEqualTo("https://links.example.test");
    }

    @Test
    void index_packagedFrontend_returnsHtmlWithRevalidation() throws Exception {
        var response = new FrontendController("https://links.example.test").index();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("<script nonce=", "id=\"root\"");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy"))
                .contains("strict-dynamic", "frame-ancestors 'none'")
                .doesNotContain("unsafe-inline", "unsafe-eval");
        assertThat(new FrontendController("https://links.example.test").index().getHeaders()
                .getFirst("Content-Security-Policy"))
                .isNotEqualTo(response.getHeaders().getFirst("Content-Security-Policy"));
    }
}
