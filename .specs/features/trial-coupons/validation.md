# Trial coupons — independent validation

Date: 2026-09-13. Spec: `.specs/features/trial-coupons/spec.md`. Initial diff: `702f3fe7..29e8953e`; final verified functional commit: `208cd905` (round 2). Verifier: independent sub-agent, author ≠ verifier. Product source/tests were read-only; only this report and grounded lessons were written. Mutations ran in an isolated `/tmp` copy.

## Verdict

**PASS — AC1–AC7 verified after round 2 at `208cd905`. G1 and G2 are resolved; no remaining findings or spec-precision gaps. Five behavioral mutants killed out of five. All executed/inspected product gates passed. The round-1 evidence and findings below are retained as history; the final round-2 section records their closure.

## Tasks

| Task | Status | Evidence |
| --- | --- | --- |
| T1 persistence/grant | Verified | Real PostgreSQL integration, transaction rollback, concurrent cap, exact durations |
| T2 HTTP/wiring | Verified | Real HTTP authentication, admin guard, validation and apply→group→OFF flow |
| T3 admin | Verified | 51 Node tests; inspected HTML bindings and server-confirmed state changes |
| T4 mobile | Verified in round 2 | Data, ViewModel, Compose UI and caller wiring verified; G1 initial network retry and G2 contract placement corrected |
| T5 validation/merge | Validation complete; merge pending | Final functional commit verified: 208cd905 |

## Spec-anchored evidence

Paths below use aliases only to keep the assertion table readable:

- **BC** = `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/TrialCampaignIntegrationTest.kt`
- **HTTP** = `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/TrialCampaignEndpointIntegrationTest.kt`
- **OLD** = `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/OrganizerTrialCreationIntegrationTest.kt`
- **DOMAIN** = `backend/features/subscriptions/src/test/kotlin/br/com/saqz/subscriptions/application/StartOrganizerTrialTest.kt`
- **ADMIN** = `adm-web/tests/trial-coupons.test.cjs`
- **VM** = `mobile/features/subscriptions/presentation/src/commonTest/kotlin/br/com/saqz/subscriptions/presentation/trial/TrialEntryViewModelTest.kt`
- **DATA** = `mobile/features/subscriptions/data/src/commonTest/kotlin/br/com/saqz/subscriptions/data/trial/KtorTrialGatewayTest.kt`
- **UI** = `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/TrialEntryScreenshotTest.kt`
- **ENTRY** = `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/di/TrialGroupCreationEntitlementTest.kt`

