# High Availability &amp; Single Points of Failure

## What's actually true today

`docker-compose.yml` runs exactly one of each: one Postgres container, one backend container,
one frontend container, one nginx container, on one host. **If that host goes down, the whole
platform goes down** — there is no automatic failover to anything. That's the honest starting
point; nothing below changes it by itself.

## What was fixed this pass (so scaling out doesn't misbehave when you do it)

The app itself had a real bug that would have made "just run more backend instances" actively
harmful: `ScheduledJobs` (overdue checks, payment reminders, FX refresh, EOD interest accrual)
had no cross-instance coordination. Two backend instances would each independently fire the
same cron job at the same time — meaning borrowers would get duplicate overdue SMS/emails, and
worse, the EOD accrual's check-then-insert on `IdempotencyKeyRepository` had a race window where
both instances could pass the "already done today?" check before either committed, double-
posting interest to the ledger. Added `SchedulerLockService` (see `V25__scheduler_locks.sql`) —
a simple DB-based mutual exclusion so only one instance runs any given job at a time, regardless
of how many instances you run. This was a genuine correctness bug, not just a nice-to-have.

The rest of the app was already reasonably scale-friendly: auth is stateless JWT (no server-side
session to stick a user to one instance), and file uploads/downloads go through the database
rather than local disk, so there's no "uploaded file only exists on the instance that received
it" problem.

## What's still a single point of failure — and why I can't fix this from here

These are infrastructure/provisioning decisions, not application code:

- **Postgres**: one container, no replica, no automated failover. A managed Postgres (RDS,
  Cloud SQL, a managed instance from your cloud/host of choice) with a standby replica is the
  standard fix — this is a hosting decision and a cost decision, not something addable via a
  pull request to this repo.
- **The backend**: one container. Running 2+ instances behind a load balancer is safe now (see
  above), but someone has to actually provision the load balancer and the additional instances.
- **The host itself**: `docker-compose` assumes one machine. Moving to something that
  schedules across multiple machines (a managed container service, or Kubernetes) is a bigger
  step than adding replicas — worth doing eventually, not necessarily before your first real
  customers.

## A reasonable order to actually do this in

1. Managed Postgres with automated backups and at least one standby — highest value, since
   losing the database is the scenario that actually loses customer data, not just uptime.
2. A second backend instance + load balancer — now safe to do given the scheduler fix above.
3. Move off a single Docker host once traffic/reliability needs justify the operational
   complexity of doing so — for many single-bank deployments, steps 1–2 plus a solid
   `DISASTER_RECOVERY.md` test cadence (see that file) is a defensible bar, not everyone needs
   full multi-region Kubernetes on day one.
