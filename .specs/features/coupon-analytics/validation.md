# Coupon analytics — independent validation

Date: 2026-09-13. Verdict: **PASS**. Spec: `.specs/features/coupon-analytics/spec.md`. Diff: `91360a14..3b0d281f`. Verifier: independent agent; not the implementation author. Product source and tests were reviewed read-only. Mutation runs used an isolated backend copy at `/tmp/coupon-analytics-sensor.YTHvfe`; no real-worktree mutation, stash, or product commit.

## Task completion

| Task | Status | Evidence |
|---|---|---|
| T1: aggregation and administrative endpoint | PASS | `f0ec5c0b`, backend gate, AC1–7 below |
| T2: administrative dashboard | PASS | `fd6412fc`, Node gate, AC8 below |
| T3: visual checks and documentation | PASS | `3b0d281f`, browser log/script and versioned screenshots inspected |
| Independent verification | PASS | This report and six killed behavioral mutants |

Integration/publication remains the orchestrator's step and is not represented as already completed here.

## Spec-anchored acceptance evidence

In the table, **B** means `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/AdminCouponAnalyticsIntegrationTest.kt`, **A** means `adm-web/tests/coupon-analytics.test.cjs`, and **J** means `backend/features/subscriptions/src/main/kotlin/br/com/saqz/subscriptions/adapter/output/jdbc/JdbcAdminCouponAnalytics.kt`. Every AC is anchored to explicit outcome assertions; structural constraints are additionally verified from production code.

| AC | Independently derived expected outcome | File:line and assertion expression | Result |
|---|---|---|---|
| AC1 | Both coupon types, including unused, disabled, expired and exhausted; identical codes remain separate identities; expose benefit/campaign/status | B:85 `assertEquals(2,r["coupons"].size())`; B:87 distinct trial/discount UUID assertions; B:88 both codes `ARENA`; B:89 campaign `Quadra A`, trial45 days, discount20%; B:90 `INACTIVE`/`EXPIRED`; B:91 users0 and null conversion; B:143 `EXHAUSTED`. J:38–48 reads the whole catalog with UNION ALL and no active filter. | PASS |
| AC2 | Only actual uses; unique participants and positive confirmed payers; rate rounded to two decimals, null without denominator; ACTIVE alone insufficient | B:91 users0 after selection only; B:103 `assertEquals(3,...users)`, `assertEquals(2,...payingUsers)`, `assertEquals(66.67,...conversionPercent)`; B:134 ACTIVE fixtures produce payingUsers0; B:149 empty summary conversion null. | PASS |
| AC3 | Only processed, owned, positive confirmed receipts at or before snapshot and at/after use; retain first confirmation time, deduplicate invoice, include renewals, reject malformed/missing/invalid/zero/negative receipts | B:97–99 fixture contains prior receipt, duplicate, renewal, unprocessed/overdue/future events; B:104 `assertEquals(2000,...revenueCents)` and payments3. B:117 late duplicate of a prior receipt; B:118–119 invalid/unowned/zero/negative fixtures; B:121 payers0, revenue0, payments0. B:140–145 exact-now textual12.345 gives1235 cents and100%. J:98–114 confirms all predicates, owner+payment key and preservation of previous timestamp. | PASS |
| AC4 | Participating owners count for each used coupon regardless of present subscription code; global people/payments deduplicated; local filters leave global totals fixed | B:102 code `RENAMED`/`INACTIVE` retains metrics; B:112 each of three campaigns reports1 payer/100%/2990 cents; B:113 summary reports1 user/1 payer/1 payment/2990 cents, not triple. A:35 `assert.equal(...analytics.users,3)` after filtering. | PASS |
| AC5 | ends_at equal to now is ended; future trial ongoing; ended-only conversion; null denominator and null discount trial data | B:105 `assertEquals(1,...ongoingTrials)`, ended2 and endedConversion50%; B:144 ended0 and null endedConversion; B:92 discount endedTrials null. J:79–89 verifies inclusive boundary and all three discount trial properties null. | PASS |
| AC6 | Incomplete confirmed evidence is visible without invented revenue/conversion, known zero not pending; partial evidence may coexist with positive receipts; explicit ownership and consistent local read | B:121 invalid receipts give incompleteUsers1, payers0 and revenue0; B:134 three owners (missing receipt, ACTIVE only, known zero) produce incompleteUsers1, payers0 and revenue0; B:145–146 positive1235 cents/payment1 coexists with incompleteUsers1. J:24–26 sets `isReadOnly=true` and `ISOLATION_REPEATABLE_READ`; J:36 encloses every read in `snapshot.execute`; J:99 requires owner; all inputs are JdbcClient SELECTs, no provider dependency. Snapshot transaction settings are static evidence, not a concurrency stress test. | PASS |
| AC7 | GET returns200 for admin,403 ordinary user,401 no session; response excludes private owner/receipt/payload | B:54 `assertEquals(200,response.statusCode(),response.body())`; B:150 exact403/401; B:152 `assertFalse(body.contains(owner.toString()))`, payment ID and `payload`. Controller uses existing `/admin` route/guard; response DTO contains only aggregates and coupon metadata. | PASS |
| AC8 | Cupons panel above forms; global summary, both types, campaign/code search, correct rate/currency/maturity/incomplete data; loading/empty/no-results/error/retry, refresh and session isolation | A:21 one pending request and exact API path; A:22 ready=false while loading; A:24 users3/payers2/`66,67%`/BRL20; A:25–27 both benefits, maturity1/2 and50%, null rate `—`, inactive status and incomplete count; A:31–36 type+UUID filtering/search and noResults; A:40–41 failure hides old totals and retry recovers; A:45 empty vs no-results; A:50 logout clears data/filter/busy and ignores parsed-late response; A:54–56 replacement session starts fresh request. `adm-web/index.html:627` panel precedes existing trial forms; `:629` explicit all-history period; `:660` calculation disclosure. Browser assertions below verify rendered integration. | PASS |

