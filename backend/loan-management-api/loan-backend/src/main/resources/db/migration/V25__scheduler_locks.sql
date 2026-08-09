-- V25: ScheduledJobs had zero cross-instance coordination. Fine for a single backend instance
-- (the only deployment topology this app has run in so far), but the moment you scale to more
-- than one instance for HA, every instance fires the same @Scheduled job at the same time —
-- meaning borrowers get duplicate overdue SMS/emails, payment reminders sent twice, and (worse)
-- the EOD accrual race could double-post interest if two instances both pass the
-- check-then-insert on IdempotencyKeyRepository before either commits.
CREATE TABLE IF NOT EXISTS scheduler_locks (
    job_name     VARCHAR(100) PRIMARY KEY,
    locked_until TIMESTAMP NOT NULL
);

COMMENT ON TABLE scheduler_locks IS
  'Simple compare-and-swap mutual exclusion for @Scheduled jobs across backend instances. '
  'See SchedulerLockService — a job holds the lock until locked_until, and a second instance '
  'can only steal it once that time has passed.';
