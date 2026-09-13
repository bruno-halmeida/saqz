# Independent review — member payment UI

Date: 2026-09-13. Independent verifier (author ≠ verifier). Initial scope: `0a2cf201..ff870197`, 23 files, 1,149 additions / 3 deletions. Acceptance source: `docs/receivables/member-payment-ui.md`, UI1–UI7. Prior gateway/backend foundation is outside the implementation review; backend reads were used only to establish reachability of a UI recovery bug.

**Final verdict at 06d70c7b: PASS for the scoped automated/source review; full result below. Initial verdict at ff870197: FAIL.** Two independently reproduced production defects, one additional history-effect generation gap, and two surviving financial mutations. The implementer is addressing these findings; final re-verification appears below when complete. This is validation of the mobile UI priority group, not completion of all T16 work, provider sandbox homologation, human UAT, or production authorization.

The real implementation/test tree was read-only to this verifier. The only reviewer-authored workspace file is this report. No stash, git worktree, commits, pushes, real Asaas calls, or edits to untracked context.md/direcionamento.md. All fault injection and regression probes ran in a temporary copy from `git archive ff870197 mobile`; local.properties was copied solely for its SDK location.

## Exact evidence aliases

Line numbers in the initial matrix refer to ff870197. These aliases resolve to full repository file paths:

- **VM**: `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModel.kt`
- **Contract**: `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentContract.kt`
- **History**: `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentHistoryViewModel.kt`
- **Roots**: `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentRoots.kt`
- **Screen**: `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentScreens.kt`
- **VT**: `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt`
- **HT**: `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentHistoryViewModelTest.kt`
- **ST**: `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/MemberPaymentScreenshotTest.kt`
- **IT**: `mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentScreenTest.kt`
- **NT**: `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/MemberPaymentNavigationTest.kt`
- **ET**: `mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/monthlypayments/OwnMonthlyPaymentsEntryTest.kt`

## Independently derived acceptance matrix

