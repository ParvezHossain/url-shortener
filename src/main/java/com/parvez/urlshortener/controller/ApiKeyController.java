package com.parvez.urlshortener.controller;

import com.parvez.urlshortener.dto.response.ApiKeyResponse;
import com.parvez.urlshortener.security.OwnerPrincipal;
import com.parvez.urlshortener.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Allows a caller to rotate or revoke its current key. */
@RestController
@RequestMapping("/api/v2/keys")
@SecurityRequirement(name = "ApiKey")
@ApiResponses({
    @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited"),
    @ApiResponse(responseCode = "503", ref = "#/components/responses/QuotaUnavailable"),
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
})
public class ApiKeyController {
    private final ApiKeyService keys;
    /** Supplies key lifecycle operations. */
    public ApiKeyController(ApiKeyService keys) { this.keys = keys; }
    /** Returns a replacement once and immediately revokes the old key. */
    @Operation(summary = "Rotate the current key", description = "Revokes the current key immediately and returns its replacement once. Operator recovery is required if the response is lost.")
    @PostMapping("/{prefix}/rotate")
    public ApiKeyResponse rotate(@RequestAttribute("owner") OwnerPrincipal owner, @PathVariable String prefix) {
        return keys.rotate(owner, prefix);
    }
    /** Revokes the current key. */
    @Operation(summary = "Revoke the current key", description = "Immediately revokes the calling credential. Other keys cannot be managed through this endpoint.")
    @ApiResponse(responseCode = "204", description = "Current key revoked")
    @DeleteMapping("/{prefix}")
    public ResponseEntity<Void> revoke(@RequestAttribute("owner") OwnerPrincipal owner, @PathVariable String prefix) {
        keys.revoke(owner, prefix);
        return ResponseEntity.noContent().build();
    }
}
