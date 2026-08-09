-- ============================================================
-- PAYMENT TRANSACTIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,

    loan_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    installment_id BIGINT,
    recorded_by BIGINT,

    transaction_reference VARCHAR(120) NOT NULL,

    amount NUMERIC(19,2) NOT NULL,
    penalty_component NUMERIC(19,2),
    interest_component NUMERIC(19,2),
    principal_component NUMERIC(19,2),
    unapplied_amount NUMERIC(19,2),

    payment_method VARCHAR(255),
    channel VARCHAR(255),
    notes VARCHAR(255),

    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',

    reversed BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP,
    reversed_at TIMESTAMP,

    reversal_reason VARCHAR(500),
    reversal_reference VARCHAR(120),

    CONSTRAINT uq_payment_txn_reference
        UNIQUE (organization_id, transaction_reference),

    CONSTRAINT fk_payment_tx_loan
        FOREIGN KEY (loan_id)
        REFERENCES loans(id),

    CONSTRAINT fk_payment_tx_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id),

    CONSTRAINT fk_payment_tx_installment
        FOREIGN KEY (installment_id)
        REFERENCES payments(id),

    CONSTRAINT fk_payment_tx_recorded_by
        FOREIGN KEY (recorded_by)
        REFERENCES app_users(id),

    CONSTRAINT chk_payment_tx_status
        CHECK (status IN ('POSTED', 'REVERSED'))
);

-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_payment_tx_loan
    ON payment_transactions (loan_id);

CREATE INDEX IF NOT EXISTS idx_payment_tx_installment
    ON payment_transactions (installment_id);

CREATE INDEX IF NOT EXISTS idx_payment_tx_status
    ON payment_transactions (status);