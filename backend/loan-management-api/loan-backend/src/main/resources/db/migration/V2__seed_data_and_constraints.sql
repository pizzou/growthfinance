-- ================================================================
-- V2: Seed data, constraints, and schema fixes
-- ================================================================

-- Ensure roles.name has UNIQUE constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'roles_name_key' AND conrelid = 'roles'::regclass
    ) THEN
        ALTER TABLE roles ADD CONSTRAINT roles_name_key UNIQUE (name);
    END IF;
END $$;

-- Seed all roles (safe, idempotent)
INSERT INTO roles (name, description) VALUES
    ('ADMIN',          'Full platform access')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (name, description) VALUES
    ('MANAGER',        'View all data, manage staff and reports')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (name, description) VALUES
    ('LOAN_OFFICER',   'Create, review, approve and disburse loans')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (name, description) VALUES
    ('CREDIT_ANALYST', 'Risk assessment and credit review')
ON CONFLICT (name) DO NOTHING;

INSERT INTO roles (name, description) VALUES
    ('TELLER',         'Record payments and view loan schedules')
ON CONFLICT (name) DO NOTHING;

-- Seed FX currency rates (idempotent)
INSERT INTO currency_rates (base_currency, target_currency, rate) VALUES
    ('USD','EUR',0.92),   ('USD','GBP',0.79),    ('USD','KES',130.50),
    ('USD','UGX',3730.0), ('USD','TZS',2480.0),  ('USD','RWF',1290.0),
    ('USD','ETB',57.50),  ('USD','NGN',1580.0),  ('USD','GHS',12.30),
    ('USD','ZAR',18.70),  ('USD','INR',83.20),   ('USD','AED',3.67),
    ('USD','SAR',3.75),   ('USD','EGP',48.50),   ('USD','BRL',5.10),
    ('USD','PHP',56.00),  ('USD','MWK',1730.0),  ('USD','ZMW',27.50),
    ('USD','XOF',600.0),  ('USD','XAF',600.0),
    ('EUR','USD',1.09),   ('GBP','USD',1.27),    ('KES','USD',0.0077)
ON CONFLICT (base_currency, target_currency) DO UPDATE SET rate = EXCLUDED.rate;

-- Unique constraints on borrowers (prevent duplicates per org)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_borrower_national_id_org') THEN
        ALTER TABLE borrowers ADD CONSTRAINT uq_borrower_national_id_org
            UNIQUE (national_id, organization_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_borrower_email_org') THEN
        ALTER TABLE borrowers ADD CONSTRAINT uq_borrower_email_org
            UNIQUE (email, organization_id);
    END IF;
END $$;

-- Fix borrower_files.data column (bytea, not oid)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'borrower_files' AND column_name = 'data' AND data_type = 'oid'
    ) THEN
        ALTER TABLE borrower_files DROP COLUMN data;
        ALTER TABLE borrower_files ADD COLUMN data BYTEA;
    END IF;
END $$;

-- Fix loan_approvals columns to match LoanApproval entity
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'loan_approvals' AND column_name = 'comments') THEN
        ALTER TABLE loan_approvals ADD COLUMN IF NOT EXISTS comments TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'loan_approvals' AND column_name = 'approver_id') THEN
        ALTER TABLE loan_approvals ADD COLUMN IF NOT EXISTS
            approver_id BIGINT REFERENCES app_users(id);
    END IF;
END $$;

-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_loans_org_status    ON loans(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_loans_org_type      ON loans(organization_id, loan_type);
CREATE INDEX IF NOT EXISTS idx_payments_loan_paid  ON payments(loan_id, paid);
CREATE INDEX IF NOT EXISTS idx_payments_due_unpaid ON payments(due_date) WHERE paid = FALSE;