| Criterion | Exact expected outcome | file:line + assertion expression | Result |
| --- | --- | --- | --- |
| AC1 default and persistence | ON by default, persisted OFF, new trial blocked | BC:56 `assertEquals(TrialOfferMode.ON, campaigns.mode())`; BC:61 `assertEquals(TrialOfferMode.OFF, JdbcTrialCampaignStore(ds, clock).mode())`; BC:62 `assertEquals(CreateGroupResult.GroupLimitExceeded, create(owner()))` | PASS |
| AC1 OFF with selection and history | Selected code cannot override OFF; old trial immutable | BC:107 `assertEquals(CreateGroupResult.GroupLimitExceeded, create(owner))`; BC:63 `assertEquals(granted, trials.find(owner))`; HTTP:122–123 preserve endsAt and 45 days | PASS |
| AC2 create/normalize/list/deactivate | 45 days and optional campaign/cap persisted, uppercase code, duplicate 409, deactivate 204 | HTTP:54–60 `assertEquals(201, created.statusCode())`, code/days/campaign/max assertions, duplicate 409; HTTP:75 `assertEquals(0, listed["uses"].intValue())`; HTTP:76 `assertEquals(204, ...)` | PASS |
| AC2 validation/default | 1–365 integer days; invalid fields 400; unknown ID 404; form defaults 14 | HTTP:99–106 invalid days/fraction/cap/code/past date → 400, absent ID → 404, 1/365 → 201; ADMIN:32 payload exact expiry/campaign/cap; ADMIN:33 `assert.equal(app.state.trialForm.days,'14')` | PASS |
| AC2 discount separation | Existing discount API remains independent | `AdminCouponsEndpointIntegrationTest` executed unchanged in bootstrap gate; HTML uses separate `/admin/trial-coupons` and `/admin/coupons`; new migration does not alter discount tables | PASS |
| AC3 selection timing | No trial or use at selection | BC:71 `assertNull(trials.find(owner))`; BC:72 `assertEquals(0L, campaigns.list().single().uses)`; HTTP:71 `assertTrue(after["startedAt"].isNull)` | PASS |
| AC3 grant/duration/attribution | First group gets exactly configured days × 24h, one use and snapshot | BC:77 `assertEquals(now.plusSeconds(45 * 86400L), trials.find(owner)?.endsAt)`; BC:78 one use; BC:80–82 coupon ID/code/campaign exact; BC:59 public 14×86400; HTTP:119 exact start+45×86400 | PASS |
| AC3 idempotency/eligibility | Retries never renew; group/trial/paid history blocks fresh trial | BC:76 same creation result; BC:85 same endsAt; DOMAIN:32–35 `assertFalse(...isEligible(owner))` for each history; OLD:101–106 deleted/legacy group remains blocked; OLD:118–125 paid history in active/cancelled/event forms | PASS |
| AC4 invalid coupons/boundary | Unknown, expired at exact instant, inactive, full rejected | BC:92/94 `assertEquals(TrialCouponSelection.UNAVAILABLE, select(...))`; BC:96 and :110 group limit exceeded; BC:141 full coupon unavailable | PASS |
| AC4 revalidation/public fallback | OFF/deactivate after selection rechecked; ON invalid code gets public duration without attribution | BC:101–114 switches modes/deactivates after selection; :113 exact 14×86400; :114 zero attributed uses | PASS |
| AC4 cap/concurrency/rollback | At most one successful grant at cap=1; failed group leaves no trial/use | BC:137–140 success=1, denial=1, uses=1, one exact 7-day trial; BC:120 exception, :121 null trial, :122 zero use, :124 successful retry consumes one | PASS |
| AC5 admin/session boundary | Every new admin route 403 regular user / 401 anonymous; application needs session | HTTP:91–95 loops all methods/routes asserting exact 403/401 | PASS |
| AC5 owner/payload and statuses | Code-only payload, current authenticated actor, 400 invalid, 409 OFF/ineligible | DATA:165 `assertEquals("{\"code\":\"ARENA\"}", ...text)`; HTTP:80/103/104 invalid 400; HTTP:90 OFF 409, :124 granted owner 409; controller resolves actor from principal (`OrganizerTrialController.kt:70`) | PASS |
| AC5 read contract | Mode, redemption permission, valid selected code and days returned | HTTP:63–72 INELIGIBLE→AVAILABLE, canRedeem=true, exact selected code and 45 days; :78–79 invalidated selection absent; :86 persisted OFF | PASS |
| AC6 modes/loading/retry/confirmation | All three choices save only after server; duplicate pending calls blocked, retry works | ADMIN:16–19 loaded state/error/retry; ADMIN:24 request count=1 and exact mode body, :25–26 state unchanged on failure | PASS |
| AC6 creation/list/deactivation/logout | Exact payload, preserve draft on failure, uses preserved, late mutation discarded after logout | ADMIN:32 exact payload, :40 no invalid request, :43 draft retained; :47 active before reply, :49 uses=3/active=false after reply; :53 blank draft/null data/busy=false/requests=1 after logout | PASS |
| AC7 public/coupon entry | Public offer needs Continue; coupon-only blocks until selected; 45 days shown | VM:19–24 14 days and OFF recheck blocks; VM:30 `assertFalse(ready)`, :35 45 days, :40 `assertTrue(ready)`; UI:36 no Continue before code, :44 exact 45-day text | PASS |
| AC7 active/subscribed/OFF | Existing authorized users continue; OFF shows no coupon or create button | VM:48–51 ready for Active/Subscribed with permission; UI:57–59 no Code/Continue and explicit unavailable copy | PASS |
| AC7 pending/error inside entry | Duplicate apply blocked, stale response ignored, retry recovers | VM:72 `assertEquals(1,gateway.applies)`; :77–78 OFF persists/ready=false after old reply; :82–87 load error → refresh → ready=true | PASS in entry |
| AC7 initial error through group list | Create action with initial lookup failure must reach retry, without authorizing actual creation | **No passing evidence.** ENTRY:30 currently asserts false on lookup failure; `TrialGroupCreationEntitlement.kt:10` returns false; `GroupListViewModel.kt:50–51` converts it to OpenPlans, before TrialEntryRoot can show retry | GAP in round 1; PASS in round 2 below |
| AC7 duration elsewhere | My Plan must show configured duration, not fixed 14 days | `mobile/features/subscriptions/presentation/src/commonTest/kotlin/br/com/saqz/subscriptions/presentation/ui/myplan/MyPlanTrialScreenTest.kt:54` — `onNodeWithText("Seus 45 dias grátis começam ao criar o primeiro grupo.").assertExists()` | PASS |

