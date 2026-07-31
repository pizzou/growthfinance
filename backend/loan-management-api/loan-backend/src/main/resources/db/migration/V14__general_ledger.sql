-- V14: double-entry general ledger — chart of accounts, journal entries,
-- journal lines. Real transactions (disbursement, payment, write-off) post
-- balanced entries automatically — see AccountingService.

CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code            VARCHAR(20) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    normal_balance  VARCHAR(10) NOT NULL,
    active          BOOLEAN DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_coa_org ON chart_of_accounts(organization_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_coa_org_code ON chart_of_accounts(organization_id, code);

CREATE TABLE IF NOT EXISTS journal_entries (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entry_date      DATE NOT NULL,
    reference       VARCHAR(100),
    source_type     VARCHAR(50),
    source_id       VARCHAR(50),
    description     TEXT,
    created_by      VARCHAR(100),
    reversed        BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_journal_org ON journal_entries(organization_id);

CREATE TABLE IF NOT EXISTS journal_lines (
    id                BIGSERIAL PRIMARY KEY,
    journal_entry_id  BIGINT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id        BIGINT NOT NULL REFERENCES chart_of_accounts(id),
    debit             DOUBLE PRECISION DEFAULT 0,
    credit            DOUBLE PRECISION DEFAULT 0,
    description       TEXT
);
CREATE INDEX IF NOT EXISTS idx_journal_line_entry ON journal_lines(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_journal_line_account ON journal_lines(account_id);
