ALTER TABLE loans
    ADD COLUMN created_by BIGINT;

ALTER TABLE loans
    ADD CONSTRAINT fk_loan_created_by
    FOREIGN KEY (created_by)
    REFERENCES app_users(id);