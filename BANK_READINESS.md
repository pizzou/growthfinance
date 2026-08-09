# Bank Readiness Checklist — Growth Finance Services Ltd

This document is an honest assessment of what this codebase already does
well, what changed in this pass, and — most importantly — everything that
is **still outside the scope of a code change** and needs a decision,
a vendor, or a human process before this platform should hold real
customer money or data. Anyone claiming this platform is "bank ready"
without working through the second half of this list is overstating it.

Status legend: ✅ already in place · 🔧 fixed in this pass · ⚠️ gap, needs
your action · 📋 organizational/process, not code.

---

## 1. What this pass changed

- 🔧 **Added an internal document library** (`InternalDocument` model/service/controller,
  `/dashboard/documents`) — separate from `BorrowerFile`'s KYC/application documents, this is
  for staff to store policies, contracts, board minutes, and templates org-wide, not tied to a
  borrower. Broader file-type allow-list than borrower KYC docs (Office formats included) since
  these are policy/contract documents, not scanned IDs.
- 🔧 **Found the Reports/CSV-export page had no sidebar link at all** — `/dashboard/reports`
  (loan/payment/overdue/summary CSV exports, all already fully implemented and working) existed
  but was unreachable unless you already knew the URL. Added it to the sidebar, alongside the
  new Internal Documents page.
- Confirmed already working, no changes needed: email OTP at login (every user without a TOTP
  app enrolled already gets a 6-digit code emailed at sign-in — `AuthController`/`MailService`),
  and uploaded borrower documents are already visible in-system on both the borrower detail page
  and the loan detail page's Documents tab.
- 🔧 **Worked through the full remaining gap list from this file in one pass** — new supporting
  docs: `SECURITY_SELF_REVIEW.md`, `SECRETS_AND_KEY_ROTATION.md`, `HIGH_AVAILABILITY.md`,
  `DISASTER_RECOVERY.md`, `OBSERVABILITY.md`, `KYC_AML_STATUS.md`. Code changes: fixed a real
  cross-instance scheduler race (`SchedulerLockService`), added structured JSON logging +
  request-correlation IDs, added dependency/container vulnerability scanning to CI
  (`.github/workflows/security-scan.yml`, `.github/dependabot.yml`), encrypted + off-site-capable
  backups, a tighter rate-limit zone for public write endpoints, and made simulated credit
  scores visibly distinguishable from real ones in the UI. Each gap's entry below now says
  specifically what changed versus what's still a genuine infra/business decision — read those,
  not just this summary, since several items are "now documented" rather than "now solved," and
  that distinction matters.
- 🚨 **`DataSeeder` had no environment guard at all — it ran on ANY empty database, including a
  real production deploy on day one**, creating a real admin account
  (`admin@growthfinance.rw` / `Admin@1234`) with a password published in this repo's own docs
  and printed in the boot logs. Added a production bootstrap path (`BOOTSTRAP_ADMIN_EMAIL` /
  `BOOTSTRAP_ADMIN_PASSWORD` / `BOOTSTRAP_ORG_NAME` etc.) that creates exactly one real
  organization and one real admin account with an enforced-strong password and zero demo data,
  and made the demo-data path only run when those variables are absent, with a loud startup
  warning when it does. **If this has ever run against a database you consider "real," rotate
  that admin password immediately — it's not a secret, it's public.**
- 🔧 **The public application form's "I agree to the Terms & Conditions" checkbox linked to
  `href="#"` (nowhere) and was never checked server-side** — a malformed or replayed request
  could create a loan application with no consent at all, and even a normal submission left no
  server-side record that consent was given. Added real `/terms` and `/privacy` pages (draft
  templates, clearly marked as requiring attorney review before publishing to real customers —
  see the banner on each page), server-side rejection of applications that don't check the box,
  and a `terms_accepted_at` timestamp on the loan itself as actual evidence of consent.
- 🚨 **Removed leaked, real-looking Gmail credentials that were hardcoded as config defaults**
  in `application.properties` (`MAIL_USERNAME`/`MAIL_PASSWORD` had an actual account and app
  password baked in as fallback values, left there by a prior session's explicit request and
  flagged in a comment as unresolved). These are now required environment variables with no
  default. **If you've ever run this repo with those defaults in place, treat that Gmail app
  password as compromised — revoke it at https://myaccount.google.com/apppasswords and issue a
  fresh one before deploying.**
- 🔧 **Root cause of "no email reaches the borrower": `MAIL_ENABLED` defaults to `false`
  everywhere** (`application.properties`, `application-dev.properties`, `docker-compose.yml`) —
  by design, so a fresh deploy fails closed instead of silently trying to send through
  unconfigured SMTP. That means email is genuinely off until you set `MAIL_ENABLED=true` plus
  real `MAIL_USERNAME`/`MAIL_PASSWORD` in your `.env`. Added a startup check in `MailService`
  that now logs a loud, explicit error if mail is enabled but credentials are blank — previously
  this failed silently per-message with no indication of why nothing was arriving.
