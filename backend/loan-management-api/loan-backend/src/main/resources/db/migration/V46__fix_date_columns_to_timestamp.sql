ALTER TABLE loans
ALTER COLUMN disbursed_at TYPE TIMESTAMP(6)
USING disbursed_at::timestamp;

ALTER TABLE payments
ALTER COLUMN interest_calculation_date TYPE TIMESTAMP(6)
USING interest_calculation_date::timestamp;