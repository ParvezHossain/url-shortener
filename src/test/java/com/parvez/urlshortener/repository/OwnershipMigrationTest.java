package com.parvez.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies upgrading populated pre-v2 storage preserves links without guessing owners. */
@Tag("integration")
@Testcontainers
class OwnershipMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void migrate_existingLinks_preservesCodesAnalyticsAndUnownedStatus() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate("insert into short_url(short_code, original_url, custom_alias, created_at, expires_at, click_count) "
                    + "values ('legacy', 'https://example.com', true, '2026-01-01 00:00:00', '2030-01-01 00:00:00', 7)");
        }
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery("select short_code, original_url, owner_id, click_count, expires_at, safety_state, safety_provider, scanned_at from short_url")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("short_code")).isEqualTo("legacy");
            assertThat(result.getString("original_url")).isEqualTo("https://example.com");
            assertThat(result.getObject("owner_id")).isNull();
            assertThat(result.getLong("click_count")).isEqualTo(7);
            assertThat(result.getTimestamp("expires_at").toInstant()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
            assertThat(result.getString("safety_state")).isEqualTo("ACTIVE");
            assertThat(result.getString("safety_provider")).isEqualTo("legacy-unscanned");
            assertThat(result.getTimestamp("scanned_at")).isNull();
            assertThat(result.next()).isFalse();
        }
    }
}
