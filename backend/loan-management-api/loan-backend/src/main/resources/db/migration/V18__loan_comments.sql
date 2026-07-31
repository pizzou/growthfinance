-- Lets a loan officer leave a note on an application — e.g. "please upload
-- your land title document" — that the applicant can see on the public
-- tracking page. visible_to_applicant lets staff also keep purely internal
-- notes that never reach the applicant.

CREATE TABLE loan_comments (
    id                   BIGSERIAL PRIMARY KEY,
    loan_id              BIGINT NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    author_id            BIGINT REFERENCES app_users(id),
    message              TEXT NOT NULL,
    visible_to_applicant BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_loan_comments_loan ON loan_comments(loan_id, created_at);
