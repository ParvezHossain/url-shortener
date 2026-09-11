package com.parvez.urlshortener.dto.response;

import java.time.Instant;

/** Exposes link metadata and recorded analytics without exposing the entity. */
public record ShortUrlStatsResponse(
        String shortCode, String originalUrl, Instant createdAt, Instant expiresAt,
        long clickCount, Instant lastAccessedAt, String shortUrl, boolean customAlias) {
}
