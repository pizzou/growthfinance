-- Lets a loan product (and the loans created from it) be priced with a
-- MONTHLY interest rate — common for microfinance/salary-advance style
-- products (e.g. 6%, 8%, 10% per month) — instead of the ANNUAL rate the
-- system assumed everywhere until now. Defaults to ANNUAL for every existing
-- row so current products/loans keep computing exactly as they did before;
-- this is purely opt-in per product going forward.

ALTER TABLE loan_products ADD COLUMN interest_rate_type VARCHAR(10) NOT NULL DEFAULT 'ANNUAL';
ALTER TABLE loans         ADD COLUMN interest_rate_type VARCHAR(10) NOT NULL DEFAULT 'ANNUAL';
