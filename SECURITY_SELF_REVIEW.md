# Security Self-Review (Spot Check) — NOT a Penetration Test

**Read this framing before the findings below, it matters:** this is a handful of targeted
checks against specific files, done by an AI assistant with no ability to actually run the app,
send it live traffic, fuzz its inputs, or think adversarially about it the way a human
pentester or a security scanner would. It found zero problems in what it checked — that is
**not** evidence the app is secure, only evidence these specific files look correct on a manual
read. Things a real pentest covers that this cannot: authentication bypass under load/race
conditions, injection via encoding tricks, business-logic abuse chains across multiple
endpoints, session handling under concurrent use, infrastructure-level misconfiguration, and
anything requiring actually attacking a running instance. **Commission a real one before this
touches real customer money.**

## What was checked, and what was found

| Area | What I looked at | Finding |
|---|---|---|
| IDOR on file access | `BorrowerFileController.download`/`.preview` — can org A read org B's uploaded documents by guessing a file ID? | Properly scoped: `fileService.getByIdForOrg(fileId, user.getOrganization().getId())` rejects a file ID belonging to another org. |
| IDOR on payments | `PaymentController` — schedule/list endpoints | Consistently pass `currentUserUtil.getCurrentOrganizationId()` into the service layer query rather than trusting a client-supplied org ID. |
| CORS | `SecurityConfig.corsSource()` | Explicit allow-list from `app.cors.allowed-origins` config, not a wildcard — correct. |
| Login brute-force | `AuthController.login` | Account lockout after repeated failures already implemented (see `MAX_FAILED_ATTEMPTS`/`LOCKOUT_MINUTES`), on top of nginx's per-IP rate limit on `/api/auth/`. This closes out the "rate limiting is IP-based only" line from the original gap list — it wasn't, on closer look. |
| Multi-tenant data leak | `PublicController` (fixed earlier this engagement) | Found and removed a `/api/public/tenants` endpoint listing every org's data unauthenticated. |
| Hardcoded credentials | `application.properties` (fixed earlier this engagement) | Found and removed a real Gmail account + app password hardcoded as a config default. |

## What was NOT checked (this list is longer than the one above, on purpose)

- SQL injection surface across every repository/native query in the codebase
- File upload content-type/magic-byte validation bypass (only read the validation code, didn't
  attempt to defeat it with a crafted file)
- Authorization on every one of the ~40+ controllers — three were spot-checked, not all
- Session/JWT handling under replay, fixation, or token-theft scenarios
- Rate limiting effectiveness under actual load (config was read, not load-tested)
- Dependency CVEs — that's what the new `security-scan.yml` CI workflow and Dependabot config
  are for; they scan continuously, this document doesn't need to
- Anything about the hosting environment, network segmentation, or physical/cloud infra security
- Business logic abuse — e.g., can a sequence of legitimate-looking API calls produce an
  outcome the app didn't intend (double-disbursement via a race condition, restructuring a loan
  into a negative balance, etc.)

## What to actually do with this

Use this table as a small head start for whoever does the real assessment — it's a few boxes
already ticked, not a report to hand a regulator instead of one.
