-- V7: public marketing/website content per organization, so each tenant's
-- public site (Growth Finance / Noble Loan / Infinity Loan / any future org)
-- shows genuinely distinct branding and copy instead of a shared demo fallback.
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS tagline        VARCHAR(255);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS mission        TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS vision         TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS founded_year   INT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS facebook_url   VARCHAR(255);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS instagram_url  VARCHAR(255);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS linkedin_url   VARCHAR(255);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS twitter_url    VARCHAR(255);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS whatsapp_url   VARCHAR(255);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS map_url        TEXT;
