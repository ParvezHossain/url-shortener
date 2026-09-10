package com.example.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persistent record of a shortened URL: its code, target, optional expiry,
 * and click analytics. See docs/ARCHITECTURE.md §4 for the schema.
 */
@Entity
@Table(name = "short_url")
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 16)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    protected ShortUrl() {
        // required by JPA
    }

    /** Creates a shortened URL with its initial timestamps and zero clicks. */
    public ShortUrl(String shortCode, String originalUrl, boolean customAlias, Instant expiresAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.customAlias = customAlias;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.clickCount = 0L;
    }

    /** True if {@code expiresAt} is set and is before now. */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /** Increments click count and updates last-accessed timestamp. */
    public void recordAccess() {
        this.clickCount++;
        this.lastAccessedAt = Instant.now();
    }

    /** Returns the database identity, assigned when the URL is persisted. */
    public Long getId() {
        return id;
    }

    /** Returns the public code used to resolve this URL. */
    public String getShortCode() {
        return shortCode;
    }

    /** Returns the original redirect destination. */
    public String getOriginalUrl() {
        return originalUrl;
    }

    /** Reports whether the public code was chosen by the caller. */
    public boolean isCustomAlias() {
        return customAlias;
    }

    /** Returns when this URL was created. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Returns the expiry timestamp, or null for a URL that never expires. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Returns the number of recorded accesses. */
    public long getClickCount() {
        return clickCount;
    }

    /** Returns the latest access timestamp, or null before the first access. */
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }
}
