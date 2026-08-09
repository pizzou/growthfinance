/**
 * Single-tenant deployment.
 *
 * This build is compiled and shipped for exactly one bank. The slug below
 * selects which organization's data this frontend renders — it is NOT a
 * user-facing URL segment (there is no /[tenant]/ path anymore). It must
 * match the SEED_ORG / org slug the backend this frontend talks to is
 * configured for. Change it (and rebuild) if this codebase is ever used to
 * stand up a deployment for a different institution.
 */
export const TENANT_SLUG =
  process.env.NEXT_PUBLIC_TENANT_SLUG || 'growthfinance';
