-- V26: staff had nowhere to store internal documents (policies, contracts, board minutes,
-- internal memos) — the only file storage in the app was BorrowerFile, which is specifically
-- KYC/application documents tied to one borrower. This adds a separate internal document
-- library, org-scoped like everything else, not tied to any borrower.
CREATE TABLE IF NOT EXISTS internal_documents (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    title           VARCHAR(255) NOT NULL,
    category        VARCHAR(50)  NOT NULL DEFAULT 'OTHER',
    description     VARCHAR(1000),
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(100) NOT NULL,
    file_size       BIGINT       NOT NULL,
    data            BYTEA        NOT NULL,
    uploaded_by_id  BIGINT REFERENCES app_users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_internal_documents_org ON internal_documents(organization_id);
CREATE INDEX IF NOT EXISTS idx_internal_documents_category ON internal_documents(organization_id, category);

COMMENT ON TABLE internal_documents IS
  'Staff document library — policies, contracts, memos, templates. Not tied to any borrower; '
  'see BorrowerFile for KYC/application documents instead.';
