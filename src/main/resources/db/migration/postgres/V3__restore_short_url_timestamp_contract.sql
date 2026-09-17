-- The PostgreSQL V1 variant uses timestamp without time zone. Hibernate's Instant
-- mapping writes UTC; preserve those instants when restoring the original contract.
ALTER TABLE short_url
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_accessed_at TYPE TIMESTAMPTZ USING last_accessed_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN custom_alias SET DEFAULT FALSE;
ALTER TABLE short_url RENAME CONSTRAINT uk_short_url_short_code TO uq_short_url_short_code;
