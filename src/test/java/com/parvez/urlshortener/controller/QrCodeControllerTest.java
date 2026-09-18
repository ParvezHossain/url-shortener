package com.parvez.urlshortener.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parvez.urlshortener.config.ApiSecurityConfig;
import com.parvez.urlshortener.dto.request.QrOptionsRequest;
import com.parvez.urlshortener.dto.response.QrCodeResponse;
import com.parvez.urlshortener.exception.ApiAuthenticationException;
import com.parvez.urlshortener.exception.UrlNotFoundException;
import com.parvez.urlshortener.security.OwnerPrincipal;
import com.parvez.urlshortener.service.ApiKeyService;
import com.parvez.urlshortener.service.QrCodeService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies authentication, option binding, image headers and safe problem responses. */
@Tag("unit")
@WebMvcTest(QrCodeController.class)
@Import(ApiSecurityConfig.class)
class QrCodeControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private QrCodeService qr;
    @MockitoBean private ApiKeyService keys;
    private final OwnerPrincipal owner = new OwnerPrincipal(UUID.randomUUID(), "prefix");

    @BeforeEach
    void setup() {
        when(keys.authenticate("valid")).thenReturn(owner);
        when(keys.authenticate(null)).thenThrow(new ApiAuthenticationException());
    }

    @Test
    void generateQr_existingOwnedCode_returnsExplicitImageHeaders() throws Exception {
        for (var format : QrCodeService.Format.values()) {
            String extension = format.name().toLowerCase(java.util.Locale.ROOT);
            String type = format == QrCodeService.Format.PNG ? "image/png" : "image/svg+xml";
            when(qr.generateQr("owned", owner, new QrOptionsRequest(null, null, null), format))
                    .thenReturn(new QrCodeResponse(new byte[]{1, 2, 3}, type));
            mvc.perform(get("/api/v2/urls/owned/qr." + extension).header("X-API-Key", "valid"))
                    .andExpect(status().isOk()).andExpect(content().contentType(type))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Vary", "X-API-Key"))
                    .andExpect(content().bytes(new byte[]{1, 2, 3}));
        }
    }

    @Test
    void generateQr_invalidOptions_returns400ProblemDetail() throws Exception {
        for (String extension : new String[]{"png", "svg"}) {
            for (String query : new String[]{"size=127", "size=1025", "margin=3", "margin=9", "correction=X", "size=abc", "size=999999999999999"}) {
                mvc.perform(get("/api/v2/urls/owned/qr." + extension + "?" + query).header("X-API-Key", "valid"))
                        .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                        .andExpect(jsonPath("$.status").value(400));
            }
        }
        verifyNoInteractions(qr);
    }

    @Test
    void generateQr_unknownOrForeignCode_doesNotDiscloseResource() throws Exception {
        when(qr.generateQr(any(), eq(owner), any(), any())).thenThrow(new UrlNotFoundException("code"));
        for (String extension : new String[]{"png", "svg"}) {
            mvc.perform(get("/api/v2/urls/code/qr." + extension).header("X-API-Key", "valid"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
    }

    @Test
    void generateQr_missingCredential_returns401BeforeGeneration() throws Exception {
        for (String extension : new String[]{"png", "svg"}) {
            mvc.perform(get("/api/v2/urls/code/qr." + extension))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
        verifyNoInteractions(qr);
    }
}