8/8 acceptance criteria matched. No spec-precision gap identified. “After use” includes the identical recorded timestamp, consistently exercised by the explicit snapshot-boundary fixture. Cent rounding uses HALF_UP, and the test asserts the actual1235-cent outcome.

## Gate execution and test integrity

Verifier ran:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) backend/gradlew -p backend :bootstrap:test --tests '*Coupon*' --tests '*Trial*' :features:subscriptions:test
node --test adm-web/tests/*.test.cjs
```

Both exited0. Backend tasks were up-to-date; XML results were independently counted: bootstrap44, subscriptions247, zero failures/errors/skips. Node executed57 tests, all passed, none skipped. Logs: `/tmp/coupon-analytics-verifier-backend.log`, `/tmp/coupon-analytics-verifier-admin.log`. The scratch baseline also compiled and freshly executed all7 new HTTP/PostgreSQL integration tests successfully before mutations.

Total gate results: **348 passed, 0 failed, 0 skipped**. Diff adds7 backend and6 administrative tests; no existing test file/assertion was modified, removed, weakened or skipped. Corresponding prior gate surface is335 tests (bootstrap37 + subscriptions247 + admin51); current348, delta+13. This prior count is derived from unchanged existing tests plus the diff, not claimed as a separate historical execution.

## Discrimination sensor

Expanded financial-data sensor: six valid behavior changes, each independently applied to the original scratch source and restored afterward. Command for baseline and each mutant:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) /tmp/coupon-analytics-sensor.YTHvfe/backend/gradlew -p /tmp/coupon-analytics-sensor.YTHvfe/backend :bootstrap:test --tests '*AdminCouponAnalyticsIntegrationTest'
```

| Mutation | Source location | Observable failing assertion | Result |
|---|---|---|---|
| Use event UUID instead of owner+invoice for deduplication | J:111 | B:104 revenue expected2000, actual3025; B:121 prior receipt falsely creates payer; B:112 expected2990, actual5980 | KILLED |
| Treat PAYMENT_OVERDUE as confirmed | J:99 | B:103 payingUsers expected2, actual3 | KILLED |
| Remove payment-after-use filtering | J:67 | B:104 expected2000, actual3025; B:121 payingUsers expected0, actual1 | KILLED |
| Attribute globally once per campaign touch instead of once per owner | J:67 | B:113 global payments expected1, actual3 | KILLED |
| Exclude discount catalog rows using WHERE false | J:42 | B:85 catalog expected2, actual1; B:111 expected3, actual1 | KILLED |
| Change trial maturity from <= now to < now | J:79 | B:105 ongoingTrials expected1, actual3 | KILLED |

All six compiled and failed on specific behavioral assertions. The discount mutant additionally caused a missing-row access in another test; the explicit catalog-count assertions above are the kill evidence. No syntax/compiler errors were counted as kills. **6 injected,6 killed,0 survived.** Scratch source restored after every case; summary `/tmp/coupon-analytics-sensor.YTHvfe/results.json`, individual `<mutation>.log` files and runner `run.py` preserve reproduction evidence.

## Rendered UI evidence and limits

Inspected the existing Playwright script `/tmp/playwright-test-coupon-analytics.js` and its successful execution log `/tmp/coupon-analytics-browser.log`. It asserts rendered summary revenue, trial maturity, table counts for both types, type filter, campaign search, no results, disabled loading button, error without table, retry/empty and no page errors. Versioned desktop and tablet screenshots were independently opened and visually inspected under `docs/coupons/evidence/`. Desktop shows both coupon types with identical code, global totals, maturity and incomplete-evidence labels. Tablet uses two summary columns and a horizontally scrollable table.

These use local fixtures, not production metrics. No user-run interactive UAT or production deployment is claimed. Manual refresh is wired to the same endpoint; the Node retry and session tests verify a fresh response replaces data, and the browser tests verify the control and loading state.

## Code quality and edge cases

PASS: changes are confined to reporting, admin UI/tests and direct documentation. Existing JDBC/controller/DC runtime patterns are retained; no migration, dependency, payment write, grant modification or mobile source changes. The local application port/DTO and adapter keep transport separate from data gathering without a new framework. All13 added tests map to ACs above. No applicable root/backend/admin AGENTS.md was found; `mobile/AGENTS.md` is outside scope. Skill `coding-principles.md` and strong existing test defaults were applied.

Additional source checks confirm organizer_trials has owner primary key and coupon_redemptions has(coupon_id,user_id) primary key, so per-coupon trial cohort counts match unique organizers. Monetary/status edge cases, same code across types, late duplicate delivery, no-use null denominator, zero-only payment, partial+positive evidence and exactly-now maturity/payment are exercised as listed above.

Metrics intentionally represent **gross recorded receipts before refunds/fees**, **participation attribution rather than exclusive sales attribution**, and **all recorded history at one snapshot**. Missing historic evidence is surfaced when observable; no price-based or owner inference fabricates revenue. These are explicit product definitions in the panel and `docs/coupons/analytics.md`, not defects or claims of net financial reconciliation.

## Ranked findings and traceability

**No blocking, major, minor or cosmetic findings requiring a fix.** AC1–AC8: Verified. There are no surviving mutants, failed/uncovered criteria or SPEC_DEVIATION signals; no lesson was recorded, as required for a clean PASS. No product source or test fixes are requested. The orchestrator can complete the authorized integration step.
