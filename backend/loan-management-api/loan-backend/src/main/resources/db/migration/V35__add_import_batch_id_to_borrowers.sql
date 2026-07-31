-- Support for bulk-importing a client's pre-existing manual (e.g. Excel) loan ledger.
CREATE TABLE IF NOT EXISTS import_batches (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    imported_by       BIGINT REFERENCES app_users(id),
    file_name         VARCHAR(255),
    total_rows        INTEGER NOT NULL DEFAULT 0,
    success_count     INTEGER NOT NULL DEFAULT 0,
    failure_count     INTEGER NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    row_results       TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_import_batches_org ON import_batches(organization_id);

-- Add to loans
ALTER TABLE loans     ADD COLUMN IF NOT EXISTS imported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE loans     ADD COLUMN IF NOT EXISTS import_batch_id BIGINT REFERENCES import_batches(id);

-- Add to borrowers (Fixed line added here!)
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS imported BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS import_batch_id BIGINT REFERENCES import_batches(id);

CREATE INDEX IF NOT EXISTS idx_loans_import_batch ON loans(import_batch_id);
CREATE INDEX IF NOT EXISTS idx_borrowers_import_batch ON borrowers(import_batch_id);
