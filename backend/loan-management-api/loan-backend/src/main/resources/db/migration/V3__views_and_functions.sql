-- ================================================================
-- V3: Database views, triggers, and utility functions
-- ================================================================

-- Portfolio summary view per organization
CREATE OR REPLACE VIEW v_portfolio_summary AS
SELECT
    o.id                                                               AS org_id,
    o.name                                                             AS org_name,
    o.default_currency                                                 AS currency,
    COUNT(l.id)                                                        AS total_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'ACTIVE')                    AS active_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'PENDING')                   AS pending_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'OVERDUE')                   AS overdue_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'PAID')                      AS completed_loans,
    COUNT(l.id) FILTER (WHERE l.status = 'DEFAULTED')                 AS defaulted_loans,
    COALESCE(SUM(l.amount) FILTER (WHERE l.status IN ('ACTIVE','OVERDUE')), 0) AS active_principal,
    COALESCE(SUM(l.total_paid), 0)                                    AS total_collected,
    COALESCE(SUM(l.outstanding_balance) FILTER (WHERE l.status='ACTIVE'), 0) AS outstanding_balance,
    COUNT(DISTINCT l.borrower_id)                                      AS total_borrowers
FROM organizations o
LEFT JOIN loans l ON l.organization_id = o.id
GROUP BY o.id, o.name, o.default_currency;

-- Overdue payments view
CREATE OR REPLACE VIEW v_overdue_payments AS
SELECT
    p.id,
    p.loan_id,
    p.due_date,
    p.amount,
    l.reference_number,
    l.organization_id,
    b.first_name || ' ' || COALESCE(b.last_name, '') AS borrower_name,
    b.email  AS borrower_email,
    b.phone  AS borrower_phone,
    CURRENT_DATE - p.due_date                         AS days_overdue
FROM payments p
JOIN loans     l ON l.id = p.loan_id
JOIN borrowers b ON b.id = l.borrower_id
WHERE p.paid = FALSE
  AND p.due_date < CURRENT_DATE
ORDER BY p.due_date ASC;

-- Auto-update updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers to all tables with updated_at
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['organizations','app_users','loans','borrowers'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_trigger
            WHERE tgname = 'trg_' || tbl || '_updated_at'
        ) THEN
            EXECUTE format(
                'CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I
                 FOR EACH ROW EXECUTE FUNCTION update_updated_at()', tbl, tbl
            );
        END IF;
    END LOOP;
END;
$$;
