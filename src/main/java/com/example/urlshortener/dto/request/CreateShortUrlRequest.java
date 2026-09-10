package com.example.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/** Carries the destination and optional settings for a new shortened URL. */
public record CreateShortUrlRequest(
        @NotBlank String originalUrl, String customAlias, Instant expiresAt) {
}