The remaining 14-day group-setup string is suppressed in the production route by `SaqzNavHost.kt:600 showTrialOffer = false`; the new entry reads `access.trialDays`. Onboarding reaches `GroupsRoute.Create` directly (same file:353–355), and that route is wrapped in TrialEntryRoot (:594). Applying a code stores the selection server-side, so the existing code-only first-group request correctly consumes it without changing the group request schema.

## Gate evidence

Commands executed independently with JDK 21:

1. `backend/gradlew -p backend :bootstrap:test --tests '*Trial*' --tests '*AdminCoupons*'` — **37 passed**, 0 failures/errors/skips. `/tmp/trial-verifier-backend.log`.
2. `backend/gradlew -p backend :features:subscriptions:test :features:groups:test` — **247 + 645 passed**, 0 failures/errors/skips. `/tmp/trial-verifier-domain.log`. An initial command omitted the `:features` project prefix and failed at task resolution; corrected command passed, no product test failure.
3. `node --test adm-web/tests/*.test.cjs` — **51 passed**, 0 failures/skips. `/tmp/trial-verifier-admin.log`.

Existing mobile gate results independently inspected rather than rerun: `/tmp/trial-mobile.log` and `/tmp/trial-mobile-visual.log` both end BUILD SUCCESSFUL. XMLs confirm subscriptions data **38**, presentation **51**, compose-app **141**, Android trial screenshot **1** test, all 0 failures/errors/skips. Logs cover Android compileDevDebugKotlin and detektAll. Eight screenshot states exist in `mobile/build/reports/trial-entry`; coupon-45-days screenshot was independently opened and inspected, including exact duration, applied code and continue/refresh controls. UI assertions inspect remaining states.

**Aggregate executed/inspected product tests: 1,211 passed; 0 failed, 0 skipped.** Backend groups/subscriptions test tasks were up-to-date with their passing XML results; bootstrap and Node gates executed in this verification.

Test integrity: diff adds tests only (no deletions or weakened assertions). Before/after within the same selected surface, derived from additive test methods: bootstrap 27→37, admin 45→51, data 36→38, presentation 44→51, compose-app 140→141; groups 645 and subscriptions 247 unchanged; targeted screenshot 0→1. This is a source-derived baseline comparison, not a separate execution of the old commit.

## Discrimination sensor

Scratch: `/tmp/trial-sensor.OjCyf1/backend`, copied via rsync excluding every `build` and `.gradle` directory. No real worktree/stash mutation. Each mutation individually restored before the next; source restored at completion. Each run executes six real PostgreSQL `TrialCampaignIntegrationTest` tests. Script/results: `/tmp/trial-sensor.OjCyf1/run_sensor.py`, `results.json`. All runs compiled and failed at behavioral assertions, not syntax/infrastructure.

| Mutant | Source and fault | Observed failing assertion | Result |
| --- | --- | --- | --- |
| M1 ignore OFF | `JdbcTrialCampaignStore.kt:67`, replace OFF guard with `if (false)` | BC:62/:107 expected GroupLimitExceeded, actual Success | KILLED |
| M2 wrong days | `JdbcTrialCampaignStore.kt:70`, always `start(14)` | BC:77 expected 2026-10-28T12:00Z, actual 2026-09-27T12:00Z; BC:140 expected one 7-day grant, got zero | KILLED |
| M3 ignore cap | `TrialCampaigns.kt:18`, remove maxUses check | BC:137 expected one success, actual two | KILLED |
| M4 exact expiration accepted | `TrialCampaigns.kt:18`, `<` to `<=` | BC:94 expected UNAVAILABLE, actual APPLIED | KILLED |
| M5 no attribution | `JdbcTrialCampaignStore.kt:71`, disable attribution update with `&& false` | BC:78 expected uses=1, actual zero; BC:137 expected one success, actual two | KILLED |

