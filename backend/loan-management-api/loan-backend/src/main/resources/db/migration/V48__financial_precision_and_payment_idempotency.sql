-- V48: Financial precision hardening and payment idempotency
-- Safe conversion from IEEE-754 floating point to exact PostgreSQL NUMERIC.
-- Dependent views are dropped before ALTER COLUMN TYPE and recreated afterward.

DROP VIEW IF EXISTS v_portfolio_summary;
DROP VIEW IF EXISTS v_org_dashboard;
DROP VIEW IF EXISTS v_overdue_payments;

ALTER TABLE organizations
    ALTER COLUMN max_loan_amount TYPE NUMERIC(19,6) USING max_loan_amount::NUMERIC,
    ALTER COLUMN min_loan_amount TYPE NUMERIC(19,6) USING min_loan_amount::NUMERIC;

ALTER TABLE borrowers
    ALTER COLUMN monthly_income TYPE NUMERIC(19,6) USING monthly_income::NUMERIC,
    ALTER COLUMN monthly_expenses TYPE NUMERIC(19,6) USING monthly_expenses::NUMERIC,
    ALTER COLUMN net_worth TYPE NUMERIC(19,6) USING net_worth::NUMERIC;

ALTER TABLE loans
    ALTER COLUMN amount TYPE NUMERIC(19,6) USING amount::NUMERIC,
    ALTER COLUMN interest_rate TYPE NUMERIC(19,9) USING interest_rate::NUMERIC,
    ALTER COLUMN processing_fee_rate TYPE NUMERIC(19,9) USING processing_fee_rate::NUMERIC,
    ALTER COLUMN processing_fee TYPE NUMERIC(19,6) USING processing_fee::NUMERIC,
    ALTER COLUMN disbursed_amount TYPE NUMERIC(19,6) USING disbursed_amount::NUMERIC,
    ALTER COLUMN total_repayable TYPE NUMERIC(19,6) USING total_repayable::NUMERIC,
    ALTER COLUMN total_paid TYPE NUMERIC(19,6) USING total_paid::NUMERIC,
    ALTER COLUMN outstanding_balance TYPE NUMERIC(19,6) USING outstanding_balance::NUMERIC,
    ALTER COLUMN penalty_amount TYPE NUMERIC(19,6) USING penalty_amount::NUMERIC,
    ALTER COLUMN collateral_value TYPE NUMERIC(19,6) USING collateral_value::NUMERIC,
    ALTER COLUMN risk_score TYPE NUMERIC(19,9) USING risk_score::NUMERIC,
    ALTER COLUMN debt_to_income_ratio TYPE NUMERIC(19,9) USING debt_to_income_ratio::NUMERIC,
    ALTER COLUMN next_installment_amount TYPE NUMERIC(19,6) USING next_installment_amount::NUMERIC;


ALTER TABLE loans
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payments
    ALTER COLUMN amount TYPE NUMERIC(19,6) USING amount::NUMERIC,
    ALTER COLUMN principal_component TYPE NUMERIC(19,6) USING principal_component::NUMERIC,
    ALTER COLUMN interest_component TYPE NUMERIC(19,6) USING interest_component::NUMERIC,
    ALTER COLUMN amount_paid TYPE NUMERIC(19,6) USING amount_paid::NUMERIC,
    ALTER COLUMN penalty TYPE NUMERIC(19,6) USING penalty::NUMERIC,
    ALTER COLUMN waived_amount TYPE NUMERIC(19,6) USING waived_amount::NUMERIC,
    ALTER COLUMN outstanding_after TYPE NUMERIC(19,6) USING outstanding_after::NUMERIC,
    ALTER COLUMN cycle_interest_due TYPE NUMERIC(19,6) USING cycle_interest_due::NUMERIC,
    ALTER COLUMN cycle_interest_remaining TYPE NUMERIC(19,6) USING cycle_interest_remaining::NUMERIC;

