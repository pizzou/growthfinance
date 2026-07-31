-- ==========================================================
-- V28__borrower_blacklisting.sql
-- Borrower blacklisting workflow
-- ==========================================================

ALTER TABLE borrowers
ADD COLUMN IF NOT EXISTS blacklist_reason VARCHAR(500);

ALTER TABLE borrowers
ADD COLUMN IF NOT EXISTS blacklisted_at TIMESTAMP;

ALTER TABLE borrowers
ADD COLUMN IF NOT EXISTS blacklisted_by BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_borrowers_blacklisted_by'
    ) THEN

        ALTER TABLE borrowers
        ADD CONSTRAINT fk_borrowers_blacklisted_by
        FOREIGN KEY (blacklisted_by)
        REFERENCES app_users(id);

    END IF;
END $$;