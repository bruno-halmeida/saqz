# Independent TLC verification — final recurring mobile

Date: 2026-09-13. Verifier: dispatched independent reviewer (`task_ee7665486ccc`, `ctx_7316cde648c3`), author != verifier.

**Final corrected snapshot: PASS within the requested mobile scope. Initial frozen snapshot: FAIL, corrected and independently reverified in this dispatch.** All nine behavior findings and the deferred-callback test gap are resolved; the original independent discriminants pass on the corrected source. No production source, tests, Git state, or shared-workspace Gradle outputs were changed by this verifier; the only repository artifact written is this report.

## Scope and provenance

Source-authoritative review of `Recurrence*`, `PixRenewal*`, `ReceiptExport*`, `MemberPayment*`, composition-root DI/navigation/runtime/share binding, and restoration. Contracts: `final-recurring-mobile-contract.md` RM-01..RM-14 and `final-recurrence-contract.md` AC-M01/R01/R02/R04/R05/R06/R08/P01/P02/P03. Backend, unrelated wallet/management, real Asaas homologation, financial execution and visual redesign are excluded.

Applicable instructions read: `mobile/AGENTS.md`, TLC `SKILL.md`, `references/validate.md`, `references/sub-agents.md`, `references/lessons.md`, Orca orchestration skill and version-matched guide. The user’s scratch-only/no-Git constraints supersede skill commit/worktree and project bookkeeping steps.

