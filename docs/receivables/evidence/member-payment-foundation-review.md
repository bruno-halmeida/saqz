# Independent review — member payment foundation

Date: 2026-09-13. Initial implementation/test range: `702f3fe7..8c3e939a` (520 additions, 12 files). Reviewer is independent of the author. Scope: paginated original-payer order discovery and KMP member gateway, AC1–AC6 from `docs/receivables/member-payment-foundation.md`; HTTP contract from `docs/receivables/payment-http-contract.md`.

**Final verdict: PASS for `702f3fe7..97855e20`, 6/6 ACs verified.** Production is unchanged from `8c3e939a`. The author added three tests and clarified instrument-ID uniqueness in AC6. All ten distinct mutations are now killed; zero surviving mutants or unresolved scoped findings.

Initial verdict on `8c3e939a` was AC6 test gaps with implementation guards present. The original eight mutations produced six behavioral kills and two survivors. Two supplemental checks found account-isolation and duplicate-instrument gaps. The final re-verification below records their closure.

The real implementation and tests were read-only during this review. The only reviewer-written workspace artifact is this report. No stash, commits, pushes, real provider calls, prior provider-session inspection, or changes to the untracked context/direcionamento documents. The mutation copy came from `git archive 8c3e939a backend mobile`, without local properties or credentials.

## Evidence conventions

All line numbers in the initial review refer to `8c3e939a`. These aliases expand to exact repository files:

- **BT**: `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/OneOffPaymentsIntegrationTest.kt`
- **MT**: `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorMemberPaymentsGatewayTest.kt`
- **Gateway**: `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorMemberPaymentsGateway.kt`
- **Transport**: `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/MemberPaymentTransports.kt`
- **Store**: `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentStore.kt`
- **Service**: `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/OneOffPayments.kt`

## Independently derived acceptance evidence

| AC | Spec outcome and exact assertion evidence | Initial outcome |
| --- | --- | --- |
| AC1 | BT:484 `assertEquals(listOf(order.copy(status = "REFUNDED")), page.orders)`; BT:485 `assertNull(page.nextCursor)`; BT:487–488 `assertEquals(PaymentOrderPage(emptyList(), null), ...ownOrders(...actor...))` for owner/unrelated actor; BT:530–537 status 200, `"no-store"`, UUID requestId, exact order/payer IDs and null cursor; BT:542–544 confirms caller-controlled payerId cannot change actor scope. Store:35 always filters `member_user_id=:payer`, with no owner/delegate branch. | PASS |
| AC2 | BT:478–482 disables link/rollout/eligibility, removes membership, soft deletes group; BT:484 retains exact original order as REFUNDED. BT:490–494 asserts customer/payment posts, instruments and payment effects all zero, underlying charge still `"PENDING"`. Service:57–62 and Store:32–47 contain only local order/quote reads: no authorization/commercial gate or provider execution. | PASS; provider GET absence is established by code inspection, not a dedicated request-count assertion. |
| AC3 | BT:507 exact 50-row ordered first page; BT:508 exact cursor; BT:510 exact remaining two IDs; BT:511 null terminal cursor; BT:512 combined unique count 52. BT:513–516 foreign/unknown cursor equals empty page. BT:554–557 proves foreign newer cursor is opaque even with older own rows. BT:538–540 no CPF/Pix payload and malformed cursor status exactly 400. Store:33–36 applies payer isolation to cursor and `(issued_at,id)` strict descending keyset. | PASS |
| AC4 | MT:16–22 GET path/query/bearer and exact `Success(MemberPaymentPage(listOf(order), "order"))`; MT:30 exact empty page. MT:39 exact full detail equality includes all six monetary fields, terms, fingerprint, Pix, checkout, expiration and milestones from MT:146–160. MT:43–46 preserves REFUNDED order/instrument despite historical available/confirmed true. MT:60–61 exact CARD quote/instrument mapping. | PASS |
| AC5 | MT:52–65 verifies POST path/bearer, two byte-identical bodies and JSON equality with only requestId/method/fingerprint/accepted/payer. MT:71–79 verifies retries remain on reconcile path, body only requestId, result UNKNOWN. MT:125–129 checks four transport retries preserve a single exact body/request ID. MT:135–137 returns INVALID for wrong fingerprint, unsupported method and absent acceptance, with engine configured to fail on any request. Gateway:13–36 has no chaining from read/reconcile to instrument. | PASS |
| AC6 | MT:83–91 exact 401/403/404/409/400 typed errors and 503 write UNCERTAIN. MT:95–102 malformed JSON/envelope, wrong response request ID, order/account/method, amount and terms all UNCERTAIN. MT:110–114 rejects invalid reads/reconcile/page cursor. MT:125–129 exact NETWORK reads vs UNCERTAIN writes under timeout/connectivity. However, MT:107 changes the root `order` key as well as its value, so it does not discriminate order-ID validation; changing 1061→1000 breaks arithmetic, so it does not discriminate internally valid instrument quote mismatch. Transport:47 and :50 guards exist but respective mutations survived. | GAP pending test-only fixes |

