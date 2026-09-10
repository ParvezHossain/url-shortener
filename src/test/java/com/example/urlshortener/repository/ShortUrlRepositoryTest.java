package com.example.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.urlshortener.domain.ShortUrl;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies entity mappings and database constraints against the Flyway schema. */
@Tag("integration")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class ShortUrlRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private final ShortUrlRepository repository;
    private final EntityManager entityManager;

    @Autowired
    ShortUrlRepositoryTest(ShortUrlRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Test
    void save_validShortUrl_persistsAndGeneratesId() {
        var expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        var url = new ShortUrl("custom", "https://example.com/path?query=value", true, expiresAt);

        var saved = repository.saveAndFlush(url);
        entityManager.clear();
        var reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getId()).isPositive();
        assertThat(reloaded.getShortCode()).isEqualTo("custom");
        assertThat(reloaded.getOriginalUrl()).isEqualTo("https://example.com/path?query=value");
        assertThat(reloaded.isCustomAlias()).isTrue();
        assertThat(reloaded.getCreatedAt()).isCloseTo(url.getCreatedAt(), within(1, ChronoUnit.MICROS));
        assertThat(reloaded.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(reloaded.getClickCount()).isZero();
        assertThat(reloaded.getLastAccessedAt()).isNull();
    }

    @Test
    void findByShortCode_existingCode_returnsEntity() {
        var url = repository.saveAndFlush(new ShortUrl("abc123", "https://example.com", false, null));
        entityManager.clear();

        var found = repository.findByShortCode("abc123").orElseThrow();

        assertThat(found.getId()).isEqualTo(url.getId());
        assertThat(found.getShortCode()).isEqualTo("abc123");
        assertThat(found.isCustomAlias()).isFalse();
        assertThat(found.getExpiresAt()).isNull();
    }

    @Test
    void findByShortCode_unknownCode_returnsEmptyOptional() {
        assertThat(repository.findByShortCode("unknown")).isEmpty();
    }

    @Test
    void save_duplicateShortCode_throwsDataIntegrityViolationException() {
        repository.saveAndFlush(new ShortUrl("duplicate", "https://example.com/first", false, null));
        entityManager.clear();
        var duplicate = new ShortUrl("duplicate", "https://example.com/second", true, null);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_short_url_short_code");
    }

    @Test
    void updateShortCode_existingRow_persistsFinalCode() {
        var saved = repository.saveAndFlush(new ShortUrl("~temporary", "https://example.com", false, null));

        repository.updateShortCode(saved.getId(), "10");

        assertThat(repository.findByShortCode("~temporary")).isEmpty();
        assertThat(repository.findByShortCode("10").orElseThrow().getId()).isEqualTo(saved.getId());
    }

    @Test
    void save_recordedAccess_persistsClickCountAndLastAccessedAt() {
        var url = repository.saveAndFlush(new ShortUrl("visited", "https://example.com", false, null));

        url.recordAccess();
        url.recordAccess();
        repository.flush();
        entityManager.clear();
        var reloaded = repository.findById(url.getId()).orElseThrow();

        assertThat(reloaded.getClickCount()).isEqualTo(2);
        assertThat(reloaded.getLastAccessedAt())
                .isCloseTo(url.getLastAccessedAt(), within(1, ChronoUnit.MICROS));
    }
}
