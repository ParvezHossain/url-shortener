package com.parvez.urlshortener.repository;

import com.parvez.urlshortener.domain.SafetyState;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records append-only scan decisions independently of the link-creation transaction. */
@Repository
public class SafetyAuditRepository {
    private final JdbcTemplate jdbc;
    /** Supplies transactional audit persistence. */
    public SafetyAuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** Retains provider and original verdict time, including cached and failed decisions. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String hash, String provider, SafetyState state, Instant scannedAt, boolean cached) {
        jdbc.update("insert into safety_audit(url_hash, provider, verdict, scanned_at, cached) values (?,?,?,?,?)",
                hash, provider, state.name(), Timestamp.from(scannedAt), cached);
    }
}
