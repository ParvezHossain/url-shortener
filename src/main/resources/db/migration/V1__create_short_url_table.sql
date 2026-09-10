CREATE TABLE short_url (
    id                BIGSERIAL PRIMARY KEY,
    short_code        VARCHAR(16) NOT NULL,
    original_url      TEXT NOT NULL,
    custom_alias      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NULL,
    click_count        BIGINT NOT NULL DEFAULT 0,
    last_accessed_at  TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX uq_short_url_short_code ON short_url (short_code);
