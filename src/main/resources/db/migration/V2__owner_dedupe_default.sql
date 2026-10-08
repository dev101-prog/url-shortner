ALTER TABLE owners ADD COLUMN dedupe_default BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN owners.dedupe_default IS 'URL-FR-1.4 owner-level dedupe default; request flag overrides (A4).';
