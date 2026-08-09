-- V24: the public application form had an "I agree to the Terms & Conditions" checkbox that
-- gated the Submit button client-side, but (a) the link behind "Terms & Conditions" went
-- nowhere (href="#"), and (b) the backend never actually checked the checkbox was ticked, nor
-- recorded that consent anywhere. A malformed or replayed request could create a loan with no
-- consent at all, and even a legitimate one left no server-side evidence of it.
ALTER TABLE loans ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP;

COMMENT ON COLUMN loans.terms_accepted_at IS
  'When the applicant accepted the Terms & Conditions on the public application form. '
  'NULL for loans created directly by staff (no separate applicant consent step there).';
