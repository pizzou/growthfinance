-- V11: field-level encryption for PII (national ID, phone, address, spouse
-- details, bank account number) plus deterministic blind-index columns for
-- the fields that still need exact-match lookups (phone, national ID).
--
-- IMPORTANT: this is a breaking change for any existing plaintext data —
-- encrypted values are much longer than plaintext (base64 of IV+ciphertext),
-- so affected columns are widened to TEXT. Existing plaintext rows will
-- still READ correctly (CryptoConverter tolerates un-prefixed legacy values)
-- but will only be encrypted the next time they're saved. For a fresh
-- deployment (recommended here), just reseed after this migration.

-- v_overdue_payments (V3) selects borrowers.phone, so Postgres won't allow
-- widening that column's type while the view depends on it. Drop it first,
-- recreate it identically afterward.
DROP VIEW IF EXISTS v_overdue_payments;

ALTER TABLE borrowers ALTER COLUMN phone                     TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN alternate_phone            TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN national_id                TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN passport_number             TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN tax_identification_number   TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN single_certificate_number   TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN spouse_national_id          TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN spouse_phone                TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN address                     TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN address_line1               TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN address_line2               TYPE TEXT;
ALTER TABLE borrowers ALTER COLUMN bank_account_number          TYPE TEXT;

ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS phone_hash       VARCHAR(64);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS national_id_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_borrower_phone_hash       ON borrowers(phone_hash);
CREATE INDEX IF NOT EXISTS idx_borrower_national_id_hash ON borrowers(national_id_hash);

-- Recreate the view exactly as it was defined in V3__views_and_functions.sql.
CREATE OR REPLACE VIEW v_overdue_payments AS
SELECT
    p.id,
    p.loan_id,
    p.due_date,
    p.amount,
    l.reference_number,
    l.organization_id,
    b.first_name || ' ' || COALESCE(b.last_name, '') AS borrower_name,
    b.email  AS borrower_email,
    b.phone  AS borrower_phone,
    CURRENT_DATE - p.due_date                         AS days_overdue
FROM payments p
JOIN loans     l ON l.id = p.loan_id
JOIN borrowers b ON b.id = l.borrower_id
WHERE p.paid = FALSE
  AND p.due_date < CURRENT_DATE
ORDER BY p.due_date ASC;
