-- ================================================================
-- V30: Create payment_schedules table matching PaymentSchedule.java exactly
-- Fixes Schema-validation: missing table [payment_schedules] boot loop
-- ================================================================
CREATE TABLE IF NOT EXISTS payment_schedules (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    installment_amount DOUBLE PRECISION NOT NULL,
    principal_amount DOUBLE PRECISION NOT NULL,
    interest_amount DOUBLE PRECISION NOT NULL,
    penalty_amount DOUBLE PRECISION DEFAULT 0.0,
    amount_paid DOUBLE PRECISION DEFAULT 0.0,
    remaining_balance DOUBLE PRECISION DEFAULT 0.0,
    status VARCHAR(50) DEFAULT 'PENDING',
    paid_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key mapping that enforces structural metadata integrity
    CONSTRAINT fk_payment_schedules_loan FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
);

-- Optimization indexing to make your public website tracking page load instantly
CREATE INDEX IF NOT EXISTS idx_payment_schedules_loan_id ON payment_schedules(loan_id);
