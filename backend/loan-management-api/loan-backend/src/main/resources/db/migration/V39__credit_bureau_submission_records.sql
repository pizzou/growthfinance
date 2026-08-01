BEGIN;

CREATE TABLE IF NOT EXISTS credit_bureau_submission_records (
    id BIGSERIAL PRIMARY KEY,

    organization_id BIGINT NOT NULL,

    submission_id BIGINT,

    borrower_id BIGINT NOT NULL,

    loan_id BIGINT NOT NULL,

    reporting_period VARCHAR(7) NOT NULL,

    full_name VARCHAR(255),

    national_id VARCHAR(255),

    date_of_birth DATE,

    gender VARCHAR(255),

    phone VARCHAR(255),

    loan_number VARCHAR(255),

    loan_type VARCHAR(255),

    loan_status VARCHAR(255),

    loan_amount NUMERIC(38,2),

    outstanding_balance NUMERIC(38,2),

    days_past_due INTEGER,

    credit_score INTEGER,

    date_opened DATE,

    last_payment_date DATE,

    maturity_date DATE,

    date_closed DATE,

    branch_name VARCHAR(255),

    currency VARCHAR(255),

    classification VARCHAR(255),

    repayment_status VARCHAR(255),

    reporting_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    correction_of_record_id BIGINT,

    created_at TIMESTAMP NOT NULL
);


-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_cbsr_org_period
    ON credit_bureau_submission_records (
        organization_id,
        reporting_period
    );

CREATE INDEX IF NOT EXISTS idx_cbsr_loan
    ON credit_bureau_submission_records (
        loan_id
    );

CREATE INDEX IF NOT EXISTS idx_cbsr_borrower
    ON credit_bureau_submission_records (
        borrower_id
    );


-- ============================================================
-- ORGANIZATION FOREIGN KEY
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbsr_organization'
    ) THEN
        ALTER TABLE credit_bureau_submission_records
        ADD CONSTRAINT fk_cbsr_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id);
    END IF;
END $$;


-- ============================================================
-- SUBMISSION FOREIGN KEY
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbsr_submission'
    ) THEN
        ALTER TABLE credit_bureau_submission_records
        ADD CONSTRAINT fk_cbsr_submission
        FOREIGN KEY (submission_id)
        REFERENCES credit_bureau_submissions(id);
    END IF;
END $$;


-- ============================================================
-- BORROWER FOREIGN KEY
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbsr_borrower'
    ) THEN
        ALTER TABLE credit_bureau_submission_records
        ADD CONSTRAINT fk_cbsr_borrower
        FOREIGN KEY (borrower_id)
        REFERENCES borrowers(id);
    END IF;
END $$;


-- ============================================================
-- LOAN FOREIGN KEY
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbsr_loan'
    ) THEN
        ALTER TABLE credit_bureau_submission_records
        ADD CONSTRAINT fk_cbsr_loan
        FOREIGN KEY (loan_id)
        REFERENCES loans(id);
    END IF;
END $$;


-- ============================================================
-- SELF-REFERENCE FOR CORRECTION CHAIN
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbsr_correction_record'
    ) THEN
        ALTER TABLE credit_bureau_submission_records
        ADD CONSTRAINT fk_cbsr_correction_record
        FOREIGN KEY (correction_of_record_id)
        REFERENCES credit_bureau_submission_records(id);
    END IF;
END $$;


COMMIT;