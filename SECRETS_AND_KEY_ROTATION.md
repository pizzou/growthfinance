# Secrets Management &amp; Key Rotation

## 1. Where secrets live today, and why that's a real gap

Every secret this app needs (`JWT_SECRET`, `APP_ENCRYPTION_KEY`, `APP_INDEX_KEY`, database
password, `MAIL_PASSWORD`, payment-provider keys, `BOOTSTRAP_ADMIN_PASSWORD`) is read from
environment variables, which in `docker-compose.yml` come from a `.env` file sitting on disk
next to it. That means: anyone with filesystem access to the host can read every secret in
plaintext, there's no access log for who read which secret when, and there's no way to grant a
CI pipeline access to *one* secret without also handing it every other one in the same file.

**I can't provision a real secrets manager for you** — that's an infrastructure/vendor decision
(which cloud, which budget, who administers it), not a code change. What I can do is tell you
concretely what "real" looks like and how this app plugs into it, since the app itself needs no
code change to use any of these — it already just reads environment variables:

- **Docker/Swarm secrets**: mount each secret as a file under `/run/secrets/`, and set the
  corresponding env var to *the file's contents* at container start (e.g. in your entrypoint:
  `export JWT_SECRET=$(cat /run/secrets/jwt_secret)` before launching the JAR). Free, no new
  vendor, works with your existing docker-compose.
- **AWS Secrets Manager / Parameter Store**: if you deploy to AWS (ECS/EKS/EC2), have your
  deployment tooling (Terraform, a task definition's `secrets` block, or an entrypoint script
  calling `aws secretsmanager get-secret-value`) inject these as env vars at container start.
  Gives you access logging and IAM-scoped per-secret permissions.
- **HashiCorp Vault**: if you're not on a single cloud, Vault's Agent sidecar can render a
  `.env`-shaped file from Vault secrets at container start, which you then source — same
  "app just reads env vars" contract, but now audited and rotatable centrally.

Whichever you pick, the acceptance test is the same: **`.env` should not exist on any server
that touches real customer data.** It's fine for local dev.

## 2. Key rotation — what each secret needs, honestly

Rotation is not the same operation for every secret here. Some are drop-in; one is not.

| Secret | Rotation impact | Procedure |
|---|---|---|
| `JWT_SECRET` | **Invalidates every existing login session** the moment it changes — everyone gets logged out and has to sign in again. No data migration needed. | Set the new value, restart the backend. Warn staff beforehand; do it off-hours. |
| `APP_ENCRYPTION_KEY` | **Cannot be swapped in place.** Every encrypted column (national ID, phone, address, spouse details — see `CryptoConverter`) was encrypted with the *old* key. Changing the env var without re-encrypting existing rows means every existing encrypted value becomes permanently unreadable garbage. There is currently no key-versioning in `CryptoConverter` (no key ID prefix on ciphertext), so this needs a proper migration, not a config change. | **Do not rotate this without a re-encryption pass**: (1) stand up a maintenance window, (2) write a one-off job that reads every row with an encrypted column using the OLD key, decrypts, re-encrypts with the NEW key, writes back — inside a transaction per table, with a full `pg_dump` backup taken immediately before starting. This tool doesn't exist yet; building and testing it (against a copy of production data, not production itself, first) should happen before this key is ever rotated. |
| `APP_INDEX_KEY` | Same issue as above — it's the HMAC key behind the blind-index columns (`phoneHash` etc.) used to look up a borrower by phone without decrypting every row. Rotating it without rebuilding the index columns breaks "find borrower by phone." | Needs the same re-encryption-style migration: recompute every blind-index column with the new key in the same pass as `APP_ENCRYPTION_KEY` rotation, since they're both derived from borrower PII. |
| Database password | Standard Postgres rotation. | `ALTER USER loansaas WITH PASSWORD '...'` on Postgres, update the secret in your secrets manager, restart the backend so it picks up the new `DATASOURCE_PASSWORD`. |
| `MAIL_PASSWORD` / payment-provider API keys | Drop-in — these are just credentials for an external service. | Rotate at the provider (Gmail App Passwords page, Flutterwave dashboard, etc.), update the secret, restart the backend. |

## 3. Recommended rotation cadence (document, don't just pick a number)

Set an actual cadence appropriate to your regulator's expectations and your risk appetite —
common baselines are 90 days for JWT/DB credentials and annually (or immediately on suspected
compromise) for the encryption keys given how expensive rotating them is. Whatever you pick,
write it down somewhere your compliance officer / auditor can point to — "we rotate on this
schedule" is itself something a pentest/audit will ask for.

## 4. If a secret is ever actually exposed

This happened once already in this repo — see `BANK_READINESS.md`'s changelog for the leaked
`MAIL_PASSWORD`. The response was: revoke/rotate immediately at the provider, don't wait for a
scheduled rotation window. That's the standing procedure for any exposure, for any secret in
this table.
