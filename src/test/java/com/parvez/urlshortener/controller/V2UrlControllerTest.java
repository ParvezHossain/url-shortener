package com.parvez.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.parvez.urlshortener.config.ApiSecurityConfig;
import com.parvez.urlshortener.dto.response.ShortUrlResponse;
import com.parvez.urlshortener.dto.response.ShortUrlStatsResponse;
import com.parvez.urlshortener.dto.response.UrlPageResponse;
import com.parvez.urlshortener.dto.response.ApiKeyResponse;
import com.parvez.urlshortener.exception.ApiAuthenticationException;
import com.parvez.urlshortener.exception.ApiPermissionException;
import com.parvez.urlshortener.exception.UrlNotFoundException;
import com.parvez.urlshortener.security.OwnerPrincipal;
import com.parvez.urlshortener.service.ApiKeyService;
import com.parvez.urlshortener.service.UrlShortenerService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies authentication runs before every v2 management route. */
@Tag("unit")
@WebMvcTest({V2UrlController.class, ApiKeyController.class})
@Import(ApiSecurityConfig.class)
class V2UrlControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ApiKeyService keys;
    @MockitoBean private UrlShortenerService urls;
    private final OwnerPrincipal owner = new OwnerPrincipal(UUID.randomUUID(), "prefix");

    @BeforeEach
    void setUp() {
        when(keys.authenticate(null)).thenThrow(new ApiAuthenticationException());
        when(keys.authenticate("invalid")).thenThrow(new ApiAuthenticationException());
        when(keys.authenticate("valid")).thenReturn(owner);
    }

    @Test
    void management_missingOrInvalidKey_returns401Problem() throws Exception {
        for (var request : List.of(get("/api/v2/urls"), post("/api/v2/urls"),
                get("/api/v2/urls/code"), delete("/api/v2/urls/code"), get("/api/v2/urls/unknown/nested"))) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
        mvc.perform(get("/api/v2/urls").header("X-API-Key", "invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Valid API key required"));
        verifyNoInteractions(urls);
    }

    @Test
    void create_authenticatedOwner_passesPrincipalAndReturnsCreated() throws Exception {
        when(urls.create(any(), eq(owner))).thenReturn(new ShortUrlResponse("code", "https://sho.rt/code",
                "https://example.com", Instant.now(), null));
        mvc.perform(post("/api/v2/urls").header("X-API-Key", "valid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.shortCode").value("code"));
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        mvc.perform(post("/api/v2/urls").header("X-API-Key", "valid").contentType(MediaType.APPLICATION_JSON)
                .content("{}")) .andExpect(status().isBadRequest());
        verifyNoInteractions(urls);
    }

    @Test
    void stats_ownedCode_returnsMetadata() throws Exception {
        when(urls.getStats("code", owner)).thenReturn(new ShortUrlStatsResponse("code", "https://example.com",
                Instant.now(), null, 0, null, "https://sho.rt/code", false));
        mvc.perform(get("/api/v2/urls/code").header("X-API-Key", "valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shortCode").value("code"));
    }

    @Test
    void stats_foreignCode_returns404() throws Exception {
        when(urls.getStats("foreign", owner)).thenThrow(new UrlNotFoundException("foreign"));
        mvc.perform(get("/api/v2/urls/foreign").header("X-API-Key", "valid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_authenticatedOwner_returnsPage() throws Exception {
        when(urls.list(owner, 0, 20)).thenReturn(new UrlPageResponse(List.of(), 0, 20, 0));
        mvc.perform(get("/api/v2/urls").header("X-API-Key", "valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void delete_ownedCode_returns204() throws Exception {
        mvc.perform(delete("/api/v2/urls/code").header("X-API-Key", "valid"))
                .andExpect(status().isNoContent());
        verify(urls).delete("code", owner);
    }

    @Test
    void list_malformedPage_returns400() throws Exception {
        mvc.perform(get("/api/v2/urls?page=abc").header("X-API-Key", "valid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Invalid request parameter"));
        verifyNoInteractions(urls);
    }

    @Test
    void management_duplicateHeaders_returns401() throws Exception {
        mvc.perform(get("/api/v2/urls").header("X-API-Key", "valid", "valid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(urls);
    }
}
