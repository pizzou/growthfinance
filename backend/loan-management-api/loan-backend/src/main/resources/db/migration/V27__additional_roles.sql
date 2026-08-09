-- ================================================================
-- V27: Add missing/additional staff roles
--
-- ACCOUNTANT existed in the Java RoleName enum but was never actually
-- inserted by any prior migration -- assigning it to a user would have
-- failed with "role not found" since the row never existed. Fixed here
-- alongside three new roles the platform was missing entirely:
-- AUDITOR (read-only oversight/compliance access), COLLECTIONS_OFFICER
-- (dedicated overdue-loan collections work, distinct from a general
-- Loan Officer), and CUSTOMER_SUPPORT (borrower-facing help desk access
-- without loan approval/disbursement authority).
-- ================================================================
INSERT INTO roles (name, description) VALUES
    ('ACCOUNTANT',          'Manage chart of accounts, journal entries, and financial reports'),
    ('AUDITOR',             'Read-only access to audit logs, approvals, and compliance records — cannot create, approve, or modify loans'),
    ('COLLECTIONS_OFFICER', 'Manage overdue loan collection cases, reminders, and recovery workflow'),
    ('CUSTOMER_SUPPORT',    'View borrower and loan information to assist customer inquiries — no approval or disbursement authority')
ON CONFLICT (name) DO NOTHING;