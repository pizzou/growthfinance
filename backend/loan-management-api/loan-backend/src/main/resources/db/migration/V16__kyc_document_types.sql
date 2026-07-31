-- KYC document categorization: lets both the applicant upload flow and the
-- staff dashboard tag what a file actually is (national ID, bank statement,
-- etc.) instead of just being an untyped attachment.

ALTER TABLE borrower_files ADD COLUMN document_type VARCHAR(50);
ALTER TABLE borrower_files ADD COLUMN uploaded_by_applicant BOOLEAN DEFAULT FALSE;

UPDATE borrower_files SET document_type = 'OTHER' WHERE document_type IS NULL;
