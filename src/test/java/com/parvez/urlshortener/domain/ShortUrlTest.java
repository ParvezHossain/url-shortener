package com.parvez.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Verifies initial entity state, expiration, and access recording without a database. */
@Tag("unit")
class ShortUrlTest {

    @Test
    void constructor_validUrl_initializesFieldsAndDefaults() {
        var before = Instant.now();
        var expiresAt = Instant.parse("2030-01-01T00:00:00Z");

        var url = new ShortUrl("custom", "https://example.com", true, expiresAt);

        assertThat(url.getId()).isNull();
        assertThat(url.getShortCode()).isEqualTo("custom");
        assertThat(url.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(url.isCustomAlias()).isTrue();
        assertThat(url.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(url.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(url.getClickCount()).isZero();
        assertThat(url.getLastAccessedAt()).isNull();
    }

    @Test
    void isExpired_noExpiry_returnsFalse() {
        var url = new ShortUrl("abc", "https://example.com", false, null);

        assertThat(url.isExpired()).isFalse();
    }

    @Test
    void isExpired_pastExpiry_returnsTrue() {
        var url = new ShortUrl("abc", "https://example.com", false, Instant.MIN);

        assertThat(url.isExpired()).isTrue();
    }

    @Test
    void isExpired_futureExpiry_returnsFalse() {
        var url = new ShortUrl("abc", "https://example.com", false, Instant.MAX);

        assertThat(url.isExpired()).isFalse();
    }

    @Test
    void recordAccess_repeatedAccess_incrementsCountAndUpdatesTimestamp() {
        var url = new ShortUrl("abc", "https://example.com", false, null);
        var beforeFirstAccess = Instant.now();

        url.recordAccess();

        assertThat(url.getClickCount()).isEqualTo(1);
        assertThat(url.getLastAccessedAt()).isBetween(beforeFirstAccess, Instant.now());
        var beforeSecondAccess = Instant.now();

        url.recordAccess();

        assertThat(url.getClickCount()).isEqualTo(2);
        assertThat(url.getLastAccessedAt()).isBetween(beforeSecondAccess, Instant.now());
    }
}
