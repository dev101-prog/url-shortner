-- V1__init_schema.sql
-- URL Shortener schema. Owner: engineering lead. Human-approved before execution (URL-AI-1).
-- All timestamps are TIMESTAMPTZ and compared in UTC (PRD A10, R12).

CREATE TABLE owners (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_owners_name UNIQUE (name)
);

CREATE TABLE api_keys (
    id          BIGSERIAL    PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES owners (id),
    key_hash    CHAR(64)     NOT NULL,              -- SHA-256 hex of the raw key; raw key never stored
    key_prefix  VARCHAR(12)  NOT NULL,              -- first chars of raw key, for human identification only
    label       VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uq_api_keys_hash  UNIQUE (key_hash),
    CONSTRAINT chk_api_keys_hash CHECK (key_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_api_keys_owner ON api_keys (owner_id);

CREATE TABLE links (
    id               BIGSERIAL     PRIMARY KEY,
    code             VARCHAR(32)   NOT NULL,        -- 7-char base62 or 4-32 char alias, case-sensitive
    owner_id         BIGINT        NOT NULL REFERENCES owners (id),
    target_url       VARCHAR(2048) NOT NULL,
    normalized_url   VARCHAR(2048) NOT NULL,        -- used for opt-in dedupe (URL-FR-1.4)
    is_custom_alias  BOOLEAN       NOT NULL DEFAULT FALSE,
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ,                   -- NULL = never expires; 'expired' is derived at read time
    deactivated_at   TIMESTAMPTZ,
    click_count      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_links_code          UNIQUE (code),
    CONSTRAINT chk_links_code         CHECK (code ~ '^[A-Za-z0-9_-]{4,32}$'),
    CONSTRAINT chk_links_status       CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_links_expiry       CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT chk_links_deactivated  CHECK ((status = 'INACTIVE') = (deactivated_at IS NOT NULL)),
    CONSTRAINT chk_links_click_count  CHECK (click_count >= 0)
);
CREATE INDEX idx_links_owner_normalized_active
    ON links (owner_id, normalized_url)
    WHERE status = 'ACTIVE';

CREATE TABLE click_events (
    id             BIGSERIAL    PRIMARY KEY,
    link_id        BIGINT       NOT NULL REFERENCES links (id),
    clicked_at     TIMESTAMPTZ  NOT NULL,
    referrer_host  VARCHAR(255),
    user_agent     VARCHAR(512),
    is_bot         BOOLEAN      NOT NULL DEFAULT FALSE,
    ip_hash        CHAR(64),                        -- HMAC-SHA256(salt, ip); raw IP never stored
    country        CHAR(2),                         -- ISO-3166 alpha-2 or NULL
    CONSTRAINT chk_click_country CHECK (country IS NULL OR country ~ '^[A-Z]{2}$')
);
CREATE INDEX idx_click_events_link_time ON click_events (link_id, clicked_at);

COMMENT ON TABLE  click_events         IS 'Append-only click log. Loss bounded to in-memory buffer on crash (URL-FR-7.6).';
COMMENT ON COLUMN click_events.ip_hash IS 'HMAC-SHA256 with secret salt. Never store raw IP (URL-FR-7.3).';
