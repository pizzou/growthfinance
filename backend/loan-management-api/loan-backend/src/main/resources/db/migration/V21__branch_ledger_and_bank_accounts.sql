-- Phase 9: branch dimension on journal entries, so branch-level reporting can filter
-- the same ledger without a parallel chart of accounts per branch.
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branches(id);
CREATE INDEX IF NOT EXISTS idx_journal_entries_branch ON journal_entries (branch_id);

-- Phase 5: Cashbook & Bank Management. Each bank/cash account the institution actually holds,
-- backed by its own dedicated chart-of-accounts sub-ledger (created in BankAccountService).
CREATE TABLE IF NOT EXISTS bank_accounts (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT NOT NULL REFERENCES organizations(id),
    branch_id           BIGINT REFERENCES branches(id),
    chart_of_account_id BIGINT NOT NULL REFERENCES chart_of_accounts(id),
    name                VARCHAR(255) NOT NULL,
    account_type        VARCHAR(16)  NOT NULL, -- CASH or BANK
    bank_name           VARCHAR(255),
    account_number      VARCHAR(100),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bank_accounts_org ON bank_accounts (organization_id);
