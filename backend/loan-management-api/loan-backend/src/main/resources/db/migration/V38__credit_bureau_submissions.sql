
CREATE TABLE credit_bureau_submissions (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT NOT NULL REFERENCES organizations(id),
    reporting_period    VARCHAR(7) NOT NULL,                  -- 'YYYY-MM'
    provider            VARCHAR(50) NOT NULL DEFAULT 'INTERNAL_SIMULATED',
    record_count        INTEGER NOT NULL DEFAULT 0,
    payload_checksum    VARCHAR(64),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at          TIMESTAMP,
    submitted_by          VARCHAR(255),
    response_reference    VARCHAR(255),
    response_message      TEXT,
    responded_at           TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_cbs_org_period ON credit_bureau_submissions(organization_id, reporting_period);

CREATE TABLE credit_bureau_submission_records (
    id                    BIGSERIAL PRIMARY KEY,
    organization_id       BIGINT NOT NULL REFERENCES organizations(id),
    submission_id          BIGINT REFERENCES credit_bureau_submissions(id),
    borrower_id             BIGINT NOT NULL REFERENCES borrowers(id),
    loan_id                  BIGINT NOT NULL REFERENCES loans(id),
    reporting_period         VARCHAR(7) NOT NULL,
    -- Same field set as the existing CreditBureauRecord DTO, persisted:
    full_name                 VARCHAR(255),
    national_id                VARCHAR(50),
    date_of_birth               DATE,
    gender                       VARCHAR(20),
    phone                         VARCHAR(30),
    loan_number                   VARCHAR(100),
    loan_type                      VARCHAR(50),
    loan_status                     VARCHAR(30),
    loan_amount                      DOUBLE PRECISION,
    outstanding_balance               DOUBLE PRECISION,
    days_past_due                       INTEGER,
    credit_score                          INTEGER,
    date_opened                             DATE,
    last_payment_date                        DATE,
    maturity_date                              DATE,
    date_closed                                 DATE,
    branch_name                                  VARCHAR(255),
    currency                                       VARCHAR(3),
    -- New regulatory fields, sourced from Loan.creditQuality/arrearsStatus (see LoanClassificationService):
    classification                                  VARCHAR(20),
    repayment_status                                  VARCHAR(20),
    reporting_status                                    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    correction_of_record_id                              BIGINT REFERENCES credit_bureau_submission_records(id),
    created_at                                             TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_cbsr_org_period ON credit_bureau_submission_records(organization_id, reporting_period);
CREATE INDEX idx_cbsr_loan ON credit_bureau_submission_records(loan_id);
CREATE INDEX idx_cbsr_borrower ON credit_bureau_submission_records(borrower_id);

CREATE TABLE credit_bureau_disputes (
    id                       BIGSERIAL PRIMARY KEY,
    organization_id          BIGINT NOT NULL REFERENCES organizations(id),
    borrower_id                BIGINT NOT NULL REFERENCES borrowers(id),
    loan_id                      BIGINT REFERENCES loans(id),
    submission_record_id           BIGINT REFERENCES credit_bureau_submission_records(id),
    submitted_at                    TIMESTAMP NOT NULL DEFAULT now(),
    reason                           TEXT NOT NULL,
    disputed_field                   VARCHAR(100),
    old_value                          VARCHAR(255),
    requested_value                     VARCHAR(255),
    supporting_document_url               VARCHAR(500),
    status                                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reviewed_by                              VARCHAR(255),
    reviewed_at                                TIMESTAMP,
    resolution                                  TEXT,
    created_at                                    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_cbd_org ON credit_bureau_disputes(organization_id);
CREATE INDEX idx_cbd_borrower ON credit_bureau_disputes(borrower_id);
CREATE INDEX idx_cbd_status ON credit_bureau_disputes(status);