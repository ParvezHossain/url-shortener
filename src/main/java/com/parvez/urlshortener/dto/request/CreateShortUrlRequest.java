package com.parvez.urlshortener.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** Carries the destination and optional settings for a new shortened URL. */
public record CreateShortUrlRequest(
        @Schema(description = "Absolute HTTP/HTTPS destination, up to 2048 characters by default (configurable)",
                example = "https://example.com/some/long/path")
        @NotBlank String originalUrl,
        @Schema(description = "Optional case-sensitive alias; omit or use null to generate a code", example = "my-link")
        @Pattern(regexp = "[a-zA-Z0-9_-]{3,16}",
                message = "must contain 3 to 16 letters, digits, underscores, or hyphens")
        String customAlias,
        @Schema(description = "Optional timestamp strictly in the future; omit or use null for no expiry", type = "string", format = "date-time")
        @Future(message = "must be in the future") Instant expiresAt) {
}