| AC | Exact outcome / discriminating assertion | Initial outcome |
| --- | --- | --- |
| UI1 | HT:17 `assertEquals(listOf(paymentOrder), ...orders)`; HT:20 exact IDs `order, second`; HT:21 null terminal cursor; HT:22 exact cursors `[null, order]`; HT:24 empty refresh and `[null, order, null]`; HT:29 exact NETWORK + nonloading; HT:31 null retry error; HT:35 only listed `order` emitted, excluding `foreign`. HT:44 stale response cannot replace refreshed empty history. HT:50–51 exact SIGNED_OUT, empty orders, only one fetch. ET:30–32 asserts CTA displayed/enabled and callback count increments in loading/empty/network-error group states. ST:31–35 captures loading/empty/error/page. | PASS for response/state/entry behavior; queued history navigation generation is a UI6 gap below. |
| UI2 | VT:28 no implicit method; VT:29 zero commands; VT:31 exact terms v1; VT:32 missing payer blocks; VT:34 valid payer allows; VT:35 switching CARD clears acceptance; VT:36 exact CARD quote; VT:38–41 exact payer, method, fingerprint and accepted command. VT:46 missing/mismatched/blank terms produce zero commands; VT:50 rejects short/long/control-character names. ST:47 disabled acceptance on missing terms; ST:49 disabled Pay before acceptance; ST:51 enabled afterward; ST:57 incomplete document disables. Contract:28–29 enforces name and 11/14 ASCII digits. Screen:152–154 shows quote amounts, visually inspected against fixture 10000/658/10658. | GAP: no exact amount assertion; wrong total-to-base mutant survives all four screenshots. |
| UI3 | VT:55 checks marker request/user before gateway invocation; VT:57 pending; VT:59 duplicate Pay keeps one command; VT:60 exact equality of full replay command; VT:64 marker cleared only on known create result; VT:70 restored pending and nonreplay; VT:71 empty personal fields; VT:74 zero creation, reconciliation, pending preserved when no instrument; VT:77 discovered new instrument resolves; VT:102 delayed create accepts only one click; VT:121–124 rejected write requires refresh, refresh creates nothing. Roots:31 and Screen:68 block system/header back while pending (source evidence; no dedicated back-action assertion in initial tests). | FAIL: VM:74 treats any old instrument as evidence for a newer uncertain POST, clears pending/marker, and permits another attempt. Scratch regression fails on pending after refresh of only old CANCELLED. |
| UI4 | VT:135 exact single Pix Copy or hosted Card Open; VT:137 ACTIVE remains ACTIVE and refresh creates zero commands. VT:112 nonactive statuses cannot use instrument/create; VT:116 past expiry sets expired and disables use; VT:143–147 accepts only hosted HTTPS and fails closed on malformed expiry. ST:89 QR exists; ST:92 exact Copy intent; ST:94 copied feedback; ST:96–97 failed image text + enabled copy fallback; ST:100 Card intent; ST:102 open-failure text; ST:80 no copy after deadline; IT:23–24 exact expired text and no copy. | GAP: removal of copy-time fresh Clock check survives 15 VM/history tests. Queued effects can execute after refund/expiry (UI6 production defect). |
| UI5 | ST:67–76 exact labels for ACTIVE, UNKNOWN, CREATING, CONFIRMED, AVAILABLE, REFUNDED, DISPUTED, RECOVERY_PENDING, CANCEL_PENDING, CANCELLED, EXPIRED, FUTURE_STATUS with historical confirmed/available true; all nonactive states have no copy. ST:81–82 order REFUNDED overrides AVAILABLE instrument with exact refund label. VT:113 preserves original instrument status. VT:121–124 definitive failed write clears accepted/pending and refresh never creates; Contract:25–26 permits new review only ISSUED plus all CANCELLED, excludes EXPIRED. | PASS for status precedence; initial rejection test does not explicitly complete a new accepted attempt after a CANCELLED instrument (recovery regression exposes the adjacent defect). |
| UI6 | VT:92 old generation cannot overwrite REFUNDED; VT:94 new session key clears detail and sets SIGNED_OUT; VT:104–105 delayed creation after logout clears name/document/detail/marker. VT:83–84 foreign saved actor clears marker and foreign payer detail yields DENIED; VT:61 saved keys exclude personal fields; VT:71 restored name/document empty. HT:44/50–51 generation/session evidence. NT:21 full serialized backstack equality; NT:23 logout yields exactly Login. Contract:8–9 routes carry only order ID. | FAIL: Roots:34 checks session only on buffered payment effects, allowing stale action after same-session refresh. History has the same unstamped queued-effect gap. UI3 old-instrument issue also clears unresolved marker. |
| UI7 | ST:37/45/55/92/100 exact UI callback intents; ET:30–32 visible enabled entry in loading/empty/error. NT:21/23 exact route restore/logout. `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt:481` maps history→order and :715 maps monthly payments→history; DI resolves both VMs in SaqzKoinModulesTest. Screen uses SaqzTheme metrics/design-system components, resource PT-BR strings, centralized tags and previews. Native files only decode QR images; UI and behavior remain commonMain. | PASS for composed screens, callback wiring, DI and route serialization. Full interactive Profile→payment walk-through and human UAT remain unperformed. |

Reverse test mapping: HT's four methods → UI1/UI6; VT acceptance/terms tests → UI2; uncertainty/restoration/rejection/concurrent create → UI3/UI6; actor/generation/session → UI6; expiry/action/URL/status → UI4/UI5; ST history → UI1/UI7, review → UI2/UI3/UI7, statuses → UI4/UI5, Pix/card → UI4/UI7; IT → UI2/UI4/UI5/UI7; NT → UI6/UI7; ET → UI1/UI7. No test deleted, skipped, or weakened in the scoped initial diff.

## Fresh initial gates and integrity

