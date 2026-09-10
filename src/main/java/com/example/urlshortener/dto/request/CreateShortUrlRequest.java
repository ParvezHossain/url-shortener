package com.example.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** Carries the destination and optional settings for a new shortened URL. */
public record CreateShortUrlRequest(
        @NotBlank String originalUrl,
        @Pattern(regexp = "[a-zA-Z0-9_-]{3,16}",
                message = "must contain 3 to 16 letters, digits, underscores, or hyphens")
        String customAlias,
        Instant expiresAt) {
}
