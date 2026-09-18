package com.parvez.urlshortener.controller;

import com.parvez.urlshortener.dto.request.CreateShortUrlRequest;
import com.parvez.urlshortener.dto.response.ShortUrlResponse;
import com.parvez.urlshortener.dto.response.ShortUrlStatsResponse;
import com.parvez.urlshortener.dto.response.UrlPageResponse;
import com.parvez.urlshortener.security.OwnerPrincipal;
import com.parvez.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes URL management scoped to the authenticated owner. */
@RestController
@RequestMapping("/api/v2/urls")
@SecurityRequirement(name = "ApiKey")
@ApiResponses({
    @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited"),
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
})
public class V2UrlController {
    private final UrlShortenerService service;
    /** Supplies owner-aware operations. */
    public V2UrlController(UrlShortenerService service) { this.service = service; }
    /** Creates an owned link. */
    @Operation(summary = "Create an owned short URL")
    @ApiResponse(responseCode = "201", description = "Owned link created")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    @ApiResponse(responseCode = "422", ref = "#/components/responses/UnsafeDestination")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/CreationUnavailable")
    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@RequestAttribute("owner") OwnerPrincipal owner,
            @Valid @RequestBody CreateShortUrlRequest request) {

        var result = service.create(request, owner);
        return ResponseEntity.created(URI.create(result.shortUrl())).body(result);
    }
    /** Reads owned statistics without recording a click. */
    @Operation(summary = "Get owned link statistics")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/QuotaUnavailable")
    @GetMapping("/{code}")
    public ShortUrlStatsResponse stats(@RequestAttribute("owner") OwnerPrincipal owner, @PathVariable String code) {
        return service.getStats(code, owner);
    }
    /** Lists owned links with bounded pagination. */
    @Operation(summary = "List owned links", description = "Zero-based pages, size 1–100; creation time and ID descending.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/QuotaUnavailable")
    @GetMapping
    public UrlPageResponse list(@RequestAttribute("owner") OwnerPrincipal owner,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(owner, page, size);
    }
    /** Deletes an owned link. */
    @Operation(summary = "Delete an owned link")
    @ApiResponse(responseCode = "204", description = "Owned link deleted")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/QuotaUnavailable")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@RequestAttribute("owner") OwnerPrincipal owner, @PathVariable String code) {
        service.delete(code, owner);
        return ResponseEntity.noContent().build();
    }
}
