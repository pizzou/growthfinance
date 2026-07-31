-- V8: full CMS control for the public website — hero copy, stats, services,
-- testimonials and team, editable per-organization from the admin dashboard.
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS hero_headline    VARCHAR(255);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS hero_subtext     TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS stats_json       TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS services_json    TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS testimonials_json TEXT;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS team_json        TEXT;
