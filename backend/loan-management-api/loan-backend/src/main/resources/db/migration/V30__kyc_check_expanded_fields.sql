-- ================================================================
-- V30: Expand kyc_checks for the full identity-verification pipeline
--
-- The kyc_checks table (V4) only had the original bare-bones columns.
-- KycCheck.java now models a much fuller pipeline -- document/face/
-- liveness/address verification, AML/PEP/sanctions/adverse-media/
-- fraud/duplicate screening, a manual compliance-review decision --
-- and every one of those fields needs its own column, or Hibernate's
-- schema validation (ddl-auto=validate) refuses to start the app at all.
-- ================================================================
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS risk_level           VARCHAR(20);
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS provider_reference   VARCHAR(255);
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS id_verified          BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS face_matched         BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS liveness_passed      BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS address_verified     BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS pep_match            BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS sanctions_match      BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS adverse_media_match  BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS duplicate_customer   BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS fraud_detected       BOOLEAN;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS aml_risk_score       INTEGER;
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS manual_decision      VARCHAR(20);
ALTER TABLE kyc_checks ADD COLUMN IF NOT EXISTS updated_at           TIMESTAMP;

-- Backfill updated_at for any pre-existing rows so it's never null going forward
UPDATE kyc_checks SET updated_at = created_at WHERE updated_at IS NULL;