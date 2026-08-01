
ALTER TABLE loans ADD COLUMN IF NOT EXISTS credit_quality   VARCHAR(20) NOT NULL DEFAULT 'CURRENT';
ALTER TABLE loans ADD COLUMN IF NOT EXISTS arrears_status   VARCHAR(20) NOT NULL DEFAULT 'NOT_DUE';
ALTER TABLE loans ADD COLUMN IF NOT EXISTS collections_stage VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE loans ADD COLUMN IF NOT EXISTS classified_at    TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_loans_credit_quality ON loans(credit_quality);
CREATE INDEX IF NOT EXISTS idx_loans_arrears_status ON loans(arrears_status);


UPDATE loans SET
  credit_quality = CASE
    WHEN status = 'WRITTEN_OFF' THEN 'LOSS'
    WHEN days_overdue IS NULL OR days_overdue <= 0 THEN 'CURRENT'
    WHEN days_overdue <= 30  THEN 'WATCH'
    WHEN days_overdue <= 90  THEN 'SUBSTANDARD'
    WHEN days_overdue <= 180 THEN 'DOUBTFUL'
    ELSE 'LOSS'
  END,
  arrears_status = CASE
    WHEN status = 'WRITTEN_OFF' THEN 'PAST_DUE'
    WHEN days_overdue IS NULL OR days_overdue <= 0 THEN 'NOT_DUE'
    ELSE 'PAST_DUE'
  END,
  collections_stage = CASE
    WHEN status = 'WRITTEN_OFF' THEN 'RECOVERY'
    WHEN days_overdue IS NULL OR days_overdue <= 0 THEN 'NORMAL'
    WHEN days_overdue <= 30  THEN 'REMINDER'
    WHEN days_overdue <= 90  THEN 'COLLECTION'
    WHEN days_overdue <= 365 THEN 'LEGAL'
    ELSE 'RECOVERY'
  END,
  classified_at = now()
WHERE status IN ('ACTIVE','OVERDUE','DEFAULTED','RESTRUCTURED','WRITTEN_OFF','DISBURSED');