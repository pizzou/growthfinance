

ALTER TABLE payments
    ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric, 2),
    ALTER COLUMN principal_component TYPE NUMERIC(19,2) USING ROUND(principal_component::numeric, 2),
    ALTER COLUMN interest_component TYPE NUMERIC(19,2) USING ROUND(interest_component::numeric, 2),
    ALTER COLUMN amount_paid TYPE NUMERIC(19,2) USING ROUND(amount_paid::numeric, 2),
    ALTER COLUMN penalty TYPE NUMERIC(19,2) USING ROUND(penalty::numeric, 2),
    ALTER COLUMN waived_amount TYPE NUMERIC(19,2) USING ROUND(waived_amount::numeric, 2),
    ALTER COLUMN outstanding_after TYPE NUMERIC(19,2) USING ROUND(outstanding_after::numeric, 2);

ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_amount_non_negative;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_principal_non_negative;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_interest_non_negative;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_amount_paid_non_negative;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_penalty_non_negative;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_waived_non_negative;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payment_outstanding_non_negative;

ALTER TABLE payments
    ADD CONSTRAINT chk_payment_amount_non_negative CHECK (amount IS NULL OR amount >= 0),
    ADD CONSTRAINT chk_payment_principal_non_negative CHECK (principal_component IS NULL OR principal_component >= 0),
    ADD CONSTRAINT chk_payment_interest_non_negative CHECK (interest_component IS NULL OR interest_component >= 0),
    ADD CONSTRAINT chk_payment_amount_paid_non_negative CHECK (amount_paid IS NULL OR amount_paid >= 0),
    ADD CONSTRAINT chk_payment_penalty_non_negative CHECK (penalty IS NULL OR penalty >= 0),
    ADD CONSTRAINT chk_payment_waived_non_negative CHECK (waived_amount IS NULL OR waived_amount >= 0),
    ADD CONSTRAINT chk_payment_outstanding_non_negative CHECK (outstanding_after IS NULL OR outstanding_after >= 0);