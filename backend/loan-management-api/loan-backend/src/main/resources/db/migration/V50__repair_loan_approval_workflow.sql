

BEGIN;



UPDATE loan_approvals
SET
    status = 'APPROVED',
    comments = CASE
        WHEN comments IS NULL OR btrim(comments) = ''
            THEN 'Legacy LOAN_OFFICER approval step retired by V50 approval workflow migration.'
        ELSE comments
             || E'\n'
             || 'Legacy LOAN_OFFICER approval step retired by V50 approval workflow migration.'
    END,
    decided_at = COALESCE(decided_at, CURRENT_TIMESTAMP)
WHERE UPPER(TRIM(required_role)) = 'LOAN_OFFICER'
  AND UPPER(TRIM(status)) = 'PENDING';


-- ============================================================
-- 2. Remove duplicate pending MANAGER steps
--
-- Keep the earliest pending Manager step for each loan.
-- ============================================================

WITH ranked_manager_steps AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY loan_id
            ORDER BY step_order ASC, id ASC
        ) AS rn
    FROM loan_approvals
    WHERE UPPER(TRIM(required_role)) = 'MANAGER'
      AND UPPER(TRIM(status)) = 'PENDING'
)
UPDATE loan_approvals la
SET
    status = 'APPROVED',
    comments = CASE
        WHEN la.comments IS NULL OR btrim(la.comments) = ''
            THEN 'Duplicate legacy pending Manager approval retired by V50.'
        ELSE la.comments
             || E'\n'
             || 'Duplicate legacy pending Manager approval retired by V50.'
    END,
    decided_at = COALESCE(la.decided_at, CURRENT_TIMESTAMP)
FROM ranked_manager_steps rms
WHERE la.id = rms.id
  AND rms.rn > 1;


-- ============================================================
-- 3. Remove duplicate pending ADMIN steps
--
-- Keep the earliest pending Admin step for each loan.
-- ============================================================

WITH ranked_admin_steps AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY loan_id
            ORDER BY step_order ASC, id ASC
        ) AS rn
    FROM loan_approvals
    WHERE UPPER(TRIM(required_role)) = 'ADMIN'
      AND UPPER(TRIM(status)) = 'PENDING'
)
UPDATE loan_approvals la
SET
    status = 'APPROVED',
    comments = CASE
        WHEN la.comments IS NULL OR btrim(la.comments) = ''
            THEN 'Duplicate legacy pending Admin approval retired by V50.'
        ELSE la.comments
             || E'\n'
             || 'Duplicate legacy pending Admin approval retired by V50.'
    END,
    decided_at = COALESCE(la.decided_at, CURRENT_TIMESTAMP)
FROM ranked_admin_steps ras
WHERE la.id = ras.id
  AND ras.rn > 1;


-- ============================================================
-- 4. Re-number approval steps
--
-- Only active approval roles are considered.
--
-- Historical rows remain in the table, but the active workflow
-- gets deterministic step ordering.
-- ============================================================

WITH active_steps AS (
    SELECT
        id,
        loan_id,
        ROW_NUMBER() OVER (
            PARTITION BY loan_id
            ORDER BY
                CASE
                    WHEN UPPER(TRIM(required_role)) = 'MANAGER'
                        THEN 1
                    WHEN UPPER(TRIM(required_role)) = 'ADMIN'
                        THEN 2
                    ELSE 99
                END,
                step_order ASC,
                id ASC
        ) AS new_step
    FROM loan_approvals
    WHERE UPPER(TRIM(status)) = 'PENDING'
      AND UPPER(TRIM(required_role)) IN ('MANAGER', 'ADMIN')
)
UPDATE loan_approvals la
SET step_order = active_steps.new_step
FROM active_steps
WHERE la.id = active_steps.id;


-- ============================================================
-- 5. Normalize step names
-- ============================================================