JDK21 via `/usr/libexec/java_home -v 21`. Test tasks explicitly used `--rerun`; passing results are executed suites, not NO-SOURCE tasks. Initial presentation gate completed before fixes: 38 Android + 40 iOS, zero failures/errors/skips, XML timestamps 19:17:23/19:17:39 UTC. Presentation before feature: 23 Android + 24 iOS; delta is 15 common methods on each platform plus one iOS screen method (31 new executions).

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:receivables:presentation:testAndroidHostTest --rerun :features:receivables:presentation:iosSimulatorArm64Test --rerun :features:receivables:presentation:detektAll

JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :compose-app:testAndroidHostTest --tests '*SaqzKoinModulesTest*' --rerun :compose-app:iosSimulatorArm64Test --tests '*SaqzKoinModulesTest*' --tests '*MemberPaymentNavigationTest*' --rerun :android-app:testDevDebugUnitTest --tests '*MemberPaymentScreenshotTest*' --rerun :features:groups:presentation:testAndroidHostTest --tests '*OwnMonthlyPaymentsEntryTest*' --rerun :android-app:compileDevDebugKotlin :compose-app:compileKotlinIosSimulatorArm64 :compose-app:detektAll :features:groups:presentation:detektAll
```

Both exit 0. Logs: `/tmp/saqz-member-ui-review-baseline.log` and `/tmp/saqz-member-ui-review-integration.log`. DI 6 Android + 6 iOS; navigation 1 iOS; screenshots 4 Android; entry 1 Android, zero failures/errors/skips. Integration XML timestamps 19:19:42–19:20:43 UTC. Total initial scoped execution evidence: 96. The integration command overlapped author recovery changes, so its compilation result is supporting workspace evidence, not a claim of immutable ff870197-only compilation. Final fixed snapshot will receive independent bounded verification. Broader general Android suite was not rerun; prior two unrelated baseline failures are not concealed as scoped successes.

Visual inspection opened `mobile/build/reports/member-payment-ui/{revisao-aceita,pix-qr,reembolso-prevalece,tentativa-incerta,cartao-hospedado}.png`. Exact base R$100.00, fee R$6.58, total R$106.58 (rendered PT-BR), terms version and acceptance are readable; QR, expiry, hosted-card guidance, refund label and recovery action are visible. Captures are fixture UI, not live provider payments, and are recorded images rather than golden-image comparisons.

## Financial discrimination sensor

Scratch: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-member-ui-review-i7q4s3ty`. Each single mutation restored in `finally`; source in the real tree was never mutated. Scripts `/tmp/saqz-member-ui-sensor.py`, `/tmp/saqz-member-ui-extra.py`; machine results `/tmp/saqz-member-ui-mutations.json`; per-mutant logs `/tmp/saqz-member-ui-mutation-<name>.log`.

VM command: scratch `mobile/gradlew -p mobile --offline :features:receivables:presentation:testAndroidHostTest --tests '*MemberPayment*ViewModelTest'` (15 tests). UI command: scratch `:android-app:testDevDebugUnitTest --tests '*MemberPaymentScreenshotTest'` (4 tests). All nine compile successfully; kills are behavioral assertion failures.

| Mutation | Target | Result / exact discrimination |
| --- | --- | --- |
| acceptance | Contract:28 remove `accepted` from canPay | KILLED 1/4; ST:49 expected disabled Pay, actual enabled. |
| replay_request | VM:100 replace reused command requestId with new UUID | KILLED 1/15; VT:60 full command equality detects different request IDs. |
| restored_uncertainty | VM:74 clearPending unconditionally on read success | KILLED 1/15; VT:70 restored pending unexpectedly false. |
| generation | VM:155 replace expected==generation with expected>=0 | KILLED 1/15; VT:92 expected REFUNDED, actual ISSUED. |
| session | VM:157 bypass current session-key equality | KILLED 2/15; VT:94/105 detail unexpectedly present across new key/logout. |
| expiry | Contract:31 remove !pixExpired permission guard | KILLED 1/15; VT:116 expected cannot-use, actual true. |
| refund_precedence | Screen:178 remove REFUNDED order override | KILLED 1/4; ST:82 expected refund text, actual confirmed text. |
| copy_expiry_freshness | VM:127 remove immediate expiry Clock check | SURVIVED 15/15; no advancing-clock action test. |
| summary_total | Screen:154 format baseCents instead of totalCents | SURVIVED 4/4; recording screenshots does not assert their monetary text. |