Depth: five targeted behavioral mutations for entitlement/data-integrity path. **5 killed, 0 survived.** Logs are named `<mutation>.log` beside results.json.

## Code quality and edges

- Scope, schema and HTTP changes are surgical; no unrelated financial code modified.
- Transaction runs under owner lock; coupon row is locked and uses are read in a subsequent SQL statement, observing prior committed grant. Offer mode shared lock prevents a racing administrative OFF update from splitting a grant.
- Selection does not reserve use. Attribution drives use counts. Rollback and historical snapshots have empirical coverage.
- Session generation guards exist for administrative loads/writes; pending mutations cannot repopulate logged-out UI.
- Product code remains commonMain and obeys presentation/domain/data boundaries, typed gateway errors, Koin and design tokens (`mobile/AGENTS.md`). One new file-organization convention violation remains (G2).
- All newly added tests map to AC1–AC7: six backend rule tests, four HTTP tests, six admin tests, two transport tests, six entry ViewModel tests, one entitlement test, one My Plan UI test and one screenshot test. No unclaimed tests.
- Layer coverage is strong for the grant path and new routes; the production entry before TrialEntryRoot lacks a success assertion for network recovery (G1).
- No interactive human UAT was requested by this independent verifier. Automated UI assertions and saved screenshots are evidence, not a claim of human approval.

## Round-1 ranked fix plans — resolved in round 2

### G1 — Major — AC7 initial lookup error opens plans instead of retry

**Root cause:** `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/TrialGroupCreationEntitlement.kt:10` collapses lookup failure to false. The existing consumer in `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/list/GroupListViewModel.kt:50–51` treats false as OpenPlans. This retained branch sits directly in the feature's modified entry adapter and prevents the requested retry behavior through the group-list path. The new entry's internal retry test cannot detect it because it bypasses the caller.

**What/where:** Route lookup failures to the new guarded trial entry or propagate a typed retry outcome to the list. Preserve blocking of the actual group form until the authoritative trial lookup allows it. Add a regression through the entry decision and TrialEntry ViewModel, including failure→retry, with no group operation before success.

**Verify/done:** First-group action from group list with initial connectivity failure reaches a retry state; it does not imply a paid plan is required, does not open group form, and refresh after recovery applies COUPON_ONLY/OFF rules normally. Re-run compose-app and subscriptions presentation tests, Android compilation, detekt.

### G2 — Minor — new State/Intent declarations violate mobile contract placement

**Root cause:** `mobile/features/subscriptions/presentation/src/commonMain/kotlin/br/com/saqz/subscriptions/presentation/trial/TrialEntryViewModel.kt:12–27` declares failure/state/intent next to ViewModel. `mobile/AGENTS.md:143–145` requires State/Intent/Effect in a single `<Tela>Contract.kt`.

**What/where:** Move the related declarations unchanged into `TrialEntryContract.kt` in the same package. No new abstraction needed.

**Verify/done:** Presentation compilation and its existing behavior tests pass; state/intent declarations are in the mandated contract file.

## Round-1 traceability (historical)

AC1–AC6 → Verified. AC7 → Needs fix G1. T4 quality → Needs fix G2. This was verification round 1; both findings are closed below. No source/spec/task status edits were performed by the verifier.


## Round 2 — final PASS at 208cd905

Independent re-verification inspected the complete correction diff `29e8953e..208cd905`, its immediate consumers and the new test assertions. Product source remained read-only. No backend/admin code changed in this correction range, so the original passing gates and five mutation results remain applicable without redundant execution.

### G1 closed — initial network failure can reach the guarded retry entry

`mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/TrialGroupCreationEntitlement.kt:15–17` now distinguishes entry navigation through `canOpenCreationFlow()`: lookup failure permits opening the entry. Its existing `canCreateGroup()` still returns false on failure (:11). Successful lookups use the same status/coupon eligibility checks (:20–23), so known denial remains denied.

`mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/list/GroupListViewModel.kt:50` now consumes `canOpenCreationFlow()`. The destination remains wrapped by TrialEntryRoot, which only exposes GroupSetup when its ViewModel reaches ready. The initial error therefore reaches a load-error/retry state, without granting access to the group form.

| Required outcome | file:line + exact assertion | Result |
| --- | --- | --- |
| Failure permits entry but not actual creation permission | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/di/TrialGroupCreationEntitlementTest.kt:51` — `assertTrue(entitlement.canOpenCreationFlow())`; :52 — `assertFalse(entitlement.canCreateGroup())` | PASS |
| Known denied state remains denied, coupon eligibility reaches entry | Same file:59 — `assertFalse(entitlement.canOpenCreationFlow())`; :61 — `assertTrue(entitlement.canOpenCreationFlow())` after canRedeemCoupon=true | PASS |
| Real adapter and real entry ViewModel compose correctly on initial error | Same file:70 — `assertTrue(TrialGroupCreationEntitlement(gateway).canOpenCreationFlow())`; :72 — `assertEquals(TrialEntryFailure.Load, entry.state.value.failure)`; :73 — `assertFalse(entry.state.value.ready)` | PASS |
| Retry to COUPON_ONLY shows redemption and still blocks creation | Same file:78 — `assertTrue(entry.state.value.access?.canRedeemCoupon == true)`; :79/:80 — `assertFalse(canContinue)` / `assertFalse(ready)` | PASS |
| Retry to OFF preserves denial | Same file:83 — `assertEquals("OFF", entry.state.value.access?.offerMode)`; :84/:85 — `assertFalse(canContinue)` / `assertFalse(ready)` | PASS |
| Group-list caller uses the entry decision | `mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/list/GroupListViewModelTest.kt:154` — `assertEquals(GroupListEffect.OpenCreateGroup, viewModel.effects.first())`, with canCreateGroup=false and canOpenCreationFlow=true fixture (:147–148) | PASS |

The fake reports a TrialError failure and drives the common SaqzResult.Failure branch; transport-specific failure type does not alter navigation. Existing duplicate-click and authoritative denial tests remain unchanged and passing.

### G2 closed — contract placement follows AGENTS

`mobile/features/subscriptions/presentation/src/commonMain/kotlin/br/com/saqz/subscriptions/presentation/trial/TrialEntryContract.kt:5–20` now owns failure/state/intent declarations. The move preserves every field, default and canContinue condition; the ViewModel imports no redundant TrialAccess. This satisfies `mobile/AGENTS.md:143–145` without changing behavior.

### Round-2 gates and integrity

Inspected `/tmp/trial-routing-fix.log` (BUILD SUCCESSFUL, 1m26s) and `/tmp/trial-routing-confirm.log` (BUILD SUCCESSFUL, 8s), plus the exact JUnit XMLs. The confirmation includes the newly added composition test. Gate covers groups/subscriptions presentation and compose-app `iosSimulatorArm64Test`, Android compileDevDebugKotlin and detektAll.

| Suite | Passed | Failed/errors/skipped |
| --- | --- | --- |
| groups presentation iOS | 556 | 0/0/0 |
| subscriptions presentation iOS | 51 | 0/0/0 |
| compose-app iOS | 144 | 0/0/0 |

The entitlement XML contains all six named tests, including `initialFailureReachesGuardedEntryAndRetryHonorsCouponOnlyAndOff`; the group-list XML contains ten tests including the caller regression. Corrections add three compose-app tests and one group-list test; no test/assertion removal or weakening. All four map to G1/AC7. The contract move requires no new behavior test.

The distinct latest gate surface across both rounds is **1,770 passed**, 0 failures/errors/skips: prior 1,211, plus 556 newly inspected groups-presentation tests and +3 compose-app tests. This count does not double-count repeated suites. Product gates for this fix were run by the implementer and their logs/XML inspected independently by the verifier.

**Final traceability:** AC1–AC7 Verified; G1 Closed; G2 Closed; T1–T4 Verified. **Sensor: 5/5 killed; 0 survived. Overall: PASS, ready for integration.** No additional findings after reviewing the correction and surrounding code. No new lessons are required for this clean re-verification; the grounded round-1 lesson remains recorded. Publication/merge is outside the verifier's actions and is not claimed by this report.
