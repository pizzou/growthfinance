-- ================================================================
-- V5: Enterprise features — credit bureau, e-signature, collections
-- ================================================================

-- Credit Bureau Checks
CREATE TABLE IF NOT EXISTS credit_bureau_checks (
    id                          BIGSERIAL PRIMARY KEY,
    borrower_id                 BIGINT NOT NULL REFERENCES borrowers(id) ON DELETE CASCADE,
    organization_id             BIGINT NOT NULL REFERENCES organizations(id),
    reference                   VARCHAR(100) UNIQUE,
    provider                    VARCHAR(100),
    national_id_checked         VARCHAR(100),
    status                      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    credit_score                INT,
    risk_grade                  VARCHAR(30),
    active_facilities           INT,
    delinquent_accounts         INT,
    total_outstanding_debt      DOUBLE PRECISION,
    total_monthly_obligations   DOUBLE PRECISION,
    has_default_history         BOOLEAN,
    has_active_listing          BOOLEAN,
    listing_reason              TEXT,
    raw_response                TEXT,
    requested_by                VARCHAR(255),
    failure_reason              TEXT,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at                  TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cbc_borrower ON credit_bureau_checks(borrower_id);
CREATE INDEX IF NOT EXISTS idx_cbc_org      ON credit_bureau_checks(organization_id);

-- E-Signature Requests
CREATE TABLE IF NOT EXISTS esignature_requests (
    id                     BIGSERIAL PRIMARY KEY,
    loan_id                BIGINT NOT NULL REFERENCES loans(id),
    borrower_id            BIGINT NOT NULL REFERENCES borrowers(id),
    organization_id        BIGINT NOT NULL REFERENCES organizations(id),
    signing_token          VARCHAR(64) NOT NULL UNIQUE,
    document_type          VARCHAR(50),
    status                 VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    otp_code_hash          VARCHAR(255),
    otp_attempts           INT DEFAULT 0,
    otp_sent_at            TIMESTAMP,
    document_snapshot      TEXT,
    document_hash          VARCHAR(64),
    consent_text           TEXT,
    signer_full_name_typed VARCHAR(255),
    signer_ip_address      VARCHAR(64),
    signer_user_agent      VARCHAR(500),
    decline_reason         TEXT,
    created_by             VARCHAR(255),
    sent_at                TIMESTAMP,
    signed_at              TIMESTAMP,
    declined_at            TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at             TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_esign_loan  ON esignature_requests(loan_id);
CREATE INDEX IF NOT EXISTS idx_esign_token ON esignature_requests(signing_token);

-- Collection Cases (one active case per delinquent loan)
CREATE TABLE IF NOT EXISTS collection_cases (
    id                    BIGSERIAL PRIMARY KEY,
    loan_id               BIGINT NOT NULL UNIQUE REFERENCES loans(id),
    organization_id       BIGINT NOT NULL REFERENCES organizations(id),
    assigned_agent_id     BIGINT REFERENCES app_users(id),
    bucket                VARCHAR(30) NOT NULL,
    status                VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority              VARCHAR(20) DEFAULT 'MEDIUM',
    days_past_due         INT,
    overdue_amount        DOUBLE PRECISION,
    total_outstanding     DOUBLE PRECISION,
    last_contact_date     DATE,
    next_action_date      DATE,
    promise_to_pay_date   DATE,
    promise_to_pay_amount DOUBLE PRECISION,
    resolution_notes      TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    closed_at             TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cc_org    ON collection_cases(organization_id);
CREATE INDEX IF NOT EXISTS idx_cc_loan   ON collection_cases(loan_id);
CREATE INDEX IF NOT EXISTS idx_cc_bucket ON collection_cases(bucket);
CREATE INDEX IF NOT EXISTS idx_cc_status ON collection_cases(status);

-- Collection Actions (contact log / audit trail per case)
CREATE TABLE IF NOT EXISTS collection_actions (
    id                   BIGSERIAL PRIMARY KEY,
    collection_case_id   BIGINT NOT NULL REFERENCES collection_cases(id) ON DELETE CASCADE,
    action_type          VARCHAR(30) NOT NULL,
    performed_by         VARCHAR(255),
    notes                TEXT,
    outcome              VARCHAR(50),
    promise_date         DATE,
    promise_amount       DOUBLE PRECISION,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ca_case ON collection_actions(collection_case_id);