ALTER TABLE payment_schedules
    ALTER COLUMN installment_amount TYPE NUMERIC(19,6) USING installment_amount::NUMERIC,
    ALTER COLUMN principal_amount TYPE NUMERIC(19,6) USING principal_amount::NUMERIC,
    ALTER COLUMN interest_amount TYPE NUMERIC(19,6) USING interest_amount::NUMERIC,
    ALTER COLUMN penalty_amount TYPE NUMERIC(19,6) USING penalty_amount::NUMERIC,
    ALTER COLUMN amount_paid TYPE NUMERIC(19,6) USING amount_paid::NUMERIC,
    ALTER COLUMN remaining_balance TYPE NUMERIC(19,6) USING remaining_balance::NUMERIC;

ALTER TABLE loan_products
    ALTER COLUMN interest_rate TYPE NUMERIC(19,9) USING interest_rate::NUMERIC,
    ALTER COLUMN min_amount TYPE NUMERIC(19,6) USING min_amount::NUMERIC,
    ALTER COLUMN max_amount TYPE NUMERIC(19,6) USING max_amount::NUMERIC,
    ALTER COLUMN processing_fee_percent TYPE NUMERIC(19,9) USING processing_fee_percent::NUMERIC;

ALTER TABLE journal_lines
    ALTER COLUMN debit TYPE NUMERIC(19,6) USING debit::NUMERIC,
    ALTER COLUMN credit TYPE NUMERIC(19,6) USING credit::NUMERIC;

ALTER TABLE expenses
    ALTER COLUMN amount TYPE NUMERIC(19,6) USING amount::NUMERIC;

ALTER TABLE collection_cases
    ALTER COLUMN overdue_amount TYPE NUMERIC(19,6) USING overdue_amount::NUMERIC,
    ALTER COLUMN total_outstanding TYPE NUMERIC(19,6) USING total_outstanding::NUMERIC,
    ALTER COLUMN promise_to_pay_amount TYPE NUMERIC(19,6) USING promise_to_pay_amount::NUMERIC;

ALTER TABLE collection_actions
    ALTER COLUMN promise_amount TYPE NUMERIC(19,6) USING promise_amount::NUMERIC;

ALTER TABLE collaterals
    ALTER COLUMN estimated_value TYPE NUMERIC(19,6) USING estimated_value::NUMERIC;

ALTER TABLE guarantors
    ALTER COLUMN monthly_income TYPE NUMERIC(19,6) USING monthly_income::NUMERIC,
    ALTER COLUMN guaranteed_amount TYPE NUMERIC(19,6) USING guaranteed_amount::NUMERIC;

ALTER TABLE loan_restructuring_history
    ALTER COLUMN previous_rate TYPE NUMERIC(19,9) USING previous_rate::NUMERIC,
    ALTER COLUMN new_rate TYPE NUMERIC(19,9) USING new_rate::NUMERIC,
    ALTER COLUMN amount_written_off TYPE NUMERIC(19,6) USING amount_written_off::NUMERIC;

ALTER TABLE bulk_disbursement_batches
    ALTER COLUMN total_amount TYPE NUMERIC(19,6) USING total_amount::NUMERIC;

ALTER TABLE public_applications
    ALTER COLUMN amount TYPE NUMERIC(19,6) USING amount::NUMERIC,
    ALTER COLUMN monthly_income TYPE NUMERIC(19,6) USING monthly_income::NUMERIC;

ALTER TABLE credit_bureau_checks
    ALTER COLUMN total_outstanding_debt TYPE NUMERIC(19,6) USING total_outstanding_debt::NUMERIC,
    ALTER COLUMN total_monthly_obligations TYPE NUMERIC(19,6) USING total_monthly_obligations::NUMERIC;

ALTER TABLE currency_rates
    ALTER COLUMN rate TYPE NUMERIC(19,12) USING rate::NUMERIC;

