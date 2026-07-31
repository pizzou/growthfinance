-- Adds the module/page column to audit_logs so every audit entry records
-- which part of the system the action happened in (Loans, Payments,
-- Borrowers, KYC & Documents, User Management, Authentication, etc.),
-- not just the raw entity type.
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS module VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_module ON audit_logs (module);

-- Backfill existing rows from entity_type so historical entries aren't blank.
UPDATE audit_logs SET module = CASE
    WHEN entity_type = 'LOAN'               THEN 'Loans'
    WHEN entity_type = 'LOAN_PRODUCT'       THEN 'Loan Products'
    WHEN entity_type = 'PAYMENT'            THEN 'Payments'
    WHEN entity_type = 'BORROWER'           THEN 'Borrowers & KYC'
    WHEN entity_type = 'BORROWER_FILE'      THEN 'Documents & KYC'
    WHEN entity_type = 'BRANCH'             THEN 'Branches'
    WHEN entity_type = 'ORGANIZATION'       THEN 'Organization Settings'
    WHEN entity_type = 'COLLECTION_CASE'    THEN 'Collections'
    WHEN entity_type = 'BULK_DISBURSEMENT'  THEN 'Bulk Disbursement'
    WHEN entity_type = 'USER'               THEN 'User Management'
    WHEN entity_type = 'ROLE'               THEN 'Roles & Permissions'
    WHEN entity_type = 'WEBHOOK'            THEN 'Webhooks & Integrations'
    WHEN entity_type = 'AUTH'               THEN 'Authentication'
    ELSE 'General'
END
WHERE module IS NULL;
