# LoanSaaS Pro — Production Hardening Report

## Applied in this build

### Financial precision
- Added PostgreSQL migration `V48__financial_precision_and_payment_idempotency.sql`.
- Converted core loan, payment, schedule, accounting, expense, collection, collateral, guarantor, restructuring, bulk-disbursement, public-application, credit-bureau monetary fields, and FX rates from `DOUBLE PRECISION` to exact PostgreSQL `NUMERIC` types.
- Added `FinancialCalculationService` as a single BigDecimal-based calculation boundary for daily interest, penalty, and payment allocation.
- Upgraded `MoneyMath` so financial calculations normalize through `BigDecimal` and `HALF_UP` monetary rounding.
- Updated scheduled interest accrual to use the same 30-day monthly/annual conversion model as payment servicing rather than the previous 365-day calculation.

### Payment integrity
- Added `LoanRepository.findByIdForUpdate()` with `PESSIMISTIC_WRITE` locking.
- Payment recording now locks the loan row before reading and updating its balance.
- Added database-enforced tenant-scoped uniqueness for `(organization_id, transaction_id)` when transaction ID is present.
- Duplicate transaction retries with a different amount are rejected instead of being silently accepted.
- Added `@Version` to `Loan` for optimistic concurrency protection on non-payment loan updates.
- Accounting posting is now part of the payment transaction; an accounting failure propagates and rolls back the payment instead of returning a false financial success.

### Deployment hardening
- Corrected the Maven POM namespace/schema declaration.
- Fixed production deployment/backup/restore/health-check scripts to target `loansaas_growthfinance`.
- Fixed backend health checks so deployment scripts test the backend inside its container rather than assuming host port `8080` is published.
- Fixed the database-size query in the production health check.

### Automated coverage added
- Added `FinancialCalculationServiceTest` covering:
  - monthly daily-rate conversion
  - annual daily-rate conversion
  - penalty → interest → principal allocation order
  - interest and penalty monetary rounding

## Verification performed in the build environment

- POM XML parsed successfully.
- Production shell scripts passed `bash -n` syntax validation.
- Source and migration files were inspected after modification.
- The production ZIP was rebuilt from the modified source tree.

## Important verification limitation

The current build environment does not provide Maven or Docker, so a full `mvn test` / `mvn package` and Docker image build could not be executed here. The source was therefore hardened and statically validated, but the final deployment environment must run the normal Maven test/build pipeline and Flyway migration against a backup/staging database before production cutover.

## Migration safety

`V48` changes database column types from floating point to exact numeric types. A production database backup must be taken before applying it. The payment transaction unique index will correctly fail migration if historical duplicate non-null transaction IDs already exist; those duplicates must be reconciled before production migration.
