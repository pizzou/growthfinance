BEGIN;

CREATE TABLE IF NOT EXISTS credit_bureau_disputes (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    borrower_id BIGINT NOT NULL,
    loan_id BIGINT,
    submission_record_id BIGINT,
    submitted_at TIMESTAMP NOT NULL,
    reason TEXT NOT NULL,
    disputed_field VARCHAR(255),
    old_value VARCHAR(255),
    requested_value VARCHAR(255),
    supporting_document_url VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    resolution TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cbd_org
    ON credit_bureau_disputes (organization_id);

CREATE INDEX IF NOT EXISTS idx_cbd_borrower
    ON credit_bureau_disputes (borrower_id);

CREATE INDEX IF NOT EXISTS idx_cbd_status
    ON credit_bureau_disputes (status);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbd_organization'
    ) THEN
        ALTER TABLE credit_bureau_disputes
        ADD CONSTRAINT fk_cbd_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbd_borrower'
    ) THEN
        ALTER TABLE credit_bureau_disputes
        ADD CONSTRAINT fk_cbd_borrower
        FOREIGN KEY (borrower_id)
        REFERENCES borrowers(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbd_loan'
    ) THEN
        ALTER TABLE credit_bureau_disputes
        ADD CONSTRAINT fk_cbd_loan
        FOREIGN KEY (loan_id)
        REFERENCES loans(id);
    END IF;
END $$;

COMMIT;