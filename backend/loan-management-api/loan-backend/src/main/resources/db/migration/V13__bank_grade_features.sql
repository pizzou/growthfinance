-- V13: bank-grade features — guarantors, collateral, branches, and a real
-- multi-step maker-checker approval chain (replacing the old single-record
-- loan_approvals table with a proper step-based one).

-- Branches
CREATE TABLE IF NOT EXISTS branches (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name            VARCHAR(150) NOT NULL,
    code            VARCHAR(30),
    address         TEXT,
    city            VARCHAR(100),
    phone           VARCHAR(50),
    manager_id      BIGINT REFERENCES app_users(id),
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_branch_org ON branches(organization_id);

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branches(id);
ALTER TABLE loans      ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branches(id);

-- Guarantors
CREATE TABLE IF NOT EXISTS guarantors (
    id                BIGSERIAL PRIMARY KEY,
    loan_id           BIGINT NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    full_name         VARCHAR(255),
    national_id       TEXT,
    phone             TEXT,
    address           TEXT,
    relationship      VARCHAR(100),
    employer_name     VARCHAR(255),
    monthly_income    DOUBLE PRECISION,
    guaranteed_amount DOUBLE PRECISION,
    consent_given     BOOLEAN DEFAULT FALSE,
    document_url      TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_guarantor_loan ON guarantors(loan_id);

-- Collateral
CREATE TABLE IF NOT EXISTS collaterals (
    id                       BIGSERIAL PRIMARY KEY,
    loan_id                  BIGINT NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    organization_id          BIGINT NOT NULL REFERENCES organizations(id),
    type                     VARCHAR(30) NOT NULL,
    description              TEXT,
    owner_name               VARCHAR(255),
    estimated_value          DOUBLE PRECISION,
    currency                 VARCHAR(10),
    insured                  BOOLEAN DEFAULT FALSE,
    insurance_expiry_date    DATE,
    insurance_policy_number  VARCHAR(100),
    document_url             TEXT,
    registration_number      VARCHAR(100),
    status                   VARCHAR(20) DEFAULT 'PLEDGED',
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_collateral_loan ON collaterals(loan_id);

-- Maker-checker approval chain — loan_approvals already exists as a simple
-- single-row table; extend it into a proper multi-step chain.
ALTER TABLE loan_approvals ADD COLUMN IF NOT EXISTS organization_id BIGINT REFERENCES organizations(id);
ALTER TABLE loan_approvals ADD COLUMN IF NOT EXISTS step_order      INT;
ALTER TABLE loan_approvals ADD COLUMN IF NOT EXISTS required_role   VARCHAR(30);
ALTER TABLE loan_approvals ADD COLUMN IF NOT EXISTS step_name       VARCHAR(150);
ALTER TABLE loan_approvals ADD COLUMN IF NOT EXISTS decided_at      TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_loan_approval_loan ON loan_approvals(loan_id);

-- Account lockout fields on app_users already exist as unused columns from
-- an earlier migration in most deployments, but ensure they're present.
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS locked_until          TIMESTAMP;
