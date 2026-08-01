
CREATE UNIQUE INDEX IF NOT EXISTS uq_journal_source_transaction
    ON journal_entries (organization_id, source_type, source_id);

COMMENT ON INDEX uq_journal_source_transaction IS
    'Prevents duplicate GL journal entries for the same financial source transaction';