Reverse mapping: BT tests starting :475 → AC1/2; :497 → AC3; :519 → AC1/3; :547 → AC3. MT tests :14/:25/:33 → AC4; :49/:68 → AC5; :82/:94/:106/:117 → AC6; :133 → AC5/6. The added DI assertion at `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/di/SaqzKoinModulesTest.kt:280` resolves `MemberPaymentsGateway` as `KtorMemberPaymentsGateway`. No test removed or weakened in the original diff. No UI deliverable in this slice; interactive UAT/visual captures do not apply.

## Independent baseline gates

JDK 21 from `/usr/libexec/java_home -v 21`. Test tasks explicitly used `--rerun`; XML timestamps establish fresh test execution, not NO-SOURCE/up-to-date claims. Compilation/lint prerequisites may reuse the existing build cache.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock backend/gradlew -p backend :bootstrap:test --tests '*OneOffPaymentsIntegrationTest' --rerun :architecture-tests:test --rerun

JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:receivables:data:testAndroidHostTest --rerun :features:receivables:data:iosSimulatorArm64Test --rerun :features:receivables:data:detektAll :features:receivables:domain:detektAll :compose-app:compileKotlinIosSimulatorArm64 :android-app:compileDevDebugKotlin :compose-app:testAndroidHostTest --tests '*SaqzKoinModulesTest*' --rerun :compose-app:iosSimulatorArm64Test --tests '*SaqzKoinModulesTest*' --rerun :compose-app:detektAll
```

Both commands exited 0. Logs: `/tmp/saqz-member-review-backend.log` (25s), `/tmp/saqz-member-review-mobile.log` (6s).

| Suite | Before delta | Verified baseline | Failed / errors / skipped |
| --- | ---: | ---: | --- |
| OneOffPaymentsIntegrationTest | 28 | 32 | 0 / 0 / 0 |
| BackendArchitectureTest | 20 | 20 | 0 / 0 / 0 |
| Receivables data Android host | 9 | 19 | 0 / 0 / 0 |
| Receivables data iOS simulator | 9 | 19 | 0 / 0 / 0 |
| SaqzKoinModulesTest Android host | 6 | 6 | 0 / 0 / 0 |
| SaqzKoinModulesTest iOS simulator | 6 | 6 | 0 / 0 / 0 |
| Total | 78 | 102 | 0 / 0 / 0 |

Backend XML timestamps: 18:34:15 UTC; mobile/DI: 18:34:20–21 UTC. Results live under each module's `build/test-results/{test,testAndroidHostTest,iosSimulatorArm64Test}`. Android application and iOS compose compilation and data/domain/compose detekt passed. The 24 execution-count increase comprises four backend tests plus ten common tests executed on each mobile target.

## Financial discrimination sensor

Scratch: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-member-sensor-32r_v47v`. Shared Gradle cache; Android SDK explicitly `/Users/bruno_almeida/Library/Android/sdk`. Each single mutation was restored in `finally` before the next. Source files in the actual workspace were never mutated. Script/log artifacts: `/tmp/saqz-member-sensor.py`, `/tmp/saqz-member-sensor-extra.py`, `/tmp/saqz-member-mutations.json`, `/tmp/saqz-member-mutation-<name>.log`.

