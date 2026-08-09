-- Adds document-level KYC verification workflow to borrower_files: staff can now
-- verify/reject/request-replacement for each uploaded document individually,
-- with a comment the borrower sees on their tracking page.
ALTER TABLE borrower_files ADD COLUMN IF NOT EXISTS verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION';
ALTER TABLE borrower_files ADD COLUMN IF NOT EXISTS officer_comment TEXT;
ALTER TABLE borrower_files ADD COLUMN IF NOT EXISTS verified_by_name VARCHAR(255);
ALTER TABLE borrower_files ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_borrower_files_verification_status ON borrower_files (verification_status);

UPDATE borrower_files SET verification_status = 'PENDING_VERIFICATION' WHERE verification_status IS NULL;
