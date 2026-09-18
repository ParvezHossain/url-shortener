package com.parvez.urlshortener.cache;

import java.time.Instant;

/** Holds only the destination and expiry needed to resolve a cached redirect. */
public record RedirectCacheEntry(String destination, Instant expiresAt) {
}