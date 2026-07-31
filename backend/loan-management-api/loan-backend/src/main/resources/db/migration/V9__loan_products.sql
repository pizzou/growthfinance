-- V9: real, priced loan products per organization — single source of truth
-- for both the public website's "Services" listing and the actual rate/
-- limits applied when a loan is created (previously a shared global table).
CREATE TABLE IF NOT EXISTS loan_products (
    id                     BIGSERIAL PRIMARY KEY,
    organization_id        BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name                   VARCHAR(150) NOT NULL,
    icon                   VARCHAR(10),
    description            TEXT,
    loan_type              VARCHAR(30) NOT NULL,
    interest_rate          DOUBLE PRECISION NOT NULL,
    min_amount             DOUBLE PRECISION NOT NULL,
    max_amount             DOUBLE PRECISION NOT NULL,
    min_term_months        INT NOT NULL,
    max_term_months        INT NOT NULL,
    processing_fee_percent DOUBLE PRECISION DEFAULT 2.0,
    active                 BOOLEAN DEFAULT TRUE,
    display_order          INT DEFAULT 0,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_lp_org ON loan_products(organization_id);
