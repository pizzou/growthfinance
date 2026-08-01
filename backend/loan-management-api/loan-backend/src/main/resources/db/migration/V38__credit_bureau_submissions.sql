BEGIN;

CREATE TABLE IF NOT EXISTS credit_bureau_submissions (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    reporting_period VARCHAR(7) NOT NULL,
    provider VARCHAR(255) DEFAULT 'INTERNAL_SIMULATED',
    record_count INTEGER DEFAULT 0,
    payload_checksum VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP,
    submitted_by VARCHAR(255),
    response_reference VARCHAR(255),
    response_message TEXT,
    responded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cbs_org_period
    ON credit_bureau_submissions (
        organization_id,
        reporting_period
    );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cbs_organization'
    ) THEN
        ALTER TABLE credit_bureau_submissions
        ADD CONSTRAINT fk_cbs_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id);
    END IF;
END $$;

COMMIT;