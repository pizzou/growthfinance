CREATE TABLE expenses (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT NOT NULL REFERENCES organizations(id),
    branch_id           BIGINT REFERENCES branches(id),
    payment_account_id  BIGINT NOT NULL REFERENCES bank_accounts(id),
    expense_date        DATE NOT NULL,
    category            VARCHAR(50) NOT NULL,
    amount              DOUBLE PRECISION NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'RWF',
    description         TEXT,
    receipt_file_name   VARCHAR(255),
    receipt_file_type   VARCHAR(100),
    receipt_file_size   BIGINT,
    receipt_data        BYTEA,
    status              VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    journal_entry_id    BIGINT REFERENCES journal_entries(id),
    created_by_name     VARCHAR(255),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    void_reason         TEXT,
    voided_at           TIMESTAMP
);

CREATE INDEX idx_expenses_org      ON expenses(organization_id);
CREATE INDEX idx_expenses_date     ON expenses(expense_date);
CREATE INDEX idx_expenses_category ON expenses(category);
CREATE INDEX idx_expenses_branch   ON expenses(branch_id);