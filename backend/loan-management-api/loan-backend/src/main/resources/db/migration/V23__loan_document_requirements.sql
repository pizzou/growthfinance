-- V23: closes the gap where a loan could be created, approved, and disbursed
-- with zero KYC documents on file for the borrower. Before this, BorrowerFile
-- existed and the upload/verify workflow worked end to end, but nothing in
-- LoanService ever checked it — an officer could click Approve, then Disburse,
-- on a borrower with an empty Documents tab.
--
-- required_document_types is a comma-separated list of BorrowerFileService
-- document type codes (NATIONAL_ID, PROOF_OF_ADDRESS, PAYSLIP, ...). NULL/blank
-- falls back to LoanService.DEFAULT_REQUIRED_DOCS at runtime, so existing rows
-- don't need special-casing in application code.
ALTER TABLE loan_products ADD COLUMN IF NOT EXISTS required_document_types VARCHAR(500);

-- Sensible baseline for existing products: proof of identity + address for everyone.
UPDATE loan_products
SET required_document_types = 'NATIONAL_ID,PROOF_OF_ADDRESS'
WHERE required_document_types IS NULL;

-- Business loans additionally need proof the business is real.
UPDATE loan_products
SET required_document_types = 'NATIONAL_ID,PROOF_OF_ADDRESS,BUSINESS_REGISTRATION'
WHERE loan_type = 'BUSINESS';

-- Income-driven products need proof of income before principal goes out the door.
UPDATE loan_products
SET required_document_types = 'NATIONAL_ID,PROOF_OF_ADDRESS,PAYSLIP'
WHERE loan_type IN ('PERSONAL', 'SALARY_ADVANCE');

COMMENT ON COLUMN loan_products.required_document_types IS
  'Comma-separated BorrowerFileService document type codes required before a loan '
  'of this product can be approved (any status) / disbursed (VERIFIED status). '
  'Blank/null falls back to the NATIONAL_ID,PROOF_OF_ADDRESS baseline.';
