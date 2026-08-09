-- ================================================================
-- V29: Holiday calendar
--
-- Nothing in the system previously knew about public holidays --
-- repayment schedules were generated purely by calendar-month
-- arithmetic, so a due date could land on a bank holiday (or a
-- weekend) with no adjustment. This adds an organization-scoped
-- holiday list and wires it into schedule generation so a due date
-- that falls on a holiday or weekend rolls forward to the next
-- genuine business day, matching standard lending practice.
-- ================================================================
CREATE TABLE IF NOT EXISTS holidays (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    holiday_date    DATE NOT NULL,
    name            VARCHAR(255) NOT NULL,
    recurring_annually BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(organization_id, holiday_date)
);

CREATE INDEX IF NOT EXISTS idx_holidays_org_date ON holidays(organization_id, holiday_date);