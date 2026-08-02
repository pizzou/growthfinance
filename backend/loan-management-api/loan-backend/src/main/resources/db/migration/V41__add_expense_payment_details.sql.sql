ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH';

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(100);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS payment_phone_number VARCHAR(30);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS payment_transaction_reference VARCHAR(150);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS payment_code VARCHAR(100);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS card_brand VARCHAR(30);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS card_last_four VARCHAR(4);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS card_authorization_code VARCHAR(100);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS cheque_number VARCHAR(100);

ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS payment_notes TEXT;

CREATE INDEX IF NOT EXISTS idx_expenses_payment_method
    ON expenses(payment_method);

CREATE INDEX IF NOT EXISTS idx_expenses_payment_reference
    ON expenses(payment_transaction_reference);