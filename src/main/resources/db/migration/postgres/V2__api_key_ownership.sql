CREATE TABLE api_owner (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE api_key (
    prefix VARCHAR(24) PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES api_owner(id),
    key_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX ix_api_key_owner ON api_key(owner_id);
CREATE TABLE api_audit (
    id BIGSERIAL PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES api_owner(id),
    event_type VARCHAR(32) NOT NULL,
    actor_prefix VARCHAR(24),
    target_prefix VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Existing links deliberately remain unowned; never infer an owner from a code.
ALTER TABLE short_url ADD COLUMN owner_id UUID REFERENCES api_owner(id);
CREATE INDEX ix_short_url_owner_created ON short_url(owner_id, created_at DESC, id DESC);
