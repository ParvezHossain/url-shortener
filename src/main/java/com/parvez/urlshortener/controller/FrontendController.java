package com.parvez.urlshortener.controller;

import com.parvez.urlshortener.dto.response.FrontendConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the frontend entry point without forwarding into the short-code route. */
@Hidden
@RestController
public class FrontendController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final String publicBaseUrl;

    /** Uses the same configured public address as generated short links. */
    public FrontendController(@Value("${app.base-url}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    /** Exposes public presentation settings without environment-specific frontend builds. */
    @GetMapping(value = "/ui/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public FrontendConfigResponse config() {
        return new FrontendConfigResponse(publicBaseUrl);
    }


    /** Returns the packaged HTML entry point, revalidated to discover new hashed assets. */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> index() throws IOException {
        byte[] nonceBytes = new byte[24];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);
        String html = new ClassPathResource("static/index.html").getContentAsString(StandardCharsets.UTF_8)
                .replace("<script ", "<script nonce=\"" + nonce + "\" ");
        String policy = "default-src 'none'; script-src 'nonce-" + nonce + "' 'strict-dynamic'; "
                + "style-src 'self'; img-src 'self' blob:; font-src 'self'; connect-src 'self'; "
                + "base-uri 'none'; object-src 'none'; frame-ancestors 'none'; form-action 'self'";
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .contentType(MediaType.TEXT_HTML)
                .header("Content-Security-Policy", policy)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .body(html);
    }
}
