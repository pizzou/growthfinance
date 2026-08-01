-- V41__enforce_monthly_repayment_frequency.sql

-- Normalize existing NULL values
UPDATE loans
SET repayment_frequency = 'MONTHLY'
WHERE repayment_frequency IS NULL;

-- Normalize any existing non-monthly values
UPDATE loans
SET repayment_frequency = 'MONTHLY'
WHERE repayment_frequency <> 'MONTHLY';

-- Default for new loans
ALTER TABLE loans
    ALTER COLUMN repayment_frequency SET DEFAULT 'MONTHLY';

-- Prevent NULL
ALTER TABLE loans
    ALTER COLUMN repayment_frequency SET NOT NULL;

-- Prevent anything other than MONTHLY
ALTER TABLE loans
    ADD CONSTRAINT chk_loans_repayment_frequency_monthly
    CHECK (repayment_frequency = 'MONTHLY');