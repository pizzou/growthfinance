ALTER TABLE payments
ADD COLUMN IF NOT EXISTS interest_calculation_date DATE;