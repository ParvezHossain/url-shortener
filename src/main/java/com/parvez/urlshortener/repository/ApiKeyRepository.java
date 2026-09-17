package com.parvez.urlshortener.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists hashed credentials and sanitized lifecycle audit events. */
@Repository
public class ApiKeyRepository {
    private final JdbcTemplate jdbc;
    /** Supplies transactional PostgreSQL access. */
    public ApiKeyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** Locks a credential to serialize authentication with revocation. */
    public Optional<KeyRecord> findForUpdate(String prefix) {
        return jdbc.query("select owner_id, key_hash, revoked_at from api_key where prefix = ? for update",
                (rs, n) -> new KeyRecord(UUID.fromString(rs.getString("owner_id")),
                        rs.getString("key_hash"), rs.getTimestamp("revoked_at") != null), prefix)
                .stream().findFirst();
    }
    /** Records successful authentication without storing request credentials. */
    public void recordUse(String prefix) {
        jdbc.update("update api_key set last_used_at = CURRENT_TIMESTAMP where prefix = ?", prefix);
    }
    /** Inserts only a hash and non-secret prefix for a new key. */
    public void insert(String prefix, UUID owner, String hash) {
        jdbc.update("insert into api_key(prefix, owner_id, key_hash) values (?, ?, ?)", prefix, owner, hash);
    }
    /** Revokes an active owned credential and reports whether it changed. */
    public boolean revoke(String prefix, UUID owner) {
        return jdbc.update("update api_key set revoked_at = CURRENT_TIMESTAMP "
                + "where prefix = ? and owner_id = ? and revoked_at is null", prefix, owner) == 1;
    }
    /** Appends a lifecycle event containing only owner and non-secret prefixes. */
    public void audit(UUID owner, String event, String actor, String target) {
        jdbc.update("insert into api_audit(owner_id, event_type, actor_prefix, target_prefix) values (?, ?, ?, ?)",
                owner, event, actor, target);
    }
    /** Holds internal authentication material that never reaches a controller. */
    public record KeyRecord(UUID owner, String hash, boolean revoked) {
        /** Keeps stored authentication material out of diagnostic output. */
        @Override public String toString() { return "KeyRecord[redacted]"; }
    }
}
