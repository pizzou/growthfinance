
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES loans(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    installment_id BIGINT REFERENCES payments(id),
    recorded_by BIGINT REFERENCES users(id),
    transaction_reference VARCHAR(120) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    penalty_component NUMERIC(19,2) NOT NULL DEFAULT 0,
    interest_component NUMERIC(19,2) NOT NULL DEFAULT 0,
    principal_component NUMERIC(19,2) NOT NULL DEFAULT 0,
    unapplied_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(80),
    channel VARCHAR(80),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMP,
    reversal_reason VARCHAR(500),
    reversal_reference VARCHAR(120),
    CONSTRAINT uq_payment_txn_reference UNIQUE (organization_id, transaction_reference),
    CONSTRAINT ck_payment_txn_amount_nonnegative CHECK (amount >= 0),
    CONSTRAINT ck_payment_txn_components_nonnegative CHECK (
        penalty_component >= 0 AND interest_component >= 0
        AND principal_component >= 0 AND unapplied_amount >= 0
    ),
    CONSTRAINT ck_payment_txn_components_match CHECK (
        amount = penalty_component + interest_component + principal_component + unapplied_amount
    )
);

CREATE INDEX IF NOT EXISTS idx_payment_tx_loan ON payment_transactions(loan_id);
CREATE INDEX IF NOT EXISTS idx_payment_tx_installment ON payment_transactions(installment_id);
CREATE INDEX IF NOT EXISTS idx_payment_tx_status ON payment_transactions(status);

