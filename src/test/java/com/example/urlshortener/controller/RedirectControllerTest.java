package com.example.urlshortener.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.exception.UrlExpiredException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies redirect responses and domain error mapping at the HTTP boundary. */
@Tag("unit")
@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    private final MockMvc mvc;

    @MockitoBean
    private UrlShortenerService service;

    @Autowired
    RedirectControllerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void redirect_validCode_returns302WithLocationHeader() throws Exception {
        when(service.resolve("My_link-1")).thenReturn("https://example.com/path?q=1#section");

        mvc.perform(get("/My_link-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/path?q=1#section"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        when(service.resolve("unknown")).thenThrow(new UrlNotFoundException("unknown"));

        mvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No short URL found for code 'unknown'"));
    }

    @Test
    void redirect_expiredCode_returns410() throws Exception {
        when(service.resolve("expired")).thenThrow(new UrlExpiredException("expired"));

        mvc.perform(get("/expired"))
                .andExpect(status().isGone())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.detail").value("Short URL 'expired' has expired"));
    }
}