UPDATE loan_approvals
SET step_name = 'Branch Manager Approval'
WHERE UPPER(TRIM(required_role)) = 'MANAGER'
  AND UPPER(TRIM(status)) = 'PENDING';


UPDATE loan_approvals
SET step_name = 'Credit Committee / Admin Approval'
WHERE UPPER(TRIM(required_role)) = 'ADMIN'
  AND UPPER(TRIM(status)) = 'PENDING';


-- ============================================================
-- 6. Repair loans that now have NO pending approval step
--
-- These are loans that previously had only a LOAN_OFFICER
-- pending step.
--
-- We create the appropriate Manager step.
--
-- The organization maximum is used to determine whether the
-- loan also requires Admin approval.
-- ============================================================

INSERT INTO loan_approvals (
    loan_id,
    organization_id,
    step_order,
    required_role,
    step_name,
    status
)
SELECT
    l.id,
    l.organization_id,
    1,
    'MANAGER',
    'Branch Manager Approval',
    'PENDING'
FROM loans l
WHERE
    l.status IN ('PENDING', 'SUBMITTED', 'UNDER_REVIEW')
    AND NOT EXISTS (
        SELECT 1
        FROM loan_approvals la
        WHERE la.loan_id = l.id
          AND UPPER(TRIM(la.status)) = 'PENDING'
          AND UPPER(TRIM(la.required_role))
              IN ('MANAGER', 'ADMIN')
    );


-- ============================================================
-- 7. Add Admin step for high-exposure loans
--
-- High exposure = more than 60% of organization's max loan.
--
-- We only add Admin after Manager for loans that currently have
-- a pending Manager step.
-- ============================================================

INSERT INTO loan_approvals (
    loan_id,
    organization_id,
    step_order,
    required_role,
    step_name,
    status
)
SELECT
    l.id,
    l.organization_id,
    2,
    'ADMIN',
    'Credit Committee / Admin Approval',
    'PENDING'
FROM loans l
JOIN organizations o
    ON o.id = l.organization_id
WHERE
    l.status IN ('PENDING', 'SUBMITTED', 'UNDER_REVIEW')
    AND o.max_loan_amount IS NOT NULL
    AND o.max_loan_amount > 0
    AND l.amount > (o.max_loan_amount * 0.60)
    AND EXISTS (
        SELECT 1
        FROM loan_approvals manager_step
        WHERE manager_step.loan_id = l.id
          AND UPPER(TRIM(manager_step.required_role)) = 'MANAGER'
          AND UPPER(TRIM(manager_step.status)) = 'PENDING'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM loan_approvals admin_step
        WHERE admin_step.loan_id = l.id
          AND UPPER(TRIM(admin_step.required_role)) = 'ADMIN'
          AND UPPER(TRIM(admin_step.status)) = 'PENDING'
    );


-- ============================================================
-- 8. Ensure there is never a pending LOAN_OFFICER step
-- ============================================================

UPDATE loan_approvals
SET
    status = 'APPROVED',
    comments = CASE
        WHEN comments IS NULL OR btrim(comments) = ''
            THEN 'Legacy LOAN_OFFICER approval step retired by V50.'
        ELSE comments
             || E'\n'
             || 'Legacy LOAN_OFFICER approval step retired by V50.'
    END,
    decided_at = COALESCE(decided_at, CURRENT_TIMESTAMP)
WHERE UPPER(TRIM(required_role)) = 'LOAN_OFFICER'
  AND UPPER(TRIM(status)) = 'PENDING';


-- ============================================================
-- 9. Production indexes
--
-- These improve approval-chain lookup performance.
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_loan_approvals_loan_status
    ON loan_approvals (loan_id, status);

CREATE INDEX IF NOT EXISTS idx_loan_approvals_loan_step
    ON loan_approvals (loan_id, step_order);

CREATE INDEX IF NOT EXISTS idx_loan_approvals_org_status
    ON loan_approvals (organization_id, status);


COMMIT;

