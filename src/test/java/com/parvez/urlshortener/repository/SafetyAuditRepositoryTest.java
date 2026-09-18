package com.parvez.urlshortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parvez.urlshortener.domain.SafetyState;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies durable audit transactions against the migrated PostgreSQL schema. */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SafetyAuditRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class SafetyAuditRepositoryTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    private final SafetyAuditRepository audit;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;

    @Autowired
    SafetyAuditRepositoryTest(SafetyAuditRepository audit, JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.audit = audit;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Test
    void record_outerTransactionRollsBack_retainsVerdictAndOriginalTimestamp() {
        String hash = "a".repeat(64);
        var scannedAt = Instant.parse("2026-09-18T00:00:00Z");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.record(hash, "provider-v1", SafetyState.REJECTED, scannedAt, true);
            status.setRollbackOnly();
        });
        var rows = jdbc.queryForList("select provider, verdict, scanned_at, cached, recorded_at from safety_audit where url_hash = ?", hash);
        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.get("provider")).isEqualTo("provider-v1");
        assertThat(row.get("verdict")).isEqualTo("REJECTED");
        assertThat(((java.sql.Timestamp) row.get("scanned_at")).toInstant()).isEqualTo(scannedAt);
        assertThat(row.get("cached")).isEqualTo(true);
        assertThat(row.get("recorded_at")).isNotNull();
    }

    @Test
    void record_pendingVerdict_rejectedByDatabaseConstraint() {
        assertThatThrownBy(() -> audit.record("b".repeat(64), "test", SafetyState.PENDING, Instant.now(), false))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
