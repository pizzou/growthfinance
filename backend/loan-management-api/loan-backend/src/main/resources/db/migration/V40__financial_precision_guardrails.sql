ALTER TABLE loans
    ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric, 2),
    ALTER COLUMN next_installment_amount TYPE NUMERIC(19,2) USING ROUND(next_installment_amount::numeric, 2),
    ALTER COLUMN processing_fee TYPE NUMERIC(19,2) USING ROUND(processing_fee::numeric, 2),
    ALTER COLUMN disbursed_amount TYPE NUMERIC(19,2) USING ROUND(disbursed_amount::numeric, 2),
    ALTER COLUMN total_repayable TYPE NUMERIC(19,2) USING ROUND(total_repayable::numeric, 2),
    ALTER COLUMN total_paid TYPE NUMERIC(19,2) USING ROUND(total_paid::numeric, 2),
    ALTER COLUMN outstanding_balance TYPE NUMERIC(19,2) USING ROUND(outstanding_balance::numeric, 2),
    ALTER COLUMN collateral_value TYPE NUMERIC(19,2) USING ROUND(collateral_value::numeric, 2);

ALTER TABLE payments
    ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric, 2),
    ALTER COLUMN principal_component TYPE NUMERIC(19,2) USING ROUND(principal_component::numeric, 2),
    ALTER COLUMN interest_component TYPE NUMERIC(19,2) USING ROUND(interest_component::numeric, 2),
    ALTER COLUMN amount_paid TYPE NUMERIC(19,2) USING ROUND(amount_paid::numeric, 2),
    ALTER COLUMN penalty TYPE NUMERIC(19,2) USING ROUND(penalty::numeric, 2),
    ALTER COLUMN waived_amount TYPE NUMERIC(19,2) USING ROUND(waived_amount::numeric, 2),
    ALTER COLUMN outstanding_after TYPE NUMERIC(19,2) USING ROUND(outstanding_after::numeric, 2);

ALTER TABLE payment_schedules
    ALTER COLUMN installment_amount TYPE NUMERIC(19,2) USING ROUND(installment_amount::numeric, 2),
    ALTER COLUMN principal_amount TYPE NUMERIC(19,2) USING ROUND(principal_amount::numeric, 2),
    ALTER COLUMN interest_amount TYPE NUMERIC(19,2) USING ROUND(interest_amount::numeric, 2),
    ALTER COLUMN penalty_amount TYPE NUMERIC(19,2) USING ROUND(penalty_amount::numeric, 2),
    ALTER COLUMN amount_paid TYPE NUMERIC(19,2) USING ROUND(amount_paid::numeric, 2),
    ALTER COLUMN remaining_balance TYPE NUMERIC(19,2) USING ROUND(remaining_balance::numeric, 2);

ALTER TABLE journal_lines
    ALTER COLUMN debit TYPE NUMERIC(19,2) USING ROUND(debit::numeric, 2),
    ALTER COLUMN credit TYPE NUMERIC(19,2) USING ROUND(credit::numeric, 2);

ALTER TABLE loan_products
    ALTER COLUMN min_amount TYPE NUMERIC(19,2) USING ROUND(min_amount::numeric, 2),
    ALTER COLUMN max_amount TYPE NUMERIC(19,2) USING ROUND(max_amount::numeric, 2);

ALTER TABLE expenses
    ALTER COLUMN amount TYPE NUMERIC(19,2) USING ROUND(amount::numeric, 2);

ALTER TABLE borrowers
    ALTER COLUMN monthly_income TYPE NUMERIC(19,2) USING ROUND(monthly_income::numeric, 2),
    ALTER COLUMN monthly_expenses TYPE NUMERIC(19,2) USING ROUND(monthly_expenses::numeric, 2),
    ALTER COLUMN net_worth TYPE NUMERIC(19,2) USING ROUND(net_worth::numeric, 2);