- 🔧 **Loan restructuring, write-off, and moratorium had zero borrower notification** — every
  other loan-lifecycle action (apply, approve, reject, disburse, payment received, document
  verified/rejected, officer comment) emails and/or SMS's the borrower; these three didn't, at
  all, in either channel. Added `sendLoanRestructured`/`sendLoanWrittenOff`/
  `sendMoratoriumGranted` to `MailService` and wired both email and SMS into
  `LoanRestructuringService`, matching the existing best-effort (never blocks the underlying
  operation on a notification failure) pattern used everywhere else.
- 🔧 **Loans can no longer be approved without KYC documents, or disbursed without staff
  verifying them.** Previously `BorrowerFile` (upload, staff review, applicant self-upload)
  was fully built but nothing in `LoanService` ever checked it — a loan could be approved
  and disbursed for a borrower with zero documents on file. `approveLoan()` now blocks on
  any required document type that hasn't been uploaded at all; `disburseLoan()` additionally
  requires those documents to be staff-`VERIFIED`, not merely present. Required types are
  configurable per loan product (`loan_products.required_document_types`, V23 migration),
  defaulting to national ID + proof of address, plus business registration for business loans
  and payslip for personal/salary-advance loans. A new `GET /loans/{id}/document-requirements`
  endpoint and a checklist banner on the loan detail page surface this before the officer
  clicks Approve/Disburse, instead of only at the point of failure.
- 🔧 **Loans with no borrower link fail loudly instead of crashing or silently proceeding.**
  The schema requires `borrower_id NOT NULL` and `createLoan()` always validates it, so this
  shouldn't occur for anything created through the app — but if you find a loan like this
  (e.g. stale test data), it's a genuine data problem: run
  `SELECT id, borrower_id FROM loans WHERE borrower_id IS NULL;` to find any, and either
  relink or delete them. Approve/disburse now reject such a loan with a clear message instead
  of a null pointer exception, and the new document-requirements endpoint degrades gracefully
  rather than throwing.
- 🔧 **Removed a leftover multi-tenant endpoint** (`GET /api/public/tenants`) that publicly
  listed every organization's name, branding, and address on this deployment — dead code from
  before the single-tenant conversion below, confirmed unused by the frontend.
- 🔧 **Single-tenant architecture, end to end.** One backend process, one
  database (`loansaas_growthfinance`), one frontend build, one nginx
  domain. Removed the multi-org docker-compose services, the per-domain
  nginx tenant-rewrite map, and the `docker/postgres/init.sql` multi-database
  bootstrap.
- 🔧 **Root URL is the bank's site.** Removed the `/[tenant]/` dynamic route
  and the lender-picker landing page. `/`, `/about`, `/services`, `/apply`,
  `/contact` are now real top-level routes serving Growth Finance directly —
  no path prefix, no client-side redirect.
- 🔧 **Staff portal rebranded.** Login screen no longer says "multi-tenant,"
  no longer lists other institutions' demo accounts, and no longer links
  back to a picker that no longer exists.
- 🔧 **Visual redesign for an institutional audience.** Replaced emoji
  iconography, pill-shaped buttons, and animated gradient blobs with a
  more restrained, editorial layout — trust badges, a dark utility bar,
  straight-edged cards — closer to what a bank's compliance/brand team
  would expect to sign off on. Loan calculator and application flow are
  functionally unchanged.