Commands in scratch use `--offline :bootstrap:test --tests '*OneOffPaymentsIntegrationTest'` (32 tests per mutation) or `--offline :features:receivables:data:testAndroidHostTest --tests '*KtorMemberPaymentsGatewayTest'` (10 tests per mutation). All kills below are test assertion failures following successful compilation; compile/setup failures are not counted.

| Mutation | Injection | Initial result / discriminating assertion |
| --- | --- | --- |
| payer_filter | Store:35 replace payer predicate with `:payer IS NOT NULL` | KILLED, 3/32 fail; BT:544 expected 0 rows, actual 1; BT:487 and :554 also fail. |
| foreign_cursor | Store:34 remove cursor's `AND member_user_id=:payer` | KILLED, 1/32 fail; BT:556 expected empty page, actual own order. |
| page_boundary | Service:60 `take(50)` → `take(49)` | KILLED, 1/32 fail; BT:507 exact expected 50 IDs differs. |
| request_id | Gateway:49 remove write requestId equality | KILLED, 1/10 fail; MT:102 expected UNCERTAIN, actual Success. |
| instrument_quote | Gateway:28 remove approved-quote membership, retain method check | KILLED, 1/10 fail; MT:102 accepts changed terms-v2 unexpectedly. |
| detail_quote | Transport:50 remove `it.quote in mappedOrder.quotes` | SURVIVED, 10/10 pass. |
| uncertain_transport | Gateway:64 map write timeout/connectivity to INVALID | KILLED, 1/10 fail; MT:126 expected UNCERTAIN, actual INVALID. |
| detail_order | Transport:47 replace orderId equality with nonblank expectedOrderId | SURVIVED, 10/10 pass. |

Original mandatory sensor: **8 mutations, 6 killed, 2 survived**. Focus spans payer and cursor authorization, pagination, response correlation, immutable prices, uncertain writes and order identity.

Supplemental checks in the same mapper, completed before the scope-narrowing instruction:

| Mutation | Initial result | Scope |
| --- | --- | --- |
| detail_account: remove Transport:50 account equality | SURVIVED, 10/10 pass | AC6 cross-resource identity; include in the same test fix. |
| detail_duplicate_id: remove Transport:49 distinct instrument IDs check | SURVIVED, 10/10 pass | Defensive validation; the current contract does not explicitly specify collection uniqueness. Recorded transparently as supplemental evidence, not an added acceptance requirement. |

## Ranked findings and concrete fixes

1. **Major test gap — AC6 order identity is not discriminated.** At MT:107, global replacement renames the root JSON key `order`, producing a schema error. Fix with a structurally valid foreign order ID plus matching foreign instrument.orderId, preserving the root key and internal relationships. Assert INVALID for detail and UNCERTAIN for reconcile. Done when the Transport:47 equality-removal mutant fails behaviorally.
2. **Major test gap — AC6 detail/reconcile snapshot and account identity are not discriminated.** MT:107 only breaks quote arithmetic, and no case changes only the detail instrument's account. Keep orderJson unchanged; provide a valid instrument quote with different terms/method or other valid monetary snapshot and a separate foreign-account instrument. Assert INVALID detail / UNCERTAIN reconcile. Done when quote-membership and account-equality mutants fail behaviorally.
3. **Initial spec-precision/test gap — duplicate-instrument defensive validation had no discriminating test.** The original contract did not explicitly state collection uniqueness. During this review, the author made uniqueness explicit in AC6 and added the exact rejection assertions. Closed by the fourth repeated mutant below.

No production defect found in the scoped delta. Guards already implement the expected behavior. No public checkout callback is interpreted as settlement; milestones are transported independently of status. Domain has no platform/network dependency; gateway uses authenticated shared transport, explicit retry safety and stable serialized command bodies; DI is composition-root only. Changes are confined to the stated feature. Transport mappers are internal DTO member methods, matching adjacent receivables code, although mobile/AGENTS.md describes private extension mappers as the general convention.

Grounded lessons for handoff: (1) malformed fixtures can fail during deserialization or arithmetic before reaching the guard under review; preserve a valid schema and all unrelated invariants, then alter only the semantic relationship being tested. (2) When a defensive invariant lacks a precise acceptance statement, clarify the contract explicitly before treating its test as an acceptance requirement. The instrument-ID uniqueness sentence in AC6 records that precision. These lessons use the documented fallback: no separate lesson bookkeeping was created because the reviewer was authorized to write only this report.