Initial sensor: **9 distinct mutations, 7 killed, 2 survived**.

## Ranked findings and concrete fix tasks

1. **Major production defect — prior cancelled history resolves a newer uncertain POST (UI3/UI6).** VM:74 clears the operation if *any* instrument exists. Reachability: `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/OneOffPayments.kt:41` excludes CANCELLED from live instruments, allowing a new attempt; JdbcPaymentStore:60 returns historical instruments. Independent scratch test `previousCancelledInstrumentCannotResolveANewUncertainPost` loads an old CANCELLED, sends new uncertain Pay, refreshes unchanged old-only history: assertTrue(pending) fails. Log `/tmp/saqz-member-ui-review-probe.log`, script `/tmp/saqz-member-ui-probe.py`; 1 test / 1 assertion failure. Fix: persist non-sensitive prior instrument IDs before the new POST; only a new instrument or authoritative terminal order resolves it; preserve marker across read error/process death. Verify live/restored/legacy-marker/error cases and kill reintroduced any-instrument behavior.
2. **Major production defect — queued payment actions execute after a new generation/refund (UI4/UI6).** Contract:45–47 carries only value; Roots:34 checks session only, while `mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/mvi/MviViewModel.kt:16` buffers effects. Independent scratch `queuedCopyIsNotDeliveredAfterRefundRefreshInSameSession` queues Copy, refreshes to REFUNDED, then collects using existing Root session guard: expected empty delivered list fails (1/1 assertion failure). Log `/tmp/saqz-member-ui-review-effect-probe.log`. Fix: stamp effect generation; validate generation/session/current instrument permission and fresh expiry at consumption; verify no stale Copy/Open and feedback only after copy occurs.
3. **Major test gaps — monetary summary and fresh expiry (UI2/UI4).** The two surviving mutations prove absent discrimination. Fix ST with exact base/fees/total/Pay text assertions before acceptance; add mutable Clock case that passes the deadline without running timer then presses Copy and sees no effect. Done only when both mutants fail behaviorally.
4. **Minor production/coverage gap — history effects lack generation validation (UI1/UI6).** History:19 emits bare order ID; Roots:20 validates only session. A delayed Open can survive refresh removing the listed ID. Fix: carry generation and validate current listing at consumption; test queue→refresh→collect. This is explicit UI6 coverage, not an added feature.

Code quality: scoped changes, existing gateway/session/DI patterns, pure screens, shared business behavior and native-only decoding comply with mobile/AGENTS.md. No unrelated source cleanup or extra provider operation introduced. Payment policies are kept beside state in Contract. Dense formatting is consistent with adjacent receivables files. Financial safety findings above prevent initial approval even though gates pass.

Grounded lessons (report-only scope): (1) Historical child records are not proof of resolution of a new uncertain financial command; correlate against pre-command identity. (2) Buffering effects crosses async context boundaries; revalidate generation and current capability at consumption, not only emission. (3) Recorded screenshots need exact monetary assertions; image generation alone does not discriminate amount regressions. (4) Timer/state expiry tests do not cover wall-clock movement before a user action. These lessons are recorded here because reviewer authorization permits only this report, not separate lesson bookkeeping.

## Re-verification

The initial review awaited an immutable fix snapshot; the completed re-verification follows. Human UAT and real sandbox homologation remain pending independently of the automated verdict.

### Final result — 06d70c7b

**PASS for automated/scoped UI1–UI7 verification at `0a2cf201..06d70c7b`.** All ranked findings are closed. This approval includes the explicit source-only and UAT limits below; it is not a claim that every native interaction was instrumented or that the broader T16 feature is complete. Recovery fix is `6d2bd9f9`; effect/clock/monetary assertion fix is `06d70c7b`. One fix→re-verify cycle closed the findings.

