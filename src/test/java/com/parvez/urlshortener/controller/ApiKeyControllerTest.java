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
@WebMvcTest(ApiKeyController.class)
@Import(ApiSecurityConfig.class)
class ApiKeyControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ApiKeyService keys;
    private final OwnerPrincipal owner = new OwnerPrincipal(UUID.randomUUID(), "prefix");

    @BeforeEach
    void setUp() {
        when(keys.authenticate(null)).thenThrow(new ApiAuthenticationException());
        when(keys.authenticate("invalid")).thenThrow(new ApiAuthenticationException());
        when(keys.authenticate("valid")).thenReturn(owner);
    }

    @Test
    void rotate_currentKey_returnsSecretOnceWithoutCaching() throws Exception {
        when(keys.rotate(owner, "prefix")).thenReturn(new ApiKeyResponse("replacement", "next"));
        mvc.perform(post("/api/v2/keys/prefix/rotate").header("X-API-Key", "valid"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.apiKey").value("replacement"));
    }

    @Test
    void rotate_otherKey_returns403() throws Exception {
        when(keys.rotate(owner, "other")).thenThrow(new ApiPermissionException());
        mvc.perform(post("/api/v2/keys/other/rotate").header("X-API-Key", "valid"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void revoke_currentKey_returns204() throws Exception {
        mvc.perform(delete("/api/v2/keys/prefix").header("X-API-Key", "valid"))
                .andExpect(status().isNoContent());
        verify(keys).revoke(owner, "prefix");
    }

}
