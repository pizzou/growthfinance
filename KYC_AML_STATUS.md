# KYC/AML Provider — Current State and What's Needed to Go Live

## What exists today

`CreditBureauService` is written against a generic provider contract (base URL + API key,
see `app.credit-bureau.*` properties) specifically so a real bureau can be plugged in via
config — no code change needed for the common case. Until real credentials are set, it
deterministically **simulates** a plausible bureau response from the borrower's own on-file
data, and marks every simulated result with `"simulated": true` in the stored raw response.

Fixed this pass: that simulated flag existed in the data but wasn't visible anywhere an officer
would actually see it — the credit score just showed as a plain number, identical in appearance
to a real bureau pull. An officer relying on it to make a lending decision had no way to tell
the difference. Now: the borrower detail page marks a simulated score with
"⚠️ estimate — no live bureau", and running a check from the loan page shows an explicit warning
instead of a plain success message when the result is simulated.

## What's still missing, and why it's not a code fix

**There is no actual contract with a real KYC/AML/credit-bureau provider.** For Rwanda
specifically, the code comments already point at TransUnion Rwanda as the BNR-licensed credit
reference bureau — but establishing that relationship (or with a regional KYC provider like
Smile Identity, which is common across African fintechs for document/liveness verification)
is a business development and compliance process: applying for access, agreeing commercial
terms, going through their own vetting of your institution, and getting API credentials. That
can't be done from a coding session — someone at the organization needs to start that
relationship.

## Once you have real credentials

1. Set `app.credit-bureau.enabled=true`, `app.credit-bureau.provider=<name>`,
   `app.credit-bureau.base-url`, `app.credit-bureau.api-key` (as real environment variables —
   see `SECRETS_AND_KEY_ROTATION.md`, not hardcoded).
2. Check `CreditBureauService.tryLiveProvider()` — it's written against a generic
   `{nationalId, firstName, lastName}` request and a generic response shape
   (`creditScore`, `riskGrade`, `activeFacilities`, etc.). Real providers won't match this shape
   exactly; this method will need updating to match whatever your actual provider's API
   contract looks like once you have their docs.
3. There's already a graceful-fallback path (`tryLiveProvider` catches failures and falls back
   to the simulation, tagging the result with the failure reason) — keep that behavior so a
   provider outage degrades to a clearly-marked estimate rather than blocking loan processing
   entirely, which is a reasonable product decision but worth your own sign-off given it means
   a temporary bureau outage could mean loans get decided on an estimate.
4. Beyond credit scoring specifically: a real KYC/AML **program** (not just an API integration)
   typically also needs a documented policy for what checks are required at what loan size, a
   sanctions/PEP list screening step (separate from credit history), and a process for what
   happens on a positive match — none of that exists in this codebase yet and isn't purely a
   coding task; it needs your compliance function to define the policy first.
