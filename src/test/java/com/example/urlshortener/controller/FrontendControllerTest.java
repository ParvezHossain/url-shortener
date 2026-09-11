package com.example.urlshortener.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Checks the entry-point response contract independently of the web server. */
@Tag("unit")
class FrontendControllerTest {

    @Test
    void index_packagedFrontend_returnsHtmlWithRevalidation() {
        var response = new FrontendController().index();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFilename()).isEqualTo("index.html");
    }
}
