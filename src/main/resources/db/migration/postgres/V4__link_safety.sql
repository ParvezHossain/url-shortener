ALTER TABLE short_url ADD COLUMN safety_state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN safety_provider VARCHAR(64), ADD COLUMN scanned_at TIMESTAMPTZ;
ALTER TABLE short_url ADD CONSTRAINT ck_short_url_safety_state
    CHECK (safety_state IN ('PENDING', 'ACTIVE', 'REJECTED', 'SCAN_FAILED'));
-- Preserve pre-migration links without pretending they have been scanned.
UPDATE short_url SET safety_state = 'ACTIVE', safety_provider = 'legacy-unscanned';
CREATE TABLE safety_audit (
    id BIGSERIAL PRIMARY KEY,
    url_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    verdict VARCHAR(16) NOT NULL CHECK (verdict IN ('ACTIVE', 'REJECTED', 'SCAN_FAILED')),
    scanned_at TIMESTAMPTZ NOT NULL,
    cached BOOLEAN NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_safety_audit_hash_time ON safety_audit(url_hash, recorded_at DESC);
