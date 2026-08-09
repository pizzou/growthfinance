-- ================================================================
-- LoanSaaS Pro — Master PostgreSQL Schema
-- V1: Full initial schema for all entities
-- ================================================================

-- Enable UUID extension (PostgreSQL)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ================================================================
-- ROLES
-- ================================================================
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- ================================================================
-- ORGANIZATIONS (tenants)
-- ================================================================
CREATE TABLE IF NOT EXISTS organizations (
    id                        BIGSERIAL PRIMARY KEY,
    name                      VARCHAR(255) NOT NULL,
    industry                  VARCHAR(100),
    country                   VARCHAR(10),
    default_currency          VARCHAR(10)  NOT NULL DEFAULT 'USD',
    timezone                  VARCHAR(100) NOT NULL DEFAULT 'UTC',
    locale                    VARCHAR(20)  NOT NULL DEFAULT 'en-US',
    logo_url                  VARCHAR(500),
    primary_color             VARCHAR(20),
    accent_color              VARCHAR(20),
    website                   VARCHAR(500),
    contact_email             VARCHAR(255),
    contact_phone             VARCHAR(50),
    address                   TEXT,
    registration_number       VARCHAR(100) UNIQUE,
    subscription_tier         VARCHAR(50)  NOT NULL DEFAULT 'TRIAL',
    status                    VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    max_users                 INT          DEFAULT 100,
    max_active_loans          INT          DEFAULT 10000,
    max_loan_amount           DOUBLE PRECISION DEFAULT 1000000.00,
    min_loan_amount           DOUBLE PRECISION DEFAULT 100.00,
    stripe_customer_id        VARCHAR(255),
    subscribed_at             TIMESTAMP,
    trial_ends_at             TIMESTAMP,
    subscription_expires_at   TIMESTAMP,
    created_at                TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ================================================================
-- USERS
-- ================================================================
CREATE TABLE IF NOT EXISTS app_users (
    id                   BIGSERIAL PRIMARY KEY,
    organization_id      BIGINT REFERENCES organizations(id) ON DELETE SET NULL,
    role_id              BIGINT REFERENCES roles(id),
    name                 VARCHAR(255) NOT NULL,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password             VARCHAR(255) NOT NULL,
    phone                VARCHAR(50),
    avatar_url           VARCHAR(500),
    job_title            VARCHAR(100),
    status               VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    two_factor_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    two_factor_secret    VARCHAR(255),
    failed_login_attempts INT         NOT NULL DEFAULT 0,
    locked_until         TIMESTAMP,
    last_login_at        TIMESTAMP,
    last_login_ip        VARCHAR(50),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_org      ON app_users(organization_id);
CREATE INDEX idx_users_email    ON app_users(email);
CREATE INDEX idx_users_role     ON app_users(role_id);

-- ================================================================
-- BORROWERS
-- ================================================================
CREATE TABLE IF NOT EXISTS borrowers (
    id                          BIGSERIAL PRIMARY KEY,
    organization_id             BIGINT NOT NULL REFERENCES organizations(id),
    first_name                  VARCHAR(100) NOT NULL,
    last_name                   VARCHAR(100),
    email                       VARCHAR(255),
    phone                       VARCHAR(50),
    alternate_phone             VARCHAR(50),
    national_id                 VARCHAR(100),
    passport_number             VARCHAR(100),
    tax_identification_number   VARCHAR(100),
    date_of_birth               DATE,
    gender                      VARCHAR(20),
    marital_status              VARCHAR(30),
    nationality                 VARCHAR(10),
    address                     VARCHAR(500),
    address_line1               VARCHAR(255),
    address_line2               VARCHAR(255),
    city                        VARCHAR(100),
    state_province              VARCHAR(100),
    postal_code                 VARCHAR(20),
    country                     VARCHAR(10),
    employer_name               VARCHAR(255),
    employment_type             VARCHAR(50),
    job_title                   VARCHAR(100),
    monthly_income              DOUBLE PRECISION,
    monthly_expenses            DOUBLE PRECISION,
    net_worth                   DOUBLE PRECISION,
    credit_score                INT,
    credit_bureau               VARCHAR(100),
    credit_report_date          DATE,
    kyc_status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    status                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    bank_name                   VARCHAR(255),
    bank_account_number         VARCHAR(100),
    bank_branch                 VARCHAR(100),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_borrowers_org   ON borrowers(organization_id);
CREATE INDEX idx_borrowers_email ON borrowers(email);
CREATE INDEX idx_borrowers_natid ON borrowers(national_id);

-- ================================================================
-- LOANS
-- ================================================================
CREATE TABLE IF NOT EXISTS loans (
    id                      BIGSERIAL PRIMARY KEY,
    organization_id         BIGINT NOT NULL REFERENCES organizations(id),
    reference_number        VARCHAR(100) NOT NULL UNIQUE,
    borrower_id             BIGINT NOT NULL REFERENCES borrowers(id),
    approved_by             BIGINT REFERENCES app_users(id),
    loan_officer_id         BIGINT REFERENCES app_users(id),
    loan_type               VARCHAR(50)  NOT NULL,
    status                  VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    repayment_frequency     VARCHAR(30),
    amount                  DOUBLE PRECISION NOT NULL,
    interest_rate           DOUBLE PRECISION  NOT NULL,
    duration_months         INT           NOT NULL,
    currency                VARCHAR(10)   NOT NULL DEFAULT 'USD',
    processing_fee_rate     DOUBLE PRECISION  DEFAULT 2.0,
    processing_fee          DOUBLE PRECISION,
    disbursed_amount        DOUBLE PRECISION,
    total_repayable         DOUBLE PRECISION,
    total_paid              DOUBLE PRECISION NOT NULL DEFAULT 0,
    outstanding_balance     DOUBLE PRECISION,
    penalty_amount          DOUBLE PRECISION DEFAULT 0,
    notes                   TEXT,
    purpose                 TEXT,
    collateral_description  TEXT,
    collateral_value        DOUBLE PRECISION,
    rejection_reason        TEXT,
    internal_notes          TEXT,
    risk_score              DOUBLE PRECISION,
    risk_category           VARCHAR(20),
    debt_to_income_ratio    DOUBLE PRECISION,
    credit_score_snapshot   INT,
    guarantors              TEXT,
    disbursement_method     VARCHAR(50),
    disbursement_account    VARCHAR(255),
    start_date              DATE,
    approved_at             DATE,
    disbursed_at            DATE,
    maturity_date           DATE,
    next_due_date           DATE,
    last_payment_date       DATE,
    missed_installments     INT DEFAULT 0,
    days_overdue            INT DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_loans_org        ON loans(organization_id);
CREATE INDEX idx_loans_borrower   ON loans(borrower_id);
CREATE INDEX idx_loans_status     ON loans(status);
CREATE INDEX idx_loans_ref        ON loans(reference_number);
CREATE INDEX idx_loans_type       ON loans(loan_type);

-- ================================================================
-- PAYMENTS (repayment schedule + recordings)
-- ================================================================
CREATE TABLE IF NOT EXISTS payments (
    id                   BIGSERIAL PRIMARY KEY,
    payment_reference    VARCHAR(150) UNIQUE,
    loan_id              BIGINT NOT NULL REFERENCES loans(id),
    organization_id      BIGINT NOT NULL REFERENCES organizations(id),
    recorded_by          BIGINT REFERENCES app_users(id),
    installment_number   INT,
    amount               DOUBLE PRECISION,
    principal_component  DOUBLE PRECISION,
    interest_component   DOUBLE PRECISION,
    amount_paid          DOUBLE PRECISION,
    penalty              DOUBLE PRECISION DEFAULT 0,
    waived_amount        DOUBLE PRECISION DEFAULT 0,
    outstanding_after    DOUBLE PRECISION,
    paid                 BOOLEAN NOT NULL DEFAULT FALSE,
    due_date             DATE,
    paid_date            DATE,
    payment_method       VARCHAR(50),
    transaction_id       VARCHAR(255),
    external_reference   VARCHAR(255),
    gateway_response     TEXT,
    channel              VARCHAR(100),
    notes                TEXT,
    is_late              BOOLEAN NOT NULL DEFAULT FALSE,
    days_late            INT DEFAULT 0,
    status               VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    verified_at          TIMESTAMP
);

CREATE INDEX idx_payments_loan    ON payments(loan_id);
CREATE INDEX idx_payments_org     ON payments(organization_id);
CREATE INDEX idx_payments_due     ON payments(due_date);
CREATE INDEX idx_payments_status  ON payments(status);

-- ================================================================
-- AUDIT LOGS
-- ================================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT REFERENCES organizations(id),
    user_id         BIGINT REFERENCES app_users(id),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       VARCHAR(50),
    description     TEXT,
    before_value    TEXT,
    after_value     TEXT,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    timestamp       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_org    ON audit_logs(organization_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_ts     ON audit_logs(timestamp DESC);

-- ================================================================
-- WEBHOOK ENDPOINTS
-- ================================================================
CREATE TABLE IF NOT EXISTS webhook_endpoints (
    id                   BIGSERIAL PRIMARY KEY,
    organization_id      BIGINT NOT NULL REFERENCES organizations(id),
    url                  VARCHAR(1000) NOT NULL,
    description          VARCHAR(255),
    secret               VARCHAR(255),
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    failure_count        INT DEFAULT 0,
    last_delivery_at     TIMESTAMP,
    last_delivery_status VARCHAR(255),
    created_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS webhook_events (
    webhook_id  BIGINT NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_type  VARCHAR(100) NOT NULL,
    PRIMARY KEY (webhook_id, event_type)
);

-- ================================================================
-- CURRENCY RATES
-- ================================================================
CREATE TABLE IF NOT EXISTS currency_rates (
    id              BIGSERIAL PRIMARY KEY,
    base_currency   VARCHAR(10) NOT NULL,
    target_currency VARCHAR(10) NOT NULL,
    rate            DOUBLE PRECISION NOT NULL,
    fetched_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(base_currency, target_currency)
);

-- ================================================================
-- LOAN APPROVALS (approval workflow)
-- ================================================================
CREATE TABLE IF NOT EXISTS loan_approvals (
    id          BIGSERIAL PRIMARY KEY,
    loan_id     BIGINT NOT NULL REFERENCES loans(id),
    approver_id BIGINT REFERENCES app_users(id),
    status      VARCHAR(30) NOT NULL,
    comments    TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ================================================================
-- BORROWER FILES
-- ================================================================
CREATE TABLE IF NOT EXISTS borrower_files (
    id              BIGSERIAL PRIMARY KEY,
    borrower_id     BIGINT NOT NULL REFERENCES borrowers(id),
    file_name       VARCHAR(255),
    file_type       VARCHAR(100),
    file_size       BIGINT,
    file_path       VARCHAR(1000),
    data            BYTEA,
    uploaded_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ================================================================
-- PASSWORD RESET TOKENS
-- ================================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_users(id),
    token       VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ================================================================
-- NOTIFICATIONS
-- ================================================================
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES app_users(id),
    organization_id BIGINT REFERENCES organizations(id),
    title           VARCHAR(255),
    message         TEXT,
    type            VARCHAR(50),
    link            VARCHAR(500),
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMP
);

CREATE INDEX idx_notif_user ON notifications(user_id, read);

-- ================================================================
-- SEED: default roles
-- ================================================================
INSERT INTO roles (name, description) VALUES
    ('ADMIN',          'Full platform access — manage users, loans, settings'),
    ('LOAN_OFFICER',   'Create, review, approve and disburse loans'),
    ('CREDIT_ANALYST', 'Risk assessment and credit review'),
    ('TELLER',         'Record payments and view loan schedules'),
    ('MANAGER',        'View all data, generate reports, manage staff')
ON CONFLICT (name) DO NOTHING;
