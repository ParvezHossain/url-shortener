package com.example.urlshortener.dto.response;

import java.time.Instant;

/** Exposes the public link and creation metadata without exposing the entity. */
public record ShortUrlResponse(
        String shortCode, String shortUrl, String originalUrl, Instant createdAt, Instant expiresAt) {
}
