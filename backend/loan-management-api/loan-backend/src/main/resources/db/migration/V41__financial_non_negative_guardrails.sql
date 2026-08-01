ALTER TABLE loans
    ADD CONSTRAINT chk_loans_amount_nonnegative CHECK (amount >= 0),
    ADD CONSTRAINT chk_loans_processing_fee_nonnegative CHECK (processing_fee IS NULL OR processing_fee >= 0),
    ADD CONSTRAINT chk_loans_disbursed_amount_nonnegative CHECK (disbursed_amount IS NULL OR disbursed_amount >= 0),
    ADD CONSTRAINT chk_loans_total_repayable_nonnegative CHECK (total_repayable IS NULL OR total_repayable >= 0),
    ADD CONSTRAINT chk_loans_total_paid_nonnegative CHECK (total_paid IS NULL OR total_paid >= 0),
    ADD CONSTRAINT chk_loans_outstanding_nonnegative CHECK (outstanding_balance IS NULL OR outstanding_balance >= 0),
    ADD CONSTRAINT chk_loans_collateral_nonnegative CHECK (collateral_value IS NULL OR collateral_value >= 0);

ALTER TABLE payment_schedules
    ADD CONSTRAINT chk_schedule_installment_nonnegative CHECK (installment_amount >= 0),
    ADD CONSTRAINT chk_schedule_principal_nonnegative CHECK (principal_amount >= 0),
    ADD CONSTRAINT chk_schedule_interest_nonnegative CHECK (interest_amount >= 0),
    ADD CONSTRAINT chk_schedule_penalty_nonnegative CHECK (penalty_amount IS NULL OR penalty_amount >= 0),
    ADD CONSTRAINT chk_schedule_paid_nonnegative CHECK (amount_paid IS NULL OR amount_paid >= 0),
    ADD CONSTRAINT chk_schedule_balance_nonnegative CHECK (remaining_balance IS NULL OR remaining_balance >= 0);