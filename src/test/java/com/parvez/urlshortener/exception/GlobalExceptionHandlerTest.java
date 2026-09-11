package com.parvez.urlshortener.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parvez.urlshortener.controller.RedirectController;
import com.parvez.urlshortener.controller.UrlController;
import com.parvez.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Verifies the shared problem response contract through the HTTP exception pipeline. */
@Tag("unit")
@WebMvcTest({UrlController.class, RedirectController.class})
class GlobalExceptionHandlerTest {

    private final MockMvc mvc;

    @MockitoBean
    private UrlShortenerService service;

    @Autowired
    GlobalExceptionHandlerTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void handleInvalidUrlException_returns400ProblemDetail() throws Exception {
        when(service.create(any())).thenThrow(new InvalidUrlException("originalUrl is malformed"));

        var result = mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"invalid\"}"));

        assertProblem(result, 400, "Bad Request", "originalUrl is malformed", "/api/v1/urls");
    }

    @Test
    void handleDuplicateAliasException_returns409ProblemDetail() throws Exception {
        when(service.create(any())).thenThrow(new DuplicateAliasException("taken"));

        var result = mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"taken\"}"));

        assertProblem(result, 409, "Conflict", "Alias 'taken' is already taken", "/api/v1/urls");
    }

    @Test
    void handleUrlNotFoundException_returns404ProblemDetail() throws Exception {
        when(service.getStats("unknown")).thenThrow(new UrlNotFoundException("unknown"));

        var result = mvc.perform(get("/api/v1/urls/unknown"));

        assertProblem(result, 404, "Not Found", "No short URL found for code 'unknown'", "/api/v1/urls/unknown");
    }

    @Test
    void handleUrlExpiredException_returns410ProblemDetail() throws Exception {
        when(service.resolve("expired")).thenThrow(new UrlExpiredException("expired"));

        var result = mvc.perform(get("/expired"));

        assertProblem(result, 410, "Gone", "Short URL 'expired' has expired", "/expired");
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() throws Exception {
        var result = mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"\",\"customAlias\":\"private alias value\"}"));

        assertProblem(result, 400, "Bad Request", "Request validation failed", "/api/v1/urls");
        result.andExpect(jsonPath("$.errors", org.hamcrest.Matchers.containsInAnyOrder(
                        "originalUrl: must not be blank",
                        "customAlias: must contain 3 to 16 letters, digits, underscores, or hyphens")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private alias value"))));
        verifyNoInteractions(service);
    }

    @Test
    void handleUnexpectedException_returns500WithoutLeakingStackTrace() throws Exception {
        when(service.getStats("broken")).thenThrow(new IllegalStateException(
                "private database credentials", new java.sql.SQLException("private SQL statement")));

        var result = mvc.perform(get("/api/v1/urls/broken"));

        assertProblem(result, 500, "Internal Server Error", "An unexpected error occurred", "/api/v1/urls/broken");
        result.andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(5)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"originalUrl\":\"https://example.com\",\"expiresAt\":\"private-invalid-date\"}"})
    void handleUnreadableRequest_returns400WithoutParserDetails(String body) throws Exception {
        var result = mvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(body));

        assertProblem(result, 400, "Bad Request", "Request body is missing or malformed", "/api/v1/urls");
        result.andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(5)));
        verifyNoInteractions(service);
    }

    private void assertProblem(ResultActions result, int expectedStatus, String title, String detail, String path)
            throws Exception {
        result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(path));
    }
}
