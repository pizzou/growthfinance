-- ================================================================
-- V32: Regulatory reporting module
--
-- Adds regulatory_api_clients — credentials for external regulatory
-- and credit-bureau systems (e.g. the National Bank of Rwanda) to
-- call the read-only reporting APIs under /api/regulatory/external/**.
-- These are machine-to-machine integrations, not staff logins, so
-- they live in their own table rather than app_users: no password,
-- no MFA, no seat. The raw API key is never stored — only a bcrypt
-- hash (key_hash) plus a short, non-secret lookup prefix (key_prefix)
-- used to find the candidate row before the bcrypt comparison.
--
-- The BNR portfolio-summary and credit-bureau-export reports
-- themselves are computed on the fly from existing loans/borrowers/
-- payments data — no new tables needed for those.
-- ================================================================
CREATE TABLE IF NOT EXISTS regulatory_api_clients (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT NOT NULL REFERENCES organizations(id),
    name             VARCHAR(255) NOT NULL,
    client_type      VARCHAR(30) NOT NULL,      -- BNR or CREDIT_BUREAU
    key_prefix       VARCHAR(20) NOT NULL UNIQUE,
    key_hash         VARCHAR(255) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    contact_email    VARCHAR(255),
    description      VARCHAR(255),
    expires_at       TIMESTAMP,
    last_used_at     TIMESTAMP,
    last_used_ip     VARCHAR(64),
    created_by       BIGINT REFERENCES app_users(id),
    revoked_at       TIMESTAMP,
    revoked_reason   VARCHAR(500),
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reg_api_client_org    ON regulatory_api_clients(organization_id);
CREATE INDEX IF NOT EXISTS idx_reg_api_client_prefix ON regulatory_api_clients(key_prefix);