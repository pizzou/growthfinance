

UPDATE journal_lines
SET debit = COALESCE(debit, 0),
    credit = COALESCE(credit, 0);

ALTER TABLE journal_lines
    ALTER COLUMN debit TYPE NUMERIC(19,2) USING ROUND(debit::numeric, 2),
    ALTER COLUMN credit TYPE NUMERIC(19,2) USING ROUND(credit::numeric, 2),
    ALTER COLUMN debit SET DEFAULT 0.00,
    ALTER COLUMN credit SET DEFAULT 0.00,
    ALTER COLUMN debit SET NOT NULL,
    ALTER COLUMN credit SET NOT NULL;

ALTER TABLE journal_lines
    DROP CONSTRAINT IF EXISTS ck_journal_line_non_negative,
    DROP CONSTRAINT IF EXISTS ck_journal_line_one_side;

ALTER TABLE journal_lines
    ADD CONSTRAINT ck_journal_line_non_negative
        CHECK (debit >= 0 AND credit >= 0),
    ADD CONSTRAINT ck_journal_line_one_side
        CHECK ((debit = 0 AND credit > 0) OR (credit = 0 AND debit > 0));

CREATE INDEX IF NOT EXISTS idx_journal_line_account_entry
    ON journal_lines(account_id, journal_entry_id);

-- A deferred constraint trigger lets a complete multi-line journal entry be assembled inside
-- one transaction while still enforcing that the ledger is balanced at COMMIT time.
CREATE OR REPLACE FUNCTION enforce_journal_entry_balanced()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    debit_total NUMERIC(19,2);
    credit_total NUMERIC(19,2);
BEGIN
    SELECT COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
      INTO debit_total, credit_total
      FROM journal_lines
     WHERE journal_entry_id = COALESCE(NEW.journal_entry_id, OLD.journal_entry_id);

    IF debit_total <> credit_total THEN
        RAISE EXCEPTION 'Journal entry % is not balanced: debits % credits %',
            COALESCE(NEW.journal_entry_id, OLD.journal_entry_id), debit_total, credit_total;
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_journal_entry_balanced ON journal_lines;
CREATE CONSTRAINT TRIGGER trg_journal_entry_balanced
AFTER INSERT OR UPDATE ON journal_lines
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_journal_entry_balanced();

COMMENT ON COLUMN journal_lines.debit IS 'Accounting amount in fixed precision NUMERIC(19,2); never floating point.';
COMMENT ON COLUMN journal_lines.credit IS 'Accounting amount in fixed precision NUMERIC(19,2); never floating point.';