ALTER TABLE kyc_checks
    ALTER COLUMN match_score TYPE NUMERIC(19,9) USING match_score::NUMERIC;

-- Recreate views whose dependent columns were altered above.
CREATE OR REPLACE VIEW v_portfolio_summary AS
SELECT
    o.id AS org_id,
    o.name AS org_name,
    o.default_currency AS currency,
    COUNT(l.id) AS total_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'ACTIVE') AS active_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'PENDING') AS pending_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'OVERDUE') AS overdue_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'PAID') AS completed_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'DEFAULTED') AS defaulted_loans,
    COALESCE(SUM(l.amount) FILTER (WHERE l.status IN ('ACTIVE','OVERDUE')), 0) AS active_principal,
    COALESCE(SUM(l.total_paid), 0) AS total_collected,
    COALESCE(SUM(l.outstanding_balance) FILTER (WHERE l.status='ACTIVE'), 0) AS outstanding_balance,
    COUNT(DISTINCT l.borrower_id) AS total_borrowers
FROM organizations o
LEFT JOIN loans l ON l.organization_id = o.id
GROUP BY o.id, o.name, o.default_currency;

CREATE OR REPLACE VIEW v_overdue_payments AS
SELECT
    p.id,
    p.loan_id,
    p.due_date,
    p.amount,
    l.reference_number,
    l.organization_id,
    b.first_name || ' ' || COALESCE(b.last_name, '') AS borrower_name,
    b.email AS borrower_email,
    b.phone AS borrower_phone,
    CURRENT_DATE - p.due_date AS days_overdue
FROM payments p
JOIN loans l ON l.id = p.loan_id
JOIN borrowers b ON b.id = l.borrower_id
WHERE p.paid = FALSE
  AND p.due_date < CURRENT_DATE
ORDER BY p.due_date ASC;

CREATE OR REPLACE VIEW v_org_dashboard AS
SELECT
    o.id AS org_id,
    o.name AS org_name,
    o.default_currency AS currency,
    COUNT(DISTINCT b.id) AS total_borrowers,
    COUNT(DISTINCT l.id) AS total_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'ACTIVE') AS active_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'PENDING') AS pending_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'OVERDUE') AS overdue_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'PAID') AS paid_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'DEFAULTED') AS defaulted_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'WRITTEN_OFF') AS written_off_loans,
    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'RESTRUCTURED') AS restructured_loans,
    COALESCE(SUM(l.amount) FILTER (WHERE l.status IN ('ACTIVE','OVERDUE')), 0) AS active_portfolio,
    COALESCE(SUM(l.total_paid), 0) AS total_collected,
    COALESCE(SUM(l.outstanding_balance) FILTER (WHERE l.status='ACTIVE'), 0) AS outstanding_balance,
    COUNT(DISTINCT p.id) FILTER (WHERE p.paid = FALSE AND p.due_date < CURRENT_DATE) AS overdue_installments,
    COUNT(DISTINCT k.id) FILTER (WHERE k.result = 'MANUAL_REVIEW') AS pending_kyc_reviews,
    COUNT(DISTINCT pa.id) FILTER (WHERE pa.status = 'PENDING_REVIEW') AS pending_public_applications,
    COUNT(DISTINCT u.id) AS total_users
FROM organizations o
LEFT JOIN borrowers b ON b.organization_id = o.id
LEFT JOIN loans l ON l.organization_id = o.id
LEFT JOIN payments p ON p.organization_id = o.id
LEFT JOIN kyc_checks k ON k.organization_id = o.id
LEFT JOIN public_applications pa ON pa.organization_id = o.id
LEFT JOIN app_users u ON u.organization_id = o.id
GROUP BY o.id, o.name, o.default_currency;

-- Payment transaction idempotency: one transaction ID per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_org_transaction_id
    ON payments (organization_id, transaction_id)
    WHERE transaction_id IS NOT NULL;
