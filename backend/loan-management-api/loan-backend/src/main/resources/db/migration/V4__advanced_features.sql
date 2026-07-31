-- ================================================================
-- V4: Advanced international fintech features
-- ================================================================

-- KYC / AML Compliance Checks
CREATE TABLE IF NOT EXISTS kyc_checks (
    id               BIGSERIAL PRIMARY KEY,
    borrower_id      BIGINT NOT NULL REFERENCES borrowers(id) ON DELETE CASCADE,
    organization_id  BIGINT NOT NULL REFERENCES organizations(id),
    check_type       VARCHAR(50)  NOT NULL,
    result           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    match_score      DOUBLE PRECISION,
    raw_response     TEXT,
    provider         VARCHAR(100),
    notes            TEXT,
    reviewed_by      VARCHAR(255),
    reviewed_at      TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kyc_borrower ON kyc_checks(borrower_id);
CREATE INDEX IF NOT EXISTS idx_kyc_org      ON kyc_checks(organization_id);
CREATE INDEX IF NOT EXISTS idx_kyc_result   ON kyc_checks(result);

-- Idempotency Keys (safe payment retries)
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id                   BIGSERIAL PRIMARY KEY,
    idempotency_key      VARCHAR(255) NOT NULL,
    organization_id      BIGINT NOT NULL REFERENCES organizations(id),
    endpoint             VARCHAR(500),
    request_hash         VARCHAR(64),
    status               VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    response_body        TEXT,
    response_status_code INT,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at           TIMESTAMP,
    UNIQUE (idempotency_key, organization_id)
);
CREATE INDEX IF NOT EXISTS idx_idempotency ON idempotency_keys(organization_id, idempotency_key);

-- Loan Restructuring History
CREATE TABLE IF NOT EXISTS loan_restructuring_history (
    id                  BIGSERIAL PRIMARY KEY,
    loan_id             BIGINT NOT NULL REFERENCES loans(id),
    organization_id     BIGINT NOT NULL REFERENCES organizations(id),
    restructuring_type  VARCHAR(50) NOT NULL,
    previous_status     VARCHAR(50),
    new_status          VARCHAR(50),
    previous_duration   INT,
    new_duration        INT,
    previous_rate       DOUBLE PRECISION,
    new_rate            DOUBLE PRECISION,
    amount_written_off  DOUBLE PRECISION,
    pause_months        INT,
    reason              TEXT,
    processed_by        BIGINT REFERENCES app_users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_restructuring_loan ON loan_restructuring_history(loan_id);

-- Bulk Disbursement Batches
CREATE TABLE IF NOT EXISTS bulk_disbursement_batches (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT NOT NULL REFERENCES organizations(id),
    initiated_by        BIGINT REFERENCES app_users(id),
    disbursement_method VARCHAR(50),
    total_loans         INT DEFAULT 0,
    success_count       INT DEFAULT 0,
    failure_count       INT DEFAULT 0,
    total_amount        DOUBLE PRECISION DEFAULT 0,
    currency            VARCHAR(10),
    status              VARCHAR(30) DEFAULT 'COMPLETED',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- SMS Notification Log
CREATE TABLE IF NOT EXISTS sms_logs (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT REFERENCES organizations(id),
    borrower_id     BIGINT REFERENCES borrowers(id),
    phone_number    VARCHAR(30),
    message         TEXT,
    provider        VARCHAR(50),
    status          VARCHAR(30),
    error_message   TEXT,
    sent_at         TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sms_org ON sms_logs(organization_id);

-- Data Privacy / GDPR Request Log
CREATE TABLE IF NOT EXISTS data_privacy_requests (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT REFERENCES organizations(id),
    borrower_id     BIGINT REFERENCES borrowers(id),
    request_type    VARCHAR(50),
    requested_by    BIGINT REFERENCES app_users(id),
    status          VARCHAR(30) DEFAULT 'COMPLETED',
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Public Website Loan Applications (from online form, pre-login)
CREATE TABLE IF NOT EXISTS public_applications (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT REFERENCES organizations(id),
    reference       VARCHAR(50) UNIQUE,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    national_id     VARCHAR(100),
    loan_type       VARCHAR(100),
    amount          DOUBLE PRECISION,
    duration_months INT,
    purpose         TEXT,
    employment_type VARCHAR(50),
    monthly_income  DOUBLE PRECISION,
    status          VARCHAR(30) DEFAULT 'PENDING_REVIEW',
    ip_address      VARCHAR(50),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMP,
    reviewed_by     BIGINT REFERENCES app_users(id)
);
CREATE INDEX IF NOT EXISTS idx_pubapps_org    ON public_applications(organization_id);
CREATE INDEX IF NOT EXISTS idx_pubapps_status ON public_applications(status);

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_payments_overdue
    ON payments(organization_id, due_date) WHERE paid = FALSE;
CREATE INDEX IF NOT EXISTS idx_loans_next_due
    ON loans(organization_id, next_due_date) WHERE status IN ('ACTIVE','OVERDUE');
CREATE INDEX IF NOT EXISTS idx_borrowers_kyc
    ON borrowers(organization_id, kyc_status);

-- Enhanced dashboard view
CREATE OR REPLACE VIEW v_org_dashboard AS
SELECT
    o.id                                                              AS org_id,
    o.name                                                            AS org_name,
    o.default_currency                                                AS currency,
    COUNT(DISTINCT b.id)                                              AS total_borrowers,
    COUNT(DISTINCT l.id)                                              AS total_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'ACTIVE')          AS active_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'PENDING')         AS pending_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'OVERDUE')         AS overdue_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'PAID')            AS paid_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'DEFAULTED')       AS defaulted_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'WRITTEN_OFF')     AS written_off_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'RESTRUCTURED')    AS restructured_loans,
    COALESCE(SUM(l.amount) FILTER (WHERE l.status IN ('ACTIVE','OVERDUE')), 0)   AS active_portfolio,
    COALESCE(SUM(l.total_paid), 0)                                   AS total_collected,
    COALESCE(SUM(l.outstanding_balance) FILTER (WHERE l.status='ACTIVE'), 0)    AS outstanding_balance,
    COUNT(DISTINCT p.id) FILTER (WHERE p.paid = FALSE AND p.due_date < CURRENT_DATE) AS overdue_installments,
    COUNT(DISTINCT k.id) FILTER (WHERE k.result = 'MANUAL_REVIEW')  AS pending_kyc_reviews,
    COUNT(DISTINCT pa.id) FILTER (WHERE pa.status = 'PENDING_REVIEW') AS pending_public_applications,
    COUNT(DISTINCT u.id)                                             AS total_users
FROM organizations o
LEFT JOIN borrowers b    ON b.organization_id = o.id
LEFT JOIN loans l        ON l.organization_id = o.id
LEFT JOIN payments p     ON p.organization_id = o.id
LEFT JOIN kyc_checks k   ON k.organization_id = o.id
LEFT JOIN public_applications pa ON pa.organization_id = o.id
LEFT JOIN app_users u    ON u.organization_id = o.id
GROUP BY o.id, o.name, o.default_currency;
