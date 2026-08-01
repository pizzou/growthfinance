-- V40__enforce_monthly_repayment_frequency.sql

-- Ensure existing rows are valid
UPDATE loans
SET repayment_frequency = 'MONTHLY'
WHERE repayment_frequency IS NULL
   OR repayment_frequency <> 'MONTHLY';

-- Set default for new loans
ALTER TABLE loans
    ALTER COLUMN repayment_frequency SET DEFAULT 'MONTHLY';

-- Make the column mandatory
ALTER TABLE loans
    ALTER COLUMN repayment_frequency SET NOT NULL;

-- Add the constraint only if it does not already exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_loans_repayment_frequency_monthly'
          AND conrelid = 'loans'::regclass
    ) THEN
        ALTER TABLE loans
            ADD CONSTRAINT chk_loans_repayment_frequency_monthly
            CHECK (repayment_frequency = 'MONTHLY');
    END IF;
END $$;