The scratch tree was refreshed directly from `git archive 06d70c7b mobile`. Independent bounded gate exited 0 in 59s, log `/tmp/saqz-member-ui-review-final-baseline.log`: presentation Android/iOS tests and detekt; Android screenshots; DI Android/iOS; navigation iOS; Android/iOS application compilation; compose-app detekt. Commands used the same JDK21/`--offline` scratch invocation as the sensor and explicitly `--rerun` each test task. After all mutations/probes, restored Android presentation and screenshot suites reran successfully in 10s (`/tmp/saqz-member-ui-review-restored.log`). Every one of the 22 scoped mobile files was then checked byte-for-byte against `git show 06d70c7b:<path>`; all identical. The regression probes were removed from scratch after execution.

| Final gate | Passing tests | Evidence / scope |
| --- | ---: | --- |
| Presentation Android host | 43 | Fresh restored suite after sensor, all common VM/coordinator tests. |
| Presentation iOS simulator | 45 | Fresh immutable final baseline, including both screen tests. |
| DI Android host / iOS simulator | 6 + 6 | Fresh immutable final baseline; both member VMs resolve at `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/di/SaqzKoinModulesTest.kt:282–283`. |
| Member navigation iOS | 1 | Fresh immutable final baseline. |
| Member screenshot Android | 4 | Fresh restored suite, exact money assertions included. |
| Monthly-payment entry Android | 1 | Unchanged source/test from independent initial integration gate. |
| **Total** | **106** | Zero failures/errors/skips. 105 executions refreshed against the final snapshot, plus one unchanged entry test from initial independent gate. |

XML counts/timestamps saved in `/tmp/saqz-member-ui-final-gate-counts.json`. Presentation delta versus pre-feature is +20 Android/+21 iOS (five more common methods than ff870197); no existing assertion was weakened or deleted. Existing effect equality fixtures were extended to assert generation, and history collectors now use the same validity predicate as the Root. Groups detekt/entry remain applicable because their source is unchanged from the initial verified snapshot.

Final exact closure evidence (line numbers now refer to 06d70c7b):

| AC / finding | New discriminating assertion and inspected implementation | Final outcome |
| --- | --- | --- |
| UI2 / monetary sensor | ST:49–52 assert exact Base `R$\u00a0100,00`, fees `R$\u00a06,58`, total `R$\u00a0106,58`, and Pay `R$\u00a0106,58`; ST:54–56 switches to CARD and asserts fees `R$\u00a09,00`, total/Pay `R$\u00a0109,00`, still disabled before acceptance. Distinct card fixture arithmetic remains valid. | PASS; wrong displayed total now killed. Additional `revisao-cartao.png` inspected: card price and disabled pre-acceptance Pay visible. |
| UI3/UI6 / historical recovery | VT:91 exact previous-ID marker is present before gateway create; VT:95–96 pending true/cannot review/request unchanged after old-only refresh; VT:98–99 NETWORK keeps marker; VT:100 full identical replay; VT:103–104 process-restored model stays pending, no replay, command count exactly two; VT:107–108 new CANCELLED instrument resolves pending, allows review, acceptance false. VT:114 legacy marker remains pending; VT:117–118 authoritative REFUNDED resolves and creates nothing. VM:79–84 compares returned IDs with saved baseline; VM:113 stores baseline only when first creating the command, preserving replay identity. | PASS; old-history recovery regression and mutation closed. |
| UI4 / direct expiry | VT:213 initially permits instrument; VT:214 sets Clock exactly to expiry without running timer; VT:216 asserts `effects.isEmpty()`, `pixExpired`, and `!copied`. VM:140 checks Clock immediately before emission. | PASS; original survivor killed at exact deadline. |
| UI4/UI6 / payment effect generation and consumption expiry | VT:180–202 loops Pix/Card through same-status refresh, refund, expiry/cancel and session replacement; VT:200 `assertFalse(vm.validEffect(vm.effects.first()), "$method/$change ...")`, VT:201 no copied feedback. VM:168–172 checks generation/session, instrument, fresh expiry and canUseInstrument; Roots:34 calls validEffect before platform action. VT:170–172 asserts valid live effect, no pre-copy feedback, then copied acknowledgment true. Roots:35–38 acknowledges only after clipboard.setText returns. | PASS; independent queued-refund probe and generation/consumption-expiry mutants closed. |
| UI1/UI6 / history queued navigation | HT:56–66 covers same-list refresh, removed-list refresh and changed session; HT:65 `assertFalse(vm.validEffect(vm.effects.first()), change)`. History:46–47 checks session, generation and current listed ID; Roots:20 consumes only valid effect. | PASS; same-list refresh mutation proves generation itself is discriminated. |
| UI5/UI7 | Initial exact status, refund, route/DI, UI callback and visual evidence remains valid; no status or navigation-wiring production changes in fixes. | PASS, with human/native walk-through limits retained. |

