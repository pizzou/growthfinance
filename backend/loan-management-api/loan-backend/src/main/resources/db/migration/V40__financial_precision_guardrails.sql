-- V40__financial_precision_guardrails.sql

-- ============================================================
-- LOANS
-- ============================================================

ALTER TABLE loans
    ALTER COLUMN amount
        TYPE NUMERIC(19,2)
        USING CAST(amount AS NUMERIC(19,2)),

    ALTER COLUMN next_installment_amount
        TYPE NUMERIC(19,2)
        USING CAST(next_installment_amount AS NUMERIC(19,2)),

    ALTER COLUMN processing_fee
        TYPE NUMERIC(19,2)
        USING CAST(processing_fee AS NUMERIC(19,2)),

    ALTER COLUMN disbursed_amount
        TYPE NUMERIC(19,2)
        USING CAST(disbursed_amount AS NUMERIC(19,2)),

    ALTER COLUMN total_repayable
        TYPE NUMERIC(19,2)
        USING CAST(total_repayable AS NUMERIC(19,2)),

    ALTER COLUMN total_paid
        TYPE NUMERIC(19,2)
        USING CAST(total_paid AS NUMERIC(19,2)),

    ALTER COLUMN outstanding_balance
        TYPE NUMERIC(19,2)
        USING CAST(outstanding_balance AS NUMERIC(19,2)),

    ALTER COLUMN collateral_value
        TYPE NUMERIC(19,2)
        USING CAST(collateral_value AS NUMERIC(19,2));


-- ============================================================
-- PAYMENTS
-- ============================================================

ALTER TABLE payments
    ALTER COLUMN amount
        TYPE NUMERIC(19,2)
        USING CAST(amount AS NUMERIC(19,2)),

    ALTER COLUMN principal_component
        TYPE NUMERIC(19,2)
        USING CAST(principal_component AS NUMERIC(19,2)),

    ALTER COLUMN interest_component
        TYPE NUMERIC(19,2)
        USING CAST(interest_component AS NUMERIC(19,2)),

    ALTER COLUMN amount_paid
        TYPE NUMERIC(19,2)
        USING CAST(amount_paid AS NUMERIC(19,2)),

    ALTER COLUMN penalty
        TYPE NUMERIC(19,2)
        USING CAST(penalty AS NUMERIC(19,2)),

    ALTER COLUMN waived_amount
        TYPE NUMERIC(19,2)
        USING CAST(waived_amount AS NUMERIC(19,2)),

    ALTER COLUMN outstanding_after
        TYPE NUMERIC(19,2)
        USING CAST(outstanding_after AS NUMERIC(19,2));


-- ============================================================
-- PAYMENT SCHEDULES
-- ============================================================

ALTER TABLE payment_schedules
    ALTER COLUMN installment_amount
        TYPE NUMERIC(19,2)
        USING CAST(installment_amount AS NUMERIC(19,2)),

    ALTER COLUMN principal_amount
        TYPE NUMERIC(19,2)
        USING CAST(principal_amount AS NUMERIC(19,2)),

    ALTER COLUMN interest_amount
        TYPE NUMERIC(19,2)
        USING CAST(interest_amount AS NUMERIC(19,2)),

    ALTER COLUMN penalty_amount
        TYPE NUMERIC(19,2)
        USING CAST(penalty_amount AS NUMERIC(19,2)),

    ALTER COLUMN amount_paid
        TYPE NUMERIC(19,2)
        USING CAST(amount_paid AS NUMERIC(19,2)),

    ALTER COLUMN remaining_balance
        TYPE NUMERIC(19,2)
        USING CAST(remaining_balance AS NUMERIC(19,2));


-- ============================================================
-- JOURNAL LINES
-- ============================================================

ALTER TABLE journal_lines
    ALTER COLUMN debit
        TYPE NUMERIC(19,2)
        USING CAST(debit AS NUMERIC(19,2)),

    ALTER COLUMN credit
        TYPE NUMERIC(19,2)
        USING CAST(credit AS NUMERIC(19,2));


-- ============================================================
-- LOAN PRODUCTS
-- ============================================================

ALTER TABLE loan_products
    ALTER COLUMN min_amount
        TYPE NUMERIC(19,2)
        USING CAST(min_amount AS NUMERIC(19,2)),

    ALTER COLUMN max_amount
        TYPE NUMERIC(19,2)
        USING CAST(max_amount AS NUMERIC(19,2));


-- ============================================================
-- EXPENSES
-- ============================================================

ALTER TABLE expenses
    ALTER COLUMN amount
        TYPE NUMERIC(19,2)
        USING CAST(amount AS NUMERIC(19,2));


-- ============================================================
-- BORROWERS
-- ============================================================

ALTER TABLE borrowers
    ALTER COLUMN monthly_income
        TYPE NUMERIC(19,2)
        USING CAST(monthly_income AS NUMERIC(19,2)),

    ALTER COLUMN monthly_expenses
        TYPE NUMERIC(19,2)
        USING CAST(monthly_expenses AS NUMERIC(19,2)),

    ALTER COLUMN net_worth
        TYPE NUMERIC(19,2)
        USING CAST(net_worth AS NUMERIC(19,2));


-- ============================================================
-- CREDIT BUREAU CHECKS
-- ============================================================

ALTER TABLE credit_bureau_checks
    ALTER COLUMN total_monthly_obligations
        TYPE NUMERIC(19,2)
        USING CAST(total_monthly_obligations AS NUMERIC(19,2)),

    ALTER COLUMN total_outstanding_debt
        TYPE NUMERIC(19,2)
        USING CAST(total_outstanding_debt AS NUMERIC(19,2));