- 🔧 **JWT secret now fails closed.** Previously, if `JWT_SECRET` was unset,
  the app silently signed tokens with a fallback string hardcoded in this
  repo's source. That fallback is removed; the app now refuses to start
  without a real secret. (Docker Compose already enforced this; the raw
  `mvn spring-boot:run` path didn't.)
- 🔧 **Clickjacking protection restored.** `X-Frame-Options` was globally
  disabled (apparently to support the dev-only H2 console). Changed to
  `SAMEORIGIN`, which still allows the H2 console in the `dev` profile but
  no longer strips frame protection from every real page.
- 🔧 **Postgres no longer bound to a public interface.** `docker-compose.yml`
  now publishes port 5432 on `127.0.0.1` only, not `0.0.0.0`.
- 🔧 **Swagger/OpenAPI and actuator internals restricted** to private IP
  ranges at the nginx layer (previously Swagger was reachable from the
  public internet).

None of this is a substitute for the review below — it's the part that was
reasonable to just fix.

---

## 2. Already solid (found, not changed)

- ✅ PII field-level encryption at rest for national ID, phone, address,
  spouse info, bank account (`APP_ENCRYPTION_KEY`), with a separate
  lookup-hash key (`APP_INDEX_KEY`) so encrypted fields stay searchable —
  see `V11__pii_encryption.sql`.
- ✅ Append-only audit hash chain (`V12__audit_hash_chain.sql`) — tamper-evident
  audit trail, not just an audit table.
- ✅ Double-entry general ledger (`V14__general_ledger.sql`).
- ✅ BCrypt password hashing at cost factor 12.
- ✅ Stateless JWT auth, MFA support, rate-limited `/api/auth/**` at nginx.
- ✅ Security headers (HSTS, X-Content-Type-Options, Referrer-Policy) at
  the nginx layer; TLS 1.2/1.3 only, modern cipher list.
- ✅ Flyway-managed schema history — every change is a reviewable, ordered
  migration.
- ✅ Webhook signature verification for the payments provider.

---

## 3. Gaps you need to close before real customer data or funds

These are not code-review nitpicks — each is something a bank regulator,
auditor, or your own risk team will ask about directly.

### Identity, access, and compliance
- 🔧 **KYC/AML provider is simulated — UI now says so; the underlying gap is unchanged.**
  `app.credit-bureau.provider` still defaults to `INTERNAL_SIMULATED`. What changed: this used
  to display identically to a real bureau score, with no visible warning — an officer had no
  way to tell an estimate from a verified report. Now both the borrower page and the credit
  check action clearly flag a simulated result. The actual gap — a real licensed provider
  relationship (see `KYC_AML_STATUS.md` for what that requires and how to wire it in once you
  have one) and a separate sanctions/PEP screening step — is a business/compliance process, not
  something this pass could close.
- ⚠️ **No formal KYC/AML program on file.** The database can *store* KYC
  fields; it does not implement a risk-based customer due diligence
  policy, suspicious-activity reporting, or a designated compliance
  officer workflow. That's a policy + process gap, not a code gap.
- 📋 **Regulatory licensing.** Confirm Growth Finance's license class with
  the National Bank of Rwanda (or applicable regulator) actually
  authorizes the products this platform offers, and that the platform's
  data handling meets that license's conditions.
- 🔧 **Demo credentials shipped in the seed data — fixed this pass.**
  `admin@growthfinance.rw` / `Admin@1234` (and the officer account) used to be created by
  `DataSeeder.java` on every fresh boot with no guard. Now gated behind `BOOTSTRAP_ADMIN_EMAIL` —
  set it (and `BOOTSTRAP_ADMIN_PASSWORD`) before going live and no demo data is created at all.
  Still on you: actually set those variables in your real deployment's environment.

### Security posture
- ⚠️ **No independent penetration test or security audit — still true, still the big one.**
  A scoped self-review this pass (`SECURITY_SELF_REVIEW.md`) checked a handful of specific
  files (file-download IDOR, payment org-scoping, CORS config) and found no issues in what it
  checked — that document is explicit that this is not a substitute for a real assessment.
  Budget for a third-party pen test before launch and at a recurring cadence after.
- 🔧 **Secrets management — documented, not solved; that requires infra you haven't chosen yet.**
  `SECRETS_AND_KEY_ROTATION.md` lays out exactly how this app plugs into Docker/K8s secrets, AWS
  Secrets Manager, or Vault (it needs zero code changes — it already just reads env vars). `.env`
  on a single server is still what's actually running; picking and provisioning a real secrets
  manager is an infrastructure decision for you to make.
- 🔧 **Key rotation — now documented per-secret, including the one that isn't a config change.**
  `SECRETS_AND_KEY_ROTATION.md` spells out that `JWT_SECRET` rotation is a drop-in restart, but
  `APP_ENCRYPTION_KEY`/`APP_INDEX_KEY` rotation requires a full re-encryption migration (no
  key-versioning exists in `CryptoConverter` yet) — that migration tool doesn't exist yet and
  needs to be built and tested against a copy of prod data before either key is ever rotated.
- 🔧 **Dependency and container scanning — added this pass.** `.github/dependabot.yml` (Maven,
  npm, Docker base images, GitHub Actions themselves) plus `.github/workflows/security-scan.yml`
  (OWASP Dependency-Check, `npm audit`, Trivy container scans) now run on every push/PR and
  weekly on a schedule. This needs to actually run once pushed to see real results — the first
  run may need tuning (see the workflow's comments on `continue-on-error`).
- 🔧 **Rate limiting — turned out to already be better than this line suggested.** Re-checking
  while working the list below: `AuthController` already implements per-account lockout after
  repeated failed logins (independent of nginx's per-IP limit on `/api/auth/`), so credential
  stuffing does hit an account-level wall, not just an IP-level one. What genuinely was missing
  and got added this pass: the public, unauthenticated, DB-writing endpoints (loan application,
  contact form) were sharing the generous general `/api/` limit (60r/m) — they now have their
  own tighter zone (`public_write_limit`, 6r/m) in `docker/nginx/conf.d/loansaas.conf`.
- 📋 **Incident response plan.** Who gets paged, who talks to the
  regulator, who talks to affected customers, and within what legally
  required timeframe — this needs to exist and be rehearsed before you
  need it.

### Availability and data durability
- 🔧 **Single points of failure — one genuine bug fixed, the rest is still an infra decision.**
  Found and fixed a real correctness bug: `ScheduledJobs` had no cross-instance coordination, so
  running more than one backend instance would have fired every cron job (overdue checks,
  reminders, EOD accrual) on every instance simultaneously — duplicate borrower SMS/emails, and
  a race condition that could double-post interest. `SchedulerLockService` (see
  `V25__scheduler_locks.sql`) fixes that, so scaling the backend out is now actually safe to do.
  Everything else — one Postgres container, one host, no replica, no load balancer — is
  unchanged and is a provisioning decision; see `HIGH_AVAILABILITY.md` for the honest picture
  and a reasonable order to address it in.
- 🔧 **Disaster recovery — backups now encrypted + shippable off-site; still needs a real test.**
  `deploy/scripts/backup.sh` now encrypts dumps (`BACKUP_ENCRYPTION_KEY`) and has an off-site
  shipping hook (`BACKUP_REMOTE_CMD`) instead of just sitting unencrypted on the same host as the
  database — `restore.sh` updated to match. `DISASTER_RECOVERY.md` has the actual test procedure
  and a log table to record it in. **I cannot run that test for you** — it needs your real
  infrastructure. Please actually run it; the log table currently has zero entries, which is
  the same as having no backup at all.
- 📋 **Data residency.** Confirm where this server actually lives and
  whether that satisfies any data-residency requirement attached to
  Growth Finance's license.

### Observability
- 🔧 **Centralized logging — logs are now ready to centralize; nothing ships them anywhere yet.**
  Added `logback-spring.xml` (structured JSON output in every non-dev profile) and
  `RequestIdFilter` (every request gets a correlation ID attached to every log line it produces).
  See `OBSERVABILITY.md` — the app's side of this is done; actually shipping these logs to
  Loki/ELK/CloudWatch/Datadog and writing alert rules on top is still an infra step for you.
- ⚠️ **No uptime/synthetic monitoring configured.** `health-check.sh`
  exists for manual/cron use; there's still no external monitoring pointed at
  `/actuator/health`. `OBSERVABILITY.md` has specific free-tier options (UptimeRobot, Better
  Stack) — genuinely about 15 minutes of setup once you have a real domain live.

### Payments
- ⚠️ **Flutterwave is the only payment rail wired up**, and its keys are
  currently unset placeholders. Before go-live: confirm Flutterwave (or
  whichever provider you settle on) is itself appropriately licensed/
  regulated for the disbursement and collection flows Growth Finance needs,
  and pressure-test the webhook retry/reconciliation path.

### Product/legal content
- 🔧 **Terms/privacy wiring fixed this pass; content itself still needs a lawyer.**
  `/terms` and `/privacy` now exist and are actually linked from the apply form and site footer,
  and the backend rejects an application that doesn't accept them, recording `terms_accepted_at`
  as evidence. The *text* on both pages is a draft — clearly marked as such — and still needs a
  lawyer licensed in your jurisdiction to review before real applications are collected. A proper
  loan agreement template (the document a borrower signs once approved, distinct from these two)
  still doesn't exist and needs the same legal review.
- 📋 **Interest rate and fee disclosure compliance.** Confirm the
  calculator and product pages disclose APR/total cost of credit the way
  your regulator requires, not just "from X% p.a."

---

## 4. Suggested order of operations

1. Rotate every secret in `.env` to freshly generated values; move them to
   a real secrets manager.
2. Disable/guard the demo data seeder; force-reset any credentials that
   did get seeded.
3. Replace the simulated credit bureau integration with a real, licensed
   provider (or explicitly decide to launch without one and document that
   risk acceptance).
4. Commission a third-party penetration test; fix findings.
5. Stand up centralized logging/alerting and a tested backup+restore
   drill before the first real customer application.
6. Get legal sign-off on terms/privacy/loan agreement content before it's
   linked into `/apply`.
7. Only then: point DNS at production, run `ssl-init.sh`, go live.

This list will keep growing as the platform grows — treat it as a living
document, not a one-time gate.
