ALTER TABLE loans
ALTER COLUMN disbursed_at TYPE TIMESTAMP(6)
USING disbursed_at::timestamp;