## Re-verification

Final snapshot: `97855e20`. Diff from the first snapshot changes only the gateway test (26 added lines / 3 test methods) and foundation documentation; all production files are byte-identical. The scratch test was copied directly from `git show 97855e20:<MT>`; original production files were retained.

Fresh focused baseline:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME=/Users/bruno_almeida/Library/Android/sdk <scratch>/mobile/gradlew -p <scratch>/mobile --offline :features:receivables:data:testAndroidHostTest --rerun :features:receivables:data:iosSimulatorArm64Test --rerun
```

Exit 0 in 9s, log `/tmp/saqz-member-review-fixed-baseline.log`. Data suites: **22 Android host + 22 iOS simulator**, including 13 member-gateway tests per platform. No failures/errors/skips. No need to rerun unaffected application compilation/backend/DI gates after a test/documentation-only change. After mutations, source restoration was checked against the final commit byte-for-byte for all four mutation targets plus the updated test; a final Android data run confirms the restored source is green, log `/tmp/saqz-member-review-restored-baseline.log`.

| Final AC6 evidence in MT at 97855e20 | Exact discriminating result |
| --- | --- |
| :117–125 — valid quote with changed terms/method, or only foreign instrument account; original orderJson preserved | :122 `assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.detail("order"))`; :123 same assertion with UNCERTAIN for reconcile. |
| :127–134 — order.id and instrument.orderId both foreign, preserving root key and internal relationships | :132 INVALID for detail; :133 UNCERTAIN for reconcile. |
| :136–141 — valid repeated instrument in instruments array | :139 INVALID for detail; :140 UNCERTAIN for reconcile. |

These three tests map to AC6 and the explicitly recorded uniqueness precision. They supplement all original assertions. Original transport retry evidence moves to MT:151–155; command acceptance/fingerprint checks move to :161–163.

| Repeated mutation | Result at final snapshot | Behavioral failure |
| --- | --- | --- |
| detail_quote | KILLED, 13 tests / 1 failure / 0 errors/skips | `detailAndReconcileRejectValidInstrumentSnapshotsThatDifferFromApprovedOrder`: expected INVALID, actual Success with changed valid quote. |
| detail_order | KILLED, 13 tests / 1 failure / 0 errors/skips | `detailAndReconcileRejectAnotherOrderEvenWithInternallyConsistentInstruments`: expected INVALID, actual Success with foreign order. |
| detail_account | KILLED, 13 tests / 1 failure / 0 errors/skips | `detailAndReconcileRejectValidInstrumentSnapshotsThatDifferFromApprovedOrder`: expected INVALID, actual Success with foreign account. |
| detail_duplicate_id | KILLED, 13 tests / 1 failure / 0 errors/skips | `detailAndReconcileRejectDuplicateInstrumentIdentities`: expected INVALID, actual Success with repeated instrument. |

Logs: `/tmp/saqz-member-mutation-reverify_detail_{quote,order,account,duplicate_id}.log`. Compilation succeeded for all four; failures are JUnit assertion failures, not compile failures. The mutation stops each test at its first detail assertion; the subsequent reconcile assertion is exercised by the passing baseline. Both paths use the same mapper.

Final financial sensor: **10 distinct mutations, 10 killed, 0 survivors** (14 total mutation attempts including the four repeats). Six unchanged mutant kills from the initial run remain applicable because neither their production guards nor their existing tests changed. One fix/re-verify iteration used; no further defect exploration after closure.

Final gate count across the scoped backend/architecture/data/DI suites: **108 passing executions** (32 + 20 + 22 + 22 + 6 + 6), zero failures/errors/skips. This count combines unchanged suites from the independent initial gate with fresh changed data suites, not a claim that all 108 were rerun after the test-only commit.

Final traceability: AC1–AC5 remain PASS; AC6 now PASS with precise snapshot/order/account/unique-ID assertions. Ranked findings 1–3 are closed. No unresolved spec-precision gap, skipped required gate, production defect, or user-facing UAT remains within this foundation slice.
