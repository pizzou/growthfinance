-- Persists website contact-form submissions. Previously these only existed as a one-shot
-- in-app Notification with no permanent record — this is the actual inbox.
CREATE TABLE IF NOT EXISTS contact_messages (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    name            VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    subject         VARCHAR(255),
    message         TEXT NOT NULL,
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP,
    read_at         TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_contact_messages_org ON contact_messages (organization_id);
