package com.parvez.urlshortener.cache;

import java.time.Duration;
import java.util.Optional;

/** Provides best-effort cache-aside storage for redirect destinations. */
public interface RedirectCache {

    /** Reads a cached destination, or returns empty when it is unavailable. */
    Optional<RedirectCacheEntry> get(String shortCode);

    /** Stores a destination for no longer than the supplied lifetime. */
    void put(String shortCode, RedirectCacheEntry entry, Duration ttl);

    /** Removes a cached destination after a link changes or is deleted. */
    void evict(String shortCode);

    /** Returns a cache implementation that deliberately performs no I/O. */
    static RedirectCache noop() {
        return new RedirectCache() {
            @Override
            public Optional<RedirectCacheEntry> get(String shortCode) {
                return Optional.empty();
            }

            @Override
            public void put(String shortCode, RedirectCacheEntry entry, Duration ttl) {
            }

            @Override
            public void evict(String shortCode) {
            }
        };
    }
}