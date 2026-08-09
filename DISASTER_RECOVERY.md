# Disaster Recovery — Backup, Restore, and the Test You Actually Need to Run

**Honesty check first:** I (the AI assistant) cannot execute this test for you. I don't have
network access or a running copy of your `docker-compose` stack from this session — I can
write and improve the scripts, but "the backup works" is a claim only *you* running the restore
against a real database can verify. An untested backup is not a backup. Please actually run
Section 2 below, on a real environment, before you consider this closed.

## 1. What backs up, and where

`deploy/scripts/backup.sh` runs `pg_dump` against the `postgres` container, gzips it, and:
- encrypts it if `BACKUP_ENCRYPTION_KEY` is set (**do set this** — a Postgres dump contains the
  ciphertext of encrypted columns, but also everything else in plaintext: loan amounts, staff
  emails, audit logs, comments)
- ships it off-host if `BACKUP_REMOTE_CMD` is set (**do set this** — e.g.
  `BACKUP_REMOTE_CMD='aws s3 cp'` or `BACKUP_REMOTE_CMD='rclone copy'`; a backup sitting on the
  same disk as the database survives everything except the one failure mode that actually
  matters, which is losing that disk/host)

Schedule it — the script itself doesn't self-schedule:
```bash
# crontab -e, on the host running docker-compose
0 2 * * * BACKUP_ENCRYPTION_KEY=... BACKUP_REMOTE_CMD='aws s3 cp' /path/to/deploy/scripts/backup.sh >> /var/log/loansaas-backup.log 2>&1
```

## 2. The test — run this for real, on a schedule (quarterly minimum)

1. Take a fresh backup: `./deploy/scripts/backup.sh`
2. Spin up a **separate** Postgres instance — not the production one — e.g.:
   ```bash
   docker run -d --name dr-test-pg -e POSTGRES_USER=loansaas -e POSTGRES_PASSWORD=test \
     -e POSTGRES_DB=loansaas -p 5433:5432 postgres:16
   ```
3. Restore the backup into it (adapt `restore.sh`'s psql line to point at `dr-test-pg:5433`
   instead of `docker-compose exec postgres`, since this is intentionally not touching prod).
4. Point a scratch instance of the backend at that restored database
   (`SPRING_PROFILES_ACTIVE=default`, `DATASOURCE_URL` pointed at `dr-test-pg`) and confirm:
   - it boots without Flyway errors
   - you can log in as a real user from the restored data
   - a loan you know existed before the backup is actually there, with the right balance
5. Tear down the scratch environment. Record the date, who ran it, how long steps 2–4 took
   (this is your actual Recovery Time Objective, not a guess), and any issues found, in this
   file's changelog below.
6. If it failed at any step: that's the point of testing it now instead of during a real
   incident. Fix the actual problem, not just this test run.

## 3. What "disaster recovery" doesn't cover yet

This runbook is about *data* recovery — it says nothing about how fast a new backend/frontend
instance can be brought up if the host running docker-compose itself is lost (see
`HIGH_AVAILABILITY.md` for that gap; today, the honest answer is "however long it takes to
provision a new host and run `docker-compose up` by hand").

## 4. Test log

| Date | Run by | RTO observed | Result | Notes |
|------|--------|---------------|--------|-------|
| _(none yet — this is the first entry that should exist)_ | | | | |