Scratch: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e`. It was created from `git archive HEAD` at `cd0a4a3c19224a5b254a7ff860f6a4f00a60d2d3`, overlaid with all 63 modified/untracked mobile files observed at snapshot time; 952 mobile-file SHA-256 entries are persisted in `snapshot-sha256.json`. Only `sdk.dir` was copied from local.properties. No secrets or real provider calls were used. All HTTP behavioral tests use MockEngine/fakes.

## Final corrected verification

All corrections were made by the coordinator or its designated author, then copied into the independent scratch. The verifier reran the **original** assertions in separate scratch test classes without weakening them, including the calendar, 202, unexpected due-date, queued-session, cancellation status, failed preview, unknown receipt, negative-overflow, and deferred-callback cases. The coordinator also retained these assertions in production tests. A full byte-for-byte comparison of every tracked/untracked mobile source and test file against scratch at completion found **zero differences** (`final-exact-production-sha256.json`); sanitized local.properties is not a source/test file.

| Gate | Tests | Failed / errors / skipped | Result |
|---|---:|---|---|
| `features/receivables/domain:testAndroidHostTest` (production + independent scratch tests) | 10 | 0 / 0 / 0 | PASS |
| `features/receivables/domain:iosSimulatorArm64Test` (production + independent scratch tests) | 10 | 0 / 0 / 0 | PASS |
| `features/receivables/data:testAndroidHostTest` (production + independent scratch tests) | 73 | 0 / 0 / 0 | PASS |
| `features/receivables/data:iosSimulatorArm64Test` (production + independent scratch tests) | 73 | 0 / 0 / 0 | PASS |
| `features/receivables/presentation:testAndroidHostTest` (production + independent scratch tests) | 121 | 0 / 0 / 0 | PASS |
| `features/receivables/presentation:iosSimulatorArm64Test` (production + independent scratch tests) | 132 | 0 / 0 / 0 | PASS |
| `compose-app:testAndroidHostTest` (production + independent scratch tests) | 13 | 0 / 0 / 0 | PASS |
| `compose-app:iosSimulatorArm64Test` (production + independent scratch tests) | 4 | 0 / 0 / 0 | PASS |
| `features/receivables/domain:testAndroidHostTest` (exact production tests after restoring all mutations) | 7 | 0 / 0 / 0 | PASS |
| `features/receivables/data:testAndroidHostTest` (exact production tests after restoring all mutations) | 67 | 0 / 0 / 0 | PASS |
| `features/receivables/presentation:testAndroidHostTest` (exact production tests after restoring all mutations) | 107 | 0 / 0 / 0 | PASS |
| `android-app:testDevDebugUnitTest` (exact production tests after restoring all mutations) | 5 | 0 / 0 / 0 | PASS |

The platform run totals **436 test executions**, not 436 unique cases (common tests run on both targets and separate verifier classes deliberately repeat some assertions). After removing the verifier-only test copies, the final exact-production Android pass totals **186 executions**: domain 7, data 67, presentation 107, and 5 Android rendering tests. Both platform configurations compile the same corrected commonMain source; the iOS presentation suite includes the shared screen interactions. Composition root host ran 13 tests; scoped iOS ran 4 (`SaqzKoinBootstrapTest`, `MemberPaymentNavigationTest`, `ReceiptExportBindingTest`).

`final-platform-gates.log`: PASS in 1m30s. Command (from scratch mobile):

```sh
./gradlew :features:receivables:domain:allTests :features:receivables:data:allTests :features:receivables:presentation:allTests :compose-app:testAndroidHostTest :compose-app:iosSimulatorArm64Test --tests '*SaqzKoinBootstrapTest*' --tests '*MemberPaymentNavigationTest*' --tests '*ReceiptExportBindingTest*' --continue --console=plain
```

`final-android-quality.log`: PASS in 22s; no suppressions introduced. Scoped `git diff --check` also passed. Command:

```sh
./gradlew :features:receivables:domain:testAndroidHostTest :features:receivables:data:testAndroidHostTest :features:receivables:presentation:testAndroidHostTest :android-app:testDevDebugUnitTest --tests '*RecurrenceScreenshotTest*' --tests '*MemberPaymentScreenshotTest*' -Proborazzi.test.record=true :core:network:detektAll :features:receivables:domain:detektAll :features:receivables:data:detektAll :features:receivables:presentation:detektAll :compose-app:detektAll --continue --console=plain
```

Final mutation retention check: `final-M5-production-callback.log` compiled successfully, ran 3 production callback tests, and failed exactly `ReceiptExportCallbackTest.verifierShareWaitsForRealCallback` with `AssertionError: no optimistic success before callback`. The mutation was restored before the 186-test exact-production Android pass. Thus **all six distinct injected faults are killed by retained test protections**, with M5’s initially surviving gap explicitly demonstrated and closed.

Android captured **42 PNGs** (7 recurrence, 35 payment). Independently opened and inspected: `recorrencia-revisao.png` (10000/490/10490 cents, real terms and disabled submit before consent), `recorrencia-cancelamento-pendente.png` (uncertain cancellation, timezone cutoff, refresh only), `recorrencia-encerrada-retomada.png` (historical stopped + new consent instructions), `recorrencia-cartao-checkout.png` (hosted Asaas action, no payment claim), `prazo-pix-vencido-renovacao.png` (same payment identity and literal 10000/658/10658 snapshot), `comprovante-compartilhado.png` (“Compartilhamento aberto.”), and `comprovante-falha.png` (distinct failure). They live under `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e/mobile/build/reports/{recurrence-ui,member-payment-ui}`. Device-level manual UAT and live Asaas remain excluded; native share success confirms launcher handoff only.

### Final exact assertion trace

These are current production assertions, all executed by the restored gates; the initial matrix above explains where its original coverage was insufficient.

| AC | Exact current production evidence | Result |
|---|---|---|
| RM-01 | [RecurrenceViewModelTest.kt:32](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:32) — `assertEquals(ReceiptError.DENIED, vm.state.value.error)` | PASS |
| RM-02 | [RecurrenceViewModelTest.kt:41](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:41) — `assertEquals(validReview, vm.state.value.review)`; [RecurrenceViewModelTest.kt:53](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:53) — `assertEquals("a".repeat(64), command.fingerprint)` | PASS |
| RM-03 | [RecurrenceViewModelTest.kt:77](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:77) — `assertFalse(saved.keys().any { it.contains("name") &#124;&#124; it.contains("document") &#124;&#124; it.contains("cpf") })`; [RecurrenceViewModelTest.kt:55](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:55) — `assertTrue(command.accepted)` | PASS |
| RM-04 | [MemberPaymentViewModelTest.kt:176](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt:176) — `assertEquals("ACTIVE", vm.state.value.instrument?.status); assertTrue(f.commands.isEmpty())` | PASS |
| RM-05 | [RecurrenceViewModelTest.kt:93](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:93) — `assertEquals(listOf(request), fake.recoveries.map { it.requestId })`; [KtorRecurringPaymentsGatewayTest.kt:184](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorRecurringPaymentsGatewayTest.kt:184) — `assertEquals(1, posts, "renewal must await GET recovery")` | PASS |
| RM-06 | [RecurrenceViewModelTest.kt:148](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:148) — `assertTrue(vm.state.value.pending, "ACTIVE is not remote cancellation proof")`; [RecurrenceViewModelTest.kt:122](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:122) — `assertFalse(vm.state.value.pending); assertEquals("STOPPED", vm.state.value.recurrence?.status)` | PASS |
| RM-07 | [RecurrenceViewModelTest.kt:104](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:104) — `assertTrue(vm.state.value.canResume); assertFalse(vm.state.value.canSubmit)`; [RecurrenceViewModelTest.kt:108](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:108) — `assertNotEquals("recurrence", vm.state.value.recurrence?.id)` | PASS |
| RM-08 | [RecurrenceViewModelTest.kt:160](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:160) — `assertTrue(fake.acceptances.isEmpty(), "403 must block old review authorization")`; [RecurringPaymentsTest.kt:38](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/domain/src/commonTest/kotlin/br/com/saqz/receivables/domain/RecurringPaymentsTest.kt:38) — `assertTrue(order.canRenew(instrument, expired = true))` | PASS |
| RM-09 | [MemberPaymentViewModelTest.kt:239](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt:239) — `assertEquals("instrument", vm.state.value.instrument?.id)`; [MemberPaymentViewModelTest.kt:241](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt:241) — `assertEquals(paymentQuote, vm.state.value.instrument?.quote)` | PASS |
| RM-10 | [ReceiptExportCallbackTest.kt:48](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceiptExportCallbackTest.kt:48) — `assertTrue(fake.renewalCommands.isEmpty(), "invalid date must not reach network")`; [MemberPaymentViewModelTest.kt:219](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt:219) — `assertTrue(effects.isEmpty()); assertTrue(vm.state.value.pixExpired); assertFalse(vm.state.value.copied)` | PASS |
| RM-11 | [ReceiptExportCallbackTest.kt:28](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceiptExportCallbackTest.kt:28) — `assertFalse(vm.state.value.receiptShared, "no optimistic success before callback")`; [ReceiptExportCallbackTest.kt:39](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceiptExportCallbackTest.kt:39) — `assertFalse(vm.state.value.receiptSharing, "return from native share must not freeze all payment intents")` | PASS |
| RM-12 | [ReceiptMoneyBoundaryTest.kt:12](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/domain/src/commonTest/kotlin/br/com/saqz/receivables/domain/ReceiptMoneyBoundaryTest.kt:12) — `assertNull(MemberPaymentDetail(order, listOf(paid)).receiptInstrument(), "unknown order must fail closed")`; [ReceiptMoneyBoundaryTest.kt:17](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/domain/src/commonTest/kotlin/br/com/saqz/receivables/domain/ReceiptMoneyBoundaryTest.kt:17) — `assertFalse(value.validFor("account", "group", "payer"), "overflow total is negative")` | PASS |
| RM-13 | [MemberPaymentViewModelTest.kt:297](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt:297) — `assertTrue(f.renewalCommands.isEmpty(), "old session queued renewal must not reach network")`; [RecurrenceViewModelTest.kt:137](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt:137) — `assertFalse(vm.validEffect(effect))` | PASS |
| RM-14 | [MemberPaymentNavigationTest.kt:26](/Users/bruno_almeida/Private/saqz/mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/MemberPaymentNavigationTest.kt:26) — `assertEquals(stack.toList(), restored.toList())`; [MemberPaymentNavigationTest.kt:28](/Users/bruno_almeida/Private/saqz/mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/MemberPaymentNavigationTest.kt:28) — `assertEquals(listOf(AccessRoute.Login), restored.toList())`; [RecurrenceScreenTest.kt:25](/Users/bruno_almeida/Private/saqz/mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceScreenTest.kt:25) — `onNodeWithTag(RecurrenceTags.Submit).assertIsEnabled().performClick()` | PASS |

The money/HTTP cases additionally compare exact `Failure(UNCERTAIN)` results, not merely error presence, in the retained `verifierAccepted*`, `verifier*Different*`, and no-replay data tests. Stable attempts are saved before network with actor/account/group/resource/request only; PII remains in memory. No scoped test count decreased versus HEAD (`evidence/test-integrity.json`); the former tests that expected automatic POST retries were strengthened to the user’s one-POST-plus-GET contract, not deleted.

Final practical limitation: this is a mobile contract, state-machine, adapter, and render verification using deterministic fakes/MockEngine. It does not prove real provider settlement or recipient receipt of shared content. **No in-scope follow-up remains.**

## Initial findings, ranked and reproduced

| ID | Priority / AC | Actual behavior and required correction | Independent evidence |
|---|---|---|---|
| F1 | Major RM-05 | `KtorRecurrenceGateway.call` and `KtorPixRenewalGateway.call` retry financial POST after timeout/5xx; one attempt sends four POSTs. Send once and recover by GET with the same request ID. | `verifierFinancialTimeoutDoesNotReplayRecurrencePost` and `verifierFinancialTimeoutDoesNotReplayRenewalPost`: expected 1, observed 4. |
| F2 | Major RM-05/RM-06/RM-09 | HTTP 202 with a value snapshot becomes definitive Success in both gateways because HTTP success status is discarded. Preserve HTTP status; 202 remains UNCERTAIN even with value. | `verifierAcceptedRecurrenceSnapshotRemainsUncertain`, `verifierAcceptedRenewalSnapshotRemainsUncertain`: expected Failure(UNCERTAIN), got Success. |
| F3 | Major RM-02/RM-09/RM-10/RM-12 | Accepted recurrence/renewal response may change the requested due date; regex-only validation also sends impossible `2026-99-99` renewal to the gateway. Match dates to commands and validate calendar dates. | `verifierAcceptanceRejectsDifferentFirstDueDate`, `verifierRenewalRejectsDifferentDueDate`, `verifierInvalidCalendarRenewalDoesNotReachNetwork`. |
| F4 | Major RM-11/RM-13 | Refresh during pending native share increments generation, discards its callback, and leaves receiptSharing true indefinitely; all non-refresh payment intents remain blocked. Keep share operation lifetime coherent with refresh/resume. | `verifierRefreshDuringShareDoesNotLeavePermanentBusyState`: expected receiptSharing false after real callback, got true. |
| F5 | Major RM-13 | A queued renewal still invokes gateway after session changes before the coroutine runs. Check generation/session before network entry as well as after the response. | `verifierQueuedRenewalAfterSessionChangeCannotReachNetwork`: StandardTestDispatcher + logout + runCurrent causes a renewal command. |
| F6 | Major RM-06 | CANCEL recovery with ACTIVE clears the marker and pending state, enabling another cancel without remote cancellation proof. Validate recovered status against operation. | `verifierCancelRecoveryActiveDoesNotClearUncertainty`: expected pending true and saved request retained, got false. |
| F7 | Major RM-08 | An accepted review survives a subsequent preview 403; Submit uses the old consent despite eligibility denial. Invalidate old review/consent before re-preview and block stale submission. | `verifierIneligiblePreviewInvalidatesPreviousConsent`: old authorization reaches fake after DENIED. |
| F8 | Major RM-12 | Receipt eligibility blacklists a few order statuses and accepts UNKNOWN_REMOTE with CONFIRMED instrument. Use an explicit supported-order eligibility set. | `verifierUnknownOrderCannotExportReceipt`: expected null, got instrument. |
| F9 | Major RM-02/RM-12 | Recurrence and review accept a negative Long.MIN_VALUE total when Long.MAX_VALUE + 1 overflows. Validate nonnegative values and overflow-safe monetary identities. | `verifierOverflowCannotMakeNegativeRecurrenceMoneyValid`, `verifierOverflowCannotMakeNegativeReviewMoneyValid`: expected false, got true. |
| F10 | Test gap RM-11 | Optimistic receipt-success mutation survives the author ViewModel suite because callback runs synchronously. Add deferred callback assertions before and after completion. | M5 survived author tests, then was killed by `verifierShareWaitsForRealCallback`; unmutated implementation passes that test. |

All findings were sent immediately through Orca lifecycle messages. These are behavioral assertion failures, not compiler failures. The 15 independent initial tests produced **14 assertion failures and 1 pass** across data (6/6 failed), presentation callback/date (2/3 failed), queued-session/cancel (2/2 failed), eligibility (1/1 failed), and closed receipt/money (3/3 failed).

Independent test sources are preserved in `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e/evidence/`: `KtorRecurringPaymentsGatewayDiscriminants.kt`, `VerifierReceiptCallbackTest.kt`, `MemberPaymentViewModelTest.kt`, `RecurrenceViewModelTest.kt`, `VerifierClosedReceiptMoneyTest.kt`. The report does not infer production fixes from coordinator claims; corrected outcomes were rerun above.

## Spec-anchored coverage

File locations below refer to the reviewed initial source; final source hashes and corrected assertions will be recorded separately.

| Criterion | Spec-defined outcome | Existing assertion evidence | Initial verdict |
|---|---|---|---|
| RM-01 | Discover own recurrence only, preserve absence, hide foreign payer | `RecurrenceViewModelTest.kt:25`: assertNull(recurrence), assertEquals(DENIED,error); `KtorRecurringPaymentsGatewayTest.kt:17`: exact GET/query/Bearer and Success(null). | Covered |
| RM-02 | Literal cents, exact context/terms/due/fingerprint | `RecurringPaymentsTest.kt:15`: total10491/net9999 rejected; `RecurrenceViewModelTest.kt:35`: exact review, terms, fingerprint; iOS `RecurrenceScreenTest.kt:12`: 10000/10490 formatted labels. | Gaps F3/F9 |
| RM-03 | Actual terms + valid payer + explicit consent, no persisted PII | `RecurrenceViewModelTest.kt:35`: commands empty before accepted; exact payer/fingerprint after; `:69`: marker-before-network and absence of name/document keys. | Covered; M6 killed |
| RM-04 | Manual monthly Pix; hosted Asaas checkout; return never confirms locally | `RecurrenceScreenTest.kt:12`: manual-Pix text; `MemberPaymentViewModelTest.kt:160`: action per method and ACTIVE after refresh; `:285`: unsafe-host rejection. | Covered; M3 killed |
| RM-05 | Stable marker before network, uncertainty locks mutations, GET-only recovery | `RecurrenceViewModelTest.kt:69`: restored request remains same and no new authorize; `MemberPaymentViewModelTest.kt:242`: no new renewal after restore. | Gaps F1/F2; M2 killed |
| RM-06 | STOP_PENDING until remote STOPPED; no optimistic cancel/repeated cancel | `RecurrenceViewModelTest.kt:111`: pending survives STOP_PENDING, clears only when fake returns STOPPED. | Gaps F2/F6 |
| RM-07 | STOPPED resume requires new review/consent/request and a new recurrence id | `RecurrenceViewModelTest.kt:101`: canResume, !canSubmit, no authorize, resumed old id/new result id; gateway `:60` checks separate resume resource. | Covered; M6 killed |
| RM-08 | Eligibility loss blocks authorize/resume; existing obligation remains accessible | `RecurringPaymentsTest.kt:37`: expired existing Pix canRenew; there was no discriminant for failed second preview. | Gap F7 |
| RM-09 | Renew same order/instrument/payment/quote and money | `MemberPaymentViewModelTest.kt:220`: exact saved context and preserved instrument/payment/quote; gateway `:105`: documented renewal/recovery paths; `:119`: changed ids/total rejected. | Gaps F1/F2/F3; M4 killed |
| RM-10 | Expiry prevents effects without reload; invalid date no network; old generation cannot win | `MemberPaymentViewModelTest.kt:203`: advance clock, effects empty; `RecurrenceViewModelTest.kt:126`: delayed discovery cannot replace newer state. | Gap F3 |
| RM-11 | Only observed paid states export; feedback only after real callback | `RecurringPaymentsTest.kt:50`: ACTIVE with milestones does not qualify; `MemberPaymentViewModelTest.kt:260`: receipt reference/payment/total/status, no payer/cpf; `ReceiptExportBindingTest.kt:9`: assertNull(result) before callback. | Gaps F4/F8/F10 |
| RM-12 | Foreign context, malformed amounts/URLs/fingerprints/content fail closed | domain `RecurringPaymentsTest.kt:15,25,37,50`; data `KtorRecurringPaymentsGatewayTest.kt:83,94,119,136`; VM foreign-payer tests. | Gaps F2/F3/F8/F9 |
| RM-13 | Generation/session/actor invalidation clears sensitive state and callbacks | `RecurrenceViewModelTest.kt:126`: invalid effect after revoked session; `MemberPaymentViewModelTest.kt:120,132,181`: stale response/marker/queued effect rejection. | Gaps F4/F5; M1 killed |
| RM-14 | Shared PT-BR UI exercised on iOS; Android render states; routes restore | `RecurrenceScreenTest.kt:12,30`; MemberPayment iOS tests; RecurrenceScreenshotTest seven states; `MemberPaymentNavigationTest.kt:16` round-trip stack and logout Login; `SaqzKoinBootstrapTest.kt:60,63` resolves both VMs. | PASS after final gates below |

No refund request, approval, or execution intent, CTA, gateway endpoint, or effect was found in this scope. REFUNDED is passive remote history/status and blocks applicable payment actions. DI binds real NativeSharePort adapter, gateways, clock, session and authenticated actor identity. Scalar serializable RecurrenceRoute(accountId,groupId) and MemberPaymentRoute(orderId) are registered in the nav serializer and host; MemberPayment’s observed own detail supplies recurrence route identifiers. Android share success represents opening/handoff of the native share sheet, not proof of recipient delivery; coordinator announced wording correction to “Compartilhamento aberto.”.

## Mutation discrimination sensor

All six faults were injected into scratch production source, compiled successfully, tested on Android host, and discarded. Exact source substitutions, logs, and XML failure messages are in `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e/evidence/mutations.json`. No fault was counted killed for a compilation failure.

| Mutation | Focused suite | Result |
| M1-ignore-session | `lateGenerationAndRevokedSessionCannotPublishRecurrenceOrCheckoutEffect` | KILLED by assertion |
| M2-restore-renewal-post | `uncertainPixRenewalRestoresByRequestAndNeverPostsAnotherRenewal` | KILLED by assertion |
| M3-checkout-confirms-payment | `activePixAndHostedCardEmitOnlyTheirOwnActionAndRefreshNeverConfirmsLocally` | KILLED by assertion |
| M4-wrong-amount-fee | `pixRenewalTimeoutRetriesByteIdenticalAndMismatchedSnapshotStaysUncertain` | KILLED by assertion |
| M5-optimistic-export-author | `all selected author tests passed` | SURVIVED — missing deferred callback discriminant |
| M5-optimistic-export-discriminant | `verifierShareWaitsForRealCallback` | KILLED by assertion |
| M6-bypass-consent | `exactReviewTermsPayerAndAcceptanceAreRequiredForAuthorization` | KILLED by assertion |

Sensor outcome: **6 distinct faults, 5 killed by existing author tests; the sixth survived until a scratch-only discriminant killed it.** The coordinator retained the discriminant as ReceiptExportCallbackTest; a final M5 injection was killed by that production test, resolving F10.

## Gates and logs

- Initial authored host gate: `:features:receivables:domain:testAndroidHostTest :features:receivables:data:testAndroidHostTest :features:receivables:presentation:testAndroidHostTest` — PASS in 6s; 4 domain, 61 data, 97 presentation tests reported for the frozen integrated suites (initial task PASS independently observed; data/presentation restored from Gradle build cache, and XML was not archived before the first targeted run). Log `baseline-host.log`.
- Independent discriminants: `discriminants.log`, `session-cancel-discriminants.log`, `eligibility-discriminant.log`, `closed-money-discriminants.log`; XML copied under `evidence/`.
- Mutation runs: seven executions (six faults; M5 rerun with new discriminant), `M1-*.log` through `M6-*.log`; compiled source and assertion failures recorded per run.
- Root’s initial `/tmp/saqz-final-mobile-runtime.log` PASS is author/coordinator evidence, not substituted for independent corrected gates.
- Final corrected Android/iOS, DI/restoration, rendering and quality gates: PASS, detailed above.

## Lessons and remaining work

Grounded lessons for coordinator retention: use deferred native callbacks in ViewModel tests; assert one financial POST under timeout then stable GET recovery; retain HTTP 202 semantics above transport decoding; invalidate consent when starting a new review; check session/generation before queued network work; validate recovery state against operation; use explicit closed status sets and overflow-safe money validation. No `.specs` files were mutated because user restricts this worker to the evidence report and scratch state.

Final verdict is PASS after source synchronization and executable corrected gates; no open in-scope fix remains. Real provider homologation and device-level manual UAT remain outside this local verification.

## Initial exact file hashes

Full manifest: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e/snapshot-sha256.json`; HEAD: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e/head.txt`.

| File | SHA-256 |
|---|---|
| `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/MemberPaymentScreenshotTest.kt` | `287322992bd125c7ae5650ab74195df9ac27d806471b1c5288014bfb04914428` |
| `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/RecurrenceScreenshotTest.kt` | `8baced141b96bd8a4465d51ae4302914c374c70ca99f93c3cac307fbfac45d00` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt` | `8aeaf0ba349ae3f2888608ee23db62d2405e76e503d53405064746d517b5a5de` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzLocalNavConfiguration.kt` | `fab54376f4dc1335f225856eeb1a58b8cf81d962ee272da57647b2dbad204bfc` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt` | `1dddf04fb980703021d01a28b885441776f851267a853797bcf21b1b03ea8392` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/receivables/ReceiptExportBinding.kt` | `47c3d9d17edf681b5864df9f6458f2c082c49fca96f7c0d40a0e028f51becae9` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/SaqzKoinBootstrapTest.kt` | `869aa5b8136cfc530bed401c02c3ec7c09a6fae5b67ad6f8e1fd5f3d7fddd324` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/MemberPaymentNavigationTest.kt` | `f878ddc4c9a48db3075fb1df5c4c141591351faf2db110bd0b48290c2b9f1b55` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHostTest.kt` | `844f912f0e28fa0c3eb0a695e6cd776961fe974e5641262fd61a4947c4105f90` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHostViewModelScopeTest.kt` | `41fc6bedd80120f4ced0444eec8fc9ab329c6d118b67f7e47c46c47a2e7f66c9` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceiptExportBindingTest.kt` | `ac4515abace586dcca61caba7f5273422609f13acc72c41d944eef69b15a640f` |
| `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/components/GroupRecurrenceSection.kt` | `4927e0f551edbac187103aec79f175e819c0d29244639908d77a20eb90116e44` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorMemberPaymentsGateway.kt` | `b4ba6f285132d9aa2c1379f81fe15d105052c9020d82fec645c7da090996a35f` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorPixRenewalGateway.kt` | `e82e5cd02d0f9eddf880e51262fdea6578f534f38682905fb283108f9ff357ec` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorRecurrenceGateway.kt` | `33dbea78d8b7af3a371dd8763ffab0fdd21cbfcbd4b9202137dd552a92bd585f` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/MemberPaymentTransports.kt` | `18534efd03f90e09d53a850f591d2576220c6e9b707242cdf700694e08c30e6f` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/RecurrenceTransports.kt` | `f9c69c8390310c81db1ffb84ff07110432e9ac6a22d4a955603e5e979b9c3645` |
| `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorMemberPaymentsGatewayTest.kt` | `a3338032ed2013dc773d6d131e8a45dc4817b81b3a3e83290831b51755f13f2c` |
| `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorRecurringPaymentsGatewayTest.kt` | `b31f61b4ba754844f6d8991edda2a9eca74ca6b7bf6ae340218262ac16d98541` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/MemberPayments.kt` | `132444781562353707fc80233731411d2bb2168e336d80e2a6c303231409b44a` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/PixRenewal.kt` | `567a3a2203334107b032c7b3db4639ec4c109a08a8ff231105001f5c3ebbcd68` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/ReceiptExport.kt` | `efba0e4a9f6d6a55db62f3a677729099677f56e5a7b4e324b379f1953dd58e60` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/Recurrence.kt` | `61d431d7d01ea7315270a0ac606af4f4ca69e76dd11e098ec53b6c880e8ebd8a` |
| `mobile/features/receivables/domain/src/commonTest/kotlin/br/com/saqz/receivables/domain/RecurringPaymentsTest.kt` | `944459398a4f71ba9ffc71abc6713a4d87fdc2b70de8b0e26529312d5e0c8add` |
| `mobile/features/receivables/presentation/src/commonMain/composeResources/values/recurrence.xml` | `8015ece86450937b254078ed6e74cf1b82774395b1ef17263d23785835899f56` |
| `mobile/features/receivables/presentation/src/commonMain/composeResources/values/strings_member_payment.xml` | `6a332f03930a09083506cc4f5264befce9b53d45e4c185d1072c210c35bd7359` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentContract.kt` | `d12b96623498547eb06559109b39957191fcde8b79f55d8a0330e7fe93857507` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentHistoryViewModel.kt` | `c9af91958c64453f2ef42a57cc09f334e5d550599411fdedbf63e392e26241cd` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentRoots.kt` | `e91d745dc1dbeb73af1eb100c8c4cd385dd47db3082d63274cb28375a73419f9` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentScreens.kt` | `86e5e84351915359c9c17deb6b47b0785c1467c04cb63062c747ab9eff0bc7bb` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModel.kt` | `72814fee6893c9bb390905277d4fdb893919678355ff6ae82d2505bfb6ba90fb` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceiptExportText.kt` | `796136cc1000dc0efdda70d44f5d114afc546b7ede94ed574279b85ca9008db6` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceContract.kt` | `1cb598b5bafb3d79352f8ec71f067cf4088bddb7ace77c1d5af869146ba24145` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceRoots.kt` | `02af5439ad1213d87696b8071c34f383d516885955a42f2c38307c59a97e1b72` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceScreen.kt` | `c366a267ea39dc3e80829420f3d090ba79e2662e1ffb8a6f426f89570751d9db` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModel.kt` | `ca3f3802972ab3aae4d7294868edb380d1adce79b5e14663fb7ace439c88ee49` |
| `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentHistoryViewModelTest.kt` | `21af066b70e287e5a16a7c81f5afbe362870918f7070cef49384772d770aa23c` |
| `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt` | `b609930e883a18d8e5e4e0e6f7e49ef92ff00b266f883e68885441288b886db9` |
| `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt` | `afb5456d0cca3e80cc2b82c4aaa375c69750d7320392cde6eb4b9da606efeb3a` |
| `mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentScreenTest.kt` | `6ac6424adc48887b0b039e5cc3167172dc4dddc876bfc8af4e7758a0491cbd04` |
| `mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceScreenTest.kt` | `25a723d412dfb9bcd559cefd28ad3d253068740cafed4632345a7f6486d758ed` |

## Final corrected file hashes

Full exact production manifest: `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e/final-exact-production-sha256.json`. All source/test files below match the final tested scratch byte-for-byte.

| File | SHA-256 |
|---|---|
| `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/MemberPaymentScreenshotTest.kt` | `287322992bd125c7ae5650ab74195df9ac27d806471b1c5288014bfb04914428` |
| `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/RecurrenceScreenshotTest.kt` | `8baced141b96bd8a4465d51ae4302914c374c70ca99f93c3cac307fbfac45d00` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/di/ReceivablesModule.kt` | `8aeaf0ba349ae3f2888608ee23db62d2405e76e503d53405064746d517b5a5de` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzLocalNavConfiguration.kt` | `fab54376f4dc1335f225856eeb1a58b8cf81d962ee272da57647b2dbad204bfc` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt` | `1dddf04fb980703021d01a28b885441776f851267a853797bcf21b1b03ea8392` |
| `mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/receivables/ReceiptExportBinding.kt` | `47c3d9d17edf681b5864df9f6458f2c082c49fca96f7c0d40a0e028f51becae9` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/SaqzKoinBootstrapTest.kt` | `869aa5b8136cfc530bed401c02c3ec7c09a6fae5b67ad6f8e1fd5f3d7fddd324` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/MemberPaymentNavigationTest.kt` | `f878ddc4c9a48db3075fb1df5c4c141591351faf2db110bd0b48290c2b9f1b55` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHostTest.kt` | `844f912f0e28fa0c3eb0a695e6cd776961fe974e5641262fd61a4947c4105f90` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHostViewModelScopeTest.kt` | `41fc6bedd80120f4ced0444eec8fc9ab329c6d118b67f7e47c46c47a2e7f66c9` |
| `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/receivables/ReceiptExportBindingTest.kt` | `ac4515abace586dcca61caba7f5273422609f13acc72c41d944eef69b15a640f` |
| `mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/HttpTransport.kt` | `32a13570fff5bf0d5d5e0aad3ed2c32f4dd90398d612af638e86871b2884f511` |
| `mobile/core/network/src/commonMain/kotlin/br/com/saqz/network/NetworkModels.kt` | `96669b1db76df02d180427cf57e561b1e1e535d1885d68089313a5834d240272` |
| `mobile/core/network/src/commonTest/kotlin/br/com/saqz/network/NetworkClientTest.kt` | `132ffc2101faad08d77eee36bd1126da795a805cde1dc0bad0114b3692390c66` |
| `mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/components/GroupRecurrenceSection.kt` | `4927e0f551edbac187103aec79f175e819c0d29244639908d77a20eb90116e44` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorMemberPaymentsGateway.kt` | `b4ba6f285132d9aa2c1379f81fe15d105052c9020d82fec645c7da090996a35f` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorPixRenewalGateway.kt` | `79f842a1f2316bb6058b4aa397a7e1195c55217e37af32c367ffa2130a0d3b57` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorRecurrenceGateway.kt` | `e71bbaa0ad783589ad2213f27071c16c1b2cb1f80122fef354898f385548aecf` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/MemberPaymentTransports.kt` | `18534efd03f90e09d53a850f591d2576220c6e9b707242cdf700694e08c30e6f` |
| `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/RecurrenceTransports.kt` | `f9c69c8390310c81db1ffb84ff07110432e9ac6a22d4a955603e5e979b9c3645` |
| `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorMemberPaymentsGatewayTest.kt` | `a3338032ed2013dc773d6d131e8a45dc4817b81b3a3e83290831b51755f13f2c` |
| `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorRecurringPaymentsGatewayTest.kt` | `11426db35cc942558b3dcc4823336af716c25103b1e46a0371001339e2be1cc0` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/MemberPayments.kt` | `132444781562353707fc80233731411d2bb2168e336d80e2a6c303231409b44a` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/PixRenewal.kt` | `12b5478e0c96d6b06a19b21bf4f60ea30d946fb2a0a5e0b58eb468983fba2895` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/ReceiptExport.kt` | `48b0b3fc5f83edc1f6a514f09c2b026a87078ffed9e824b46647c4f807fd1eaf` |
| `mobile/features/receivables/domain/src/commonMain/kotlin/br/com/saqz/receivables/domain/Recurrence.kt` | `a4f6aaa2242290c4100510d80e2a4a9f65eb41c44b0c3aa5e29905dae2645d6b` |
| `mobile/features/receivables/domain/src/commonTest/kotlin/br/com/saqz/receivables/domain/ReceiptMoneyBoundaryTest.kt` | `5c52d13ee7cdce56079891d28454c4118addbced7c9147cd7f6afb22b0092ff7` |
| `mobile/features/receivables/domain/src/commonTest/kotlin/br/com/saqz/receivables/domain/RecurringPaymentsTest.kt` | `944459398a4f71ba9ffc71abc6713a4d87fdc2b70de8b0e26529312d5e0c8add` |
| `mobile/features/receivables/presentation/src/commonMain/composeResources/values/recurrence.xml` | `8015ece86450937b254078ed6e74cf1b82774395b1ef17263d23785835899f56` |
| `mobile/features/receivables/presentation/src/commonMain/composeResources/values/strings_member_payment.xml` | `8c0d4ae47f2b3a41eb615efc28dbc851d97b2d2a86704bb69402b3ddd14dd023` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentContract.kt` | `6ccdcbee362aa2955a7420fdb7dd441bbb745991090700322b0559a2d1363593` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentHistoryViewModel.kt` | `c9af91958c64453f2ef42a57cc09f334e5d550599411fdedbf63e392e26241cd` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentRoots.kt` | `e91d745dc1dbeb73af1eb100c8c4cd385dd47db3082d63274cb28375a73419f9` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentScreens.kt` | `86e5e84351915359c9c17deb6b47b0785c1467c04cb63062c747ab9eff0bc7bb` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModel.kt` | `31c0e38efbe1cdf358feb9db743befb9a671c4c2a5c20805ebd730a960ebf144` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/ReceiptExportText.kt` | `9de9d91830a24e1c36573d42241ab9c31e15f3ec969f5017cd5cbdfdb680198f` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceContract.kt` | `670997ef8daafb1c66a9ff5341517753f8497390be697b7be675d97d9f0efddc` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceRoots.kt` | `02af5439ad1213d87696b8071c34f383d516885955a42f2c38307c59a97e1b72` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceScreen.kt` | `c366a267ea39dc3e80829420f3d090ba79e2662e1ffb8a6f426f89570751d9db` |
| `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModel.kt` | `a444f95165d8ecfed6b986fb07a7046fa294434142efb42623357f805225121c` |
| `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentHistoryViewModelTest.kt` | `21af066b70e287e5a16a7c81f5afbe362870918f7070cef49384772d770aa23c` |
| `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentViewModelTest.kt` | `b8c58c635c1e5138d5d4bf807cdc3da8fa9d026bf2a95335b814ec6b7c43836b` |
| `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ReceiptExportCallbackTest.kt` | `2b2edc99f59d596510e1e6373163ab4734a68a74a9d4d8b99d7f17f13b89426e` |
| `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceViewModelTest.kt` | `b16b9a9fdad3da4094f714f5f7113b83ef4779d60121752604296d7be7d38975` |
| `mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/MemberPaymentScreenTest.kt` | `f9720547dc11adf3558c5fdbab598961f2fe5cb71db29e1ca4043fb516933d40` |
| `mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/RecurrenceScreenTest.kt` | `25a723d412dfb9bcd559cefd28ad3d253068740cafed4632345a7f6486d758ed` |

## Evidence log digests

All logs are in `/var/folders/y5/qpphs0gs7558vcgg6pjplblh0000gn/T/saqz-recurring-review.8hlpas_e`; test-result XML is preserved under `evidence/`.

| Log | SHA-256 |
|---|---|
| `M1-ignore-session.log` | `72ef12a59a39ca727e09b2131a16b6f4ac87aa00e39f7d7c60e5c9eab485c5bd` |
| `M2-restore-renewal-post.log` | `5bba653bdebc01d5e8476bbe463d479c62035ca276f36b804167321be764c596` |
| `M3-checkout-confirms-payment.log` | `e7202a4f0a8cf957e71b33ca4b41ca72ca6396b08346e7d1a2f98cfaa6aa2437` |
| `M4-wrong-amount-fee.log` | `5dbf71a97fe035e369cc93020ea121b579c60f76bd5615037c9145d0e288b3b0` |
| `M5-optimistic-export-author.log` | `74f426710275e7bfc2b54974686f16951c90d7d91d5800de22da8b307746eda0` |
| `M5-optimistic-export-discriminant.log` | `766edb66f75982a49f3aed88f84c3a6aaeded99631814841e2e84c16ce2b2c03` |
| `M6-bypass-consent.log` | `34ee98f4d3179e4812a29165d4f0dbd6bdefab35a807ac1ba05f772e4df1b471` |
| `baseline-host.log` | `98250da80f3310f8ce5c6faf2c88f33de79187913dfbaaec82f1aa9e53846701` |
| `closed-money-discriminants.log` | `cd8a080e3a99ca29db5cacae77659ffaa6e6b879f4c76edfef38c25fa25fc5fc` |
| `corrected-host-partial.log` | `8b3c4cefb16058f37c9f799e313568434d255778c63fa13b0cfcb614b40b44d8` |
| `discriminants.log` | `f65e407d57225ec9008766a592410c8d6ced48426993c2d8613ce98e95499dde` |
| `eligibility-discriminant.log` | `cd7554278c4046bf206b8db60b0bc4f41f54a71fe0582ac35d588b4099eb5225` |
| `final-M5-production-callback.log` | `3415aa4a6355eca1da6421ec67cc544b34f93fc1fb09455339bcca504c2aa475` |
| `final-android-quality.log` | `9a44d6465a2a37ad19a395a839d023a5de9a9df174f1756bca1b1311661375e1` |
| `final-platform-gates.log` | `fc1970091d88ca6218f533e3269e43a22808353f2c3c8328364e6df0bb284570` |
| `session-cancel-discriminants.log` | `e5a15ac03e94f55a7f621b96be47bf7f1b1de73241157a593198b42272c6ab14` |