The three independent regression probes also reran against final source and **all pass, 1 test each**: `/tmp/saqz-member-ui-review-final-recovery-probe.log`, `...-final-effect-probe.log`, `...-final-history-probe.log`. Recovery probe is unchanged. Effect/history probes preserve their queue→refresh→consume scenario and exact expected empty output, adapting only their simulated Root collector to the final `validEffect` interface. Their original versions each failed against ff870197 (history log `/tmp/saqz-member-ui-review-history-probe.log` supplements initial finding 4).

| Final fault injection | Result | Discriminating failure |
| --- | --- | --- |
| Repeat copy_expiry_freshness | KILLED, 1/20 VM/history failures | VT:216 detects emitted effect after wall-clock expiry. |
| Repeat summary_total | KILLED, 1/4 screenshot failures | ST:51 exact total R$106.58 node absent when base R$100.00 is displayed. |
| Restore old any-instrument recovery condition at VM:84 | KILLED, 2/20 | VT:95 and :114 pending unexpectedly false for new and legacy recovery. |
| Remove generation check only from VM:169 effect consumption | KILLED, 1/20 | VT:200 `PIX/refresh` must discard even when current status stays ACTIVE. |
| Remove fresh expiry update only from VM:171 effect consumption | KILLED, 1/20 | VT:200 `PIX/expiry` must discard queued payload after deadline. |
| Remove generation equality only from History:46 | KILLED, 1/20 | HT:65 `same-list` must discard stale navigation despite ID still listed. |

Every final mutation compiled successfully and failed assertions, with zero test errors/skips. Results `/tmp/saqz-member-ui-final-mutations.json`, script `/tmp/saqz-member-ui-final-sensor.py`, logs `/tmp/saqz-member-ui-mutation-final_*.log`. **Final sensor: 13 distinct behavior faults killed, zero survivors; 15 mutation attempts including the two repeated survivors.** Seven initial kills remain applicable to unchanged guard behavior/assertions; six final kills add the two closed survivors and four recovery/effect guards. Probe executions are separate from mutation counts and gate totals.

Final traceability: UI1/UI5/UI7 remain PASS; UI2/UI3/UI4/UI6 now PASS for the automated/source-reviewed scope. No unresolved scoped production defect, surviving financial mutant, or spec-precision ambiguity found. The pending-only BackHandler and header-back condition were inspected in source; system-back behavior was not independently driven on a native device. Hosted checkout and clipboard dispatch have unit/UI intent and source-wiring evidence, not a complete native external-browser/clipboard walk-through. Full Profile→order human journey, live sandbox payment/return/reconciliation, and human UAT remain pending. T16 remains partial beyond this mobile UI priority group.

The coordinator additionally recorded the four grounded lessons through the skill's lessons.py in `.specs/LESSONS.md` and `.specs/lessons.json`; no separate reviewer writes were required. Initial findings, mutation history and their original source line citations remain preserved above.
