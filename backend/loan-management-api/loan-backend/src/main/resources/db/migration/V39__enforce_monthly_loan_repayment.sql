
UPDATE loans
SET repayment_frequency = 'MONTHLY'
WHERE repayment_frequency IS NULL
   OR repayment_frequency <> 'MONTHLY';

ALTER TABLE loans
    ALTER COLUMN repayment_frequency SET DEFAULT 'MONTHLY';

ALTER TABLE loans
    ALTER COLUMN repayment_frequency SET NOT NULL;

ALTER TABLE loans
    ADD CONSTRAINT chk_loans_repayment_frequency_monthly
    CHECK (repayment_frequency = 'MONTHLY');