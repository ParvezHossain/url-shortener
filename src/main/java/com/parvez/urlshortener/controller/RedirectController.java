package com.parvez.urlshortener.controller;

import com.parvez.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Redirects short codes to their resolved destinations. */
@RestController
public class RedirectController {

    private final UrlShortenerService service;

    /** Supplies the URL application service. */
    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    /** Returns a temporary redirect after the service records a successful access. */
    @Operation(summary = "Resolve and redirect a short URL", description = "Checks expiry and records one access before redirecting. Unknown or expired links do not record visits.")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Temporary redirect", content = @Content,
                headers = {
                    @Header(name = "Location", description = "Original destination URL", schema = @Schema(type = "string", format = "uri")),
                    @Header(name = "Cache-Control", description = "Prevents cached redirects", schema = @Schema(type = "string", example = "no-store"))
                }),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "410", ref = "#/components/responses/Gone"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/ServerError")
    })
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String destination = service.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destination))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
