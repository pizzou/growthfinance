-- V10: unlimited-amount loan products + marital status documentation

-- Loan products: max_amount becomes optional (null = no upper limit / "unlimited")
ALTER TABLE loan_products ALTER COLUMN max_amount DROP NOT NULL;

-- Borrowers: single status certificate reference + spouse details for married applicants
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS single_certificate_number VARCHAR(100);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS spouse_full_name          VARCHAR(255);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS spouse_national_id        VARCHAR(100);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS spouse_phone              VARCHAR(50);
ALTER TABLE borrowers ADD COLUMN IF NOT EXISTS spouse_consent            BOOLEAN;
