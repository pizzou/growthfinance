-- V6: enrich audit_logs with parsed OS/browser and best-effort IP geolocation
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS operating_system VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS browser           VARCHAR(50);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS location          VARCHAR(150);
