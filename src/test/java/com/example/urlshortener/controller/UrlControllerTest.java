package com.example.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.dto.request.CreateShortUrlRequest;
import com.example.urlshortener.dto.response.ShortUrlResponse;
import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.DuplicateAliasException;
import com.example.urlshortener.service.UrlShortenerService;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies creation status, response mapping, and request validation at the HTTP boundary. */
@Tag("unit")
@WebMvcTest(UrlController.class)
class UrlControllerTest {

    private final MockMvc mvc;

    @MockitoBean
    private UrlShortenerService service;

    @Autowired
    UrlControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void createShortUrl_validRequest_returns201WithLocationAndBody() throws Exception {
        var request = new CreateShortUrlRequest("https://example.com", null, null);
        when(service.create(request)).thenReturn(new ShortUrlResponse("10", "https://sho.rt/10",
                request.originalUrl(), Instant.parse("2026-01-01T00:00:00Z"), null));

        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "https://sho.rt/10"))
                .andExpect(jsonPath("$.shortCode").value("10"))
                .andExpect(jsonPath("$.shortUrl").value("https://sho.rt/10"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"));
    }

    @Test
    void createShortUrl_missingOriginalUrl_returns400() throws Exception {
        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void createShortUrl_serviceThrowsInvalidUrlException_returns400WithProblemDetail() throws Exception {
        when(service.create(any())).thenThrow(new InvalidUrlException("originalUrl is malformed"));

        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("originalUrl is malformed"));
    }
    @Test
    void createShortUrl_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
        verifyNoInteractions(service);
    }

    @Test
    void createShortUrl_validCustomAlias_returns201() throws Exception {
        var request = new CreateShortUrlRequest("https://example.com", "My_link-1", null);
        when(service.create(request)).thenReturn(new ShortUrlResponse("My_link-1", "https://sho.rt/My_link-1",
                request.originalUrl(), Instant.parse("2026-01-01T00:00:00Z"), null));

        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"My_link-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "https://sho.rt/My_link-1"))
                .andExpect(jsonPath("$.shortCode").value("My_link-1"));
    }

    @Test
    void createShortUrl_duplicateAlias_returns409() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateAliasException("taken"));

        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Alias 'taken' is already taken"));
    }

    @Test
    void createShortUrl_invalidAliasPattern_returns400() throws Exception {
        mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"bad alias\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0]").value(
                        "customAlias: must contain 3 to 16 letters, digits, underscores, or hyphens"));
        verifyNoInteractions(service);
    }

}
