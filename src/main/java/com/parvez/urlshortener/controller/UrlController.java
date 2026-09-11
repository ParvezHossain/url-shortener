package com.parvez.urlshortener.controller;

import com.parvez.urlshortener.dto.request.CreateShortUrlRequest;
import com.parvez.urlshortener.dto.response.ShortUrlResponse;
import com.parvez.urlshortener.dto.response.ShortUrlStatsResponse;
import com.parvez.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Maps URL creation, statistics, and deletion requests to the application service. */
@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShortenerService service;

    /** Supplies the URL application service. */
    public UrlController(UrlShortenerService service) {
        this.service = service;
    }

    /** Creates a short link and returns its public location. */
    @Operation(summary = "Create a short URL", description = "Creates a generated code or a case-sensitive custom alias, with optional future expiry.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Link created",
                headers = @Header(name = "Location", description = "Public short URL", schema = @Schema(type = "string", format = "uri")),
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ShortUrlResponse.class))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/ServerError")
    })
    @PostMapping
    public ResponseEntity<ShortUrlResponse> createShortUrl(@Valid @RequestBody CreateShortUrlRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }
    /** Returns link metadata and analytics without recording a visit. */
    @Operation(summary = "Get short URL statistics", description = "Reads metadata and analytics without recording a visit. Expired links remain queryable.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Link metadata and analytics",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ShortUrlStatsResponse.class))),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/ServerError")
    })
    @GetMapping("/{shortCode}")
    public ResponseEntity<ShortUrlStatsResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(service.getStats(shortCode));
    }
    /** Deletes a link and returns an empty response after the transaction commits. */
    @Operation(summary = "Delete a short URL", description = "Permanently deletes the link and analytics, including expired links. Repeated deletion returns 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Link deleted", content = @Content),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/ServerError")
    })
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteShortUrl(@PathVariable String shortCode) {
        service.delete(shortCode);
        return ResponseEntity.noContent().build();
    }


}
