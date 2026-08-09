-- V12: tamper-evident hash chain on audit_logs, for compliance-grade
-- audit logging — each entry's hash covers its own content plus the
-- previous entry's hash, so any historical edit or deletion is detectable
-- via GET /api/audit/verify.
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entry_hash    VARCHAR(64);
