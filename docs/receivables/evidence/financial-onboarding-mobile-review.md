# Independent verification — financial onboarding mobile

Date: 2026-09-13. Verdict: **PASS for the specified implementation wave**, with the native/real-provider limits below. Spec: `docs/receivables/financial-onboarding-mobile.md`, FO1–FO7. Reviewed delta: **24fd5bc4..19fba8f4**. Independent verifier did not author implementation or tests. Commit f56f2432 is an ancestor of19fba8f4 and is included in this range; it only wraps existing Android test lines. Supplemental03e6f2e3 is separately reviewed in the appendix.

## Isolation and execution

The verifier read `mobile/AGENTS.md`, the spec, and the TLC verification instructions. All executable work and ten behavior mutations ran in `/tmp/saqz-independent-onboarding`, created using `git archive 19fba8f4`; only Android SDK location was copied into scratch `local.properties`. No real source/test was edited, no worktree/stash/commit/push was used, no real Asaas or contact was accessed. The two user-owned untracked `.specs/features/recebimentos-asaas/{context,direcionamento}.md` were not read or changed. This report is the only verifier write in the real source tree.

Independent passing gates (JDK21; backend uses local Colima Postgres and mock provider):

| Command, relative to scratch root | Executed evidence | Result |
| --- | --- | --- |
| `mobile/gradlew -p mobile :features:receivables:presentation:testAndroidHostTest --tests '*FinancialOnboardingViewModelTest' :features:receivables:data:testAndroidHostTest --tests '*KtorFinancialOnboardingGatewayTest'` | `/tmp/saqz-independent-onboarding-baseline.log`; both test tasks executed | 11 VM +9 gateway, zero failures/skips |
| `backend/gradlew -p backend :features:receivables:integrationTest --tests '*FinancialConditionsIntegrationTest' --tests '*FinancialOnboardingIntegrationTest' :bootstrap:test --tests '*BearerSecurityIntegrationTest' :architecture-tests:test --rerun-tasks` | `/tmp/saqz-independent-onboarding-backend-executed.log`; 32 Gradle tasks executed, BUILD SUCCESSFUL | 4 conditions +10 onboarding +8 security +20 architecture =42; zero failures/errors/skips |
| `mobile/gradlew -p mobile :features:receivables:presentation:testAndroidHostTest --tests '*FinancialOnboardingViewModelTest' :features:receivables:data:testAndroidHostTest :android-app:testDevDebugUnitTest --tests '*FinancialOnboarding*Test' --tests '*ReceiptDocumentPickerTest'` | `/tmp/saqz-independent-onboarding-final-mobile.log`; data and Android test tasks executed, VM restored from cache | data39 +Android6 +VM11, zero failures/skips |
| Restored VM command above with `--rerun` on its test task | `/tmp/saqz-independent-onboarding-restored-vm.log`; test task executed | 11 VM, zero failures/skips |
| Restored receivables backend integration command above with `--rerun` on its test task | `/tmp/saqz-independent-onboarding-restored-backend.log`; test task executed | 14, zero failures/skips |

XML results live under scratch modules' `build/test-results`. The first backend attempt used nonexistent `:bootstrap:integrationTest` and stopped at task selection; it was corrected to `:bootstrap:test` before the executed gate. This invocation error is not counted as a passing test or mutation kill. Security has two existing emulator-tagged tests excluded by the normal test task, not newly skipped tests. No Firebase emulator or authenticated real-provider walkthrough was performed.

Changed test files contain 64 `@Test` declarations at base and 97 at review head (+33). No tests were removed or disabled; old Android screenshot/back edits preserve assertions and only wrap lines. Existing DI/profile/navigation tests gained the new route/port/action. New test bodies map to FO1–FO7 below; no unrelated new behavior tests were found.

## Acceptance proof

Aliases below are exact repo-relative paths, so each alias plus line is a file:line citation:

- **Conditions** = `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/FinancialConditionsIntegrationTest.kt`
- **Backend** = `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/FinancialOnboardingIntegrationTest.kt`
- **Security** = `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/BearerSecurityIntegrationTest.kt`
- **Gateway** = `mobile/features/receivables/data/src/commonTest/kotlin/br/com/saqz/receivables/data/KtorFinancialOnboardingGatewayTest.kt`
- **VM** = `mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingViewModelTest.kt`
- **UI** = `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FinancialOnboardingScreenshotTest.kt`
- **Back** = `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FinancialOnboardingBackTest.kt`
- **Picker** = `mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/ReceiptDocumentPickerTest.kt`

| AC / expected outcome | Exact assertion evidence | Verdict |
| --- | --- | --- |
| FO1: missing current terms is 404; no fabricated terms | Conditions:103 `assertEquals(404, missing.status)`; :104 `assertNull(f.conditions.currentTerms(at))` | PASS |
| FO1: future/unpublished excluded, effective/published/version deterministic order | Conditions:108 `assertEquals("v2", f.conditions.currentTerms(at)?.version)`; :114 `assertEquals("v4", body["value"]["version"].asText())` after the competing fixtures at :105–110 | PASS |
| FO1: actual content/hash and historical access; discovery writes nothing | Conditions:115 `assertEquals("Test conditions only", body["value"]["content"].asText())`; :116 exact 64-character hash; :118 `assertEquals("v1", f.conditions.terms("v1", at)?.version)`; :120 `assertEquals(0L, f.jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single())` | PASS |
| FO1/2: both new routes require session | Security:62–64 call `assertUnauthorized` on GET terms and POST recover; helper :116 `assertEquals(401, response.statusCode())`, :119 authentication-required code | PASS |
| FO2: missing/foreign holder is 404/NOT_FOUND | Backend:173 `assertEquals(FinancialResult.Failure(FinancialError.NOT_FOUND, recovery.requestId), service.recover(recovery, now))`; :184 same assertion for foreign actor; :217 HTTP404 | PASS |
| FO2: recover existing operation; UNKNOWN never creates again | Backend:179 `assertEquals(OperationStatus.UNKNOWN, store.creationOperation(account.id).status)`; :180 same account/request result; :181 `assertEquals(operation.id, store.creationOperation(account.id).id)`; :182 `assertEquals(1, server.requestCount); assertNull(store.credentials(account.id))` | PASS |
| FO2: exact recovery response and no sensitive fields/cache | Backend:210 status200/no-store; :211 exact request/account IDs; :212 actor/UNDER_REVIEW; :213 operationsfalse; :214 `assertEquals(setOf("id", "ownerUserId", "registration", "newOperationsEnabled"), json["value"].fieldNames().asSequence().toSet())` | PASS |
| FO2/4: cut blocks new business while existing account stays accessible | Backend:162 provider unavailable, :165 exact existing ID, :168 zero provider requests; VM:29 existing account; :30 `assertEquals(0, f.availabilityReads); assertEquals(0, f.termReads)` | PASS |
| FO3: actor and read absence validated independently from errors | Gateway:20 Success(account); :21 `assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.mine("foreign"))`; :22 Success(null) only404; :23 NETWORK | PASS |
| FO3: exact PF/PJ data, income cents, dates, required fields | VM:34 `assertEquals(250001L, registration.incomeCents)` and exact ISO date; :36 every required field missing gives null; :37 invalid money cases; :38 exact 123456789012L; :39 invalid date null; :43 exact PJ type/cents and null birth | PASS |
| FO3: genuine nonempty terms and explicit consent; edits invalidate it | Gateway:30 exact terms; :32 blank version/content INVALID; VM:48 `assertTrue(f.commands.isEmpty())` before consent; :52 exact version/accepted; :63/64 `assertFalse(vm.state.value.accepted)` after edit/refresh; UI:40–43 independently tests null terms, blank version, blank content | PASS |
| FO3: payload snapshot and correlated write envelopes | Gateway:43 `assertEquals(2, bodies.size); assertEquals(bodies[0], bodies[1])`; :47 literal expected JSON includes incomeCents250001; :52 exact recovery-only JSON; :56/58/59 foreign/correlation violations UNCERTAIN | PASS |
| FO3/6: multipart actual bytes, typed invalid/lost response and no blind upload retry | Gateway:81/82 exact path/type/request; :83 auth; :85/86 field/MIME/PDF bytes; :89 success and calls1; :97/101 `assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.upload(document, "request", file)); assertEquals(1, calls)` | PASS |
| FO4: failed own-account lookup never offers new registration | VM:25 `assertFalse(failed.state.value.discovered); assertFalse(failed.state.value.canEdit); assertEquals(ReceiptError.NETWORK, failed.state.value.error)`; UI:51 absent empty-account claim | PASS |
| FO4: account approval does not activate groups, explicit status labels | VM:55 `assertFalse(vm.state.value.account!!.operationsEnabled)`; UI:76 UNDER_REVIEW label, :83–88 exact INCOMPLETE/CORRECTION_REQUIRED/APPROVED/REJECTED labels | PASS |
| FO5: marker precedes write; only nonsensitive attempt metadata persists | VM:49 `assertNotNull(saved.get<String>("onboarding.attempt"))` in pre-write hook; :71 exact CREATE marker; :72 no CPF/name; :114 exact UPLOAD actor/request/kind/doc/status | PASS |
| FO5: pending blocks edits/new create; replay exact command only after own read | VM:74 commands1 and unchanged name; :76 marker retained on failed read; :78 `assertTrue(f.mineReads > reads); assertSame(f.commands[0], f.commands[1])` | PASS |
| FO5: restoration has no PII/recreation; discovered account ends CREATE | VM:80 `assertEquals(OnboardingForm(), restored.state.value.form)`; :81 commands2 and pending; :83 pendingfalse, exact account, commands still2 | PASS |
| FO5: explicit recovery reuses request; never invokes create | VM:88 recoveries initially empty; :91 unchanged marker after refresh; :92 `assertEquals(f.recoveries[0], f.recoveries[1]); assertTrue(f.commands.isEmpty())` | PASS |
| FO5: logout discards delayed response, PII, picker result, foreign marker and effect | VM:139 foreign marker cleared; :146 account null/form empty/marker null after delayed create; :150 `assertNull(vm3.state.value.selectedFile); assertEquals(ReceiptError.SIGNED_OUT, vm3.state.value.error); assertTrue(f3.pickCancelled)`; :129/:132 stale effect invalid | PASS |
| FO6: explicit selection then confirmation; cancellation never uploads | VM:99 null selection/empty uploads on cancellation; :101 selection same and uploads empty; :103 exact document and bytes; UI:69 only ChooseFile; :74 only explicit Upload | PASS |
| FO6: submission is not approval; remote status consulted | VM:104 selectedFile null/uploadedtrue; :105 UNDER_REVIEW; :106 PENDING retained; UI:77 exact sent-for-analysis wording | PASS |
| FO6: old PENDING cannot resolve uncertain upload or allow duplicate | VM:116 uploads1 and exact retained marker; :118 restoration pending/no bytes; :120 changed AWAITING_APPROVAL clears pending; :121 uploads still1 | PASS |
| FO6: valid hosted Asaas URL uses browser, not API | Gateway:68–76 unsafe URLs and duplicates INVALID, valid sandbox link preserved; VM:126 picks0; :128 exact Open effect; :129 refresh invalidates it; :134 malicious URL/user-info rejected | PASS |
| FO6: PDF/JPEG/PNG up to inclusive5MiB; native cancellation no old callback | Picker:28 `assertEquals(mime, file.contentType); assertArrayEquals(bytes, file.bytes)` for each MIME at limit; :32 illegal argument empty/oversize/type; :43 OPEN_DOCUMENT; :49 cancelled callback absent; :53 exact Invalid/Cancelled sequence | PASS for Android; iOS source/build reviewed |
| FO7: shared terms/amount/form/state UI and pending native Back | UI:31 exact formatted R$2500.01 display; :34 disabled Create without consent; :49/51/53 loading/error/unavailable; :56–60 pending CTA/blocked Back; Back:50 `assertEquals(0, returned); assertEquals(marker, saved.get<String>("onboarding.attempt"))`; :57 changed1; :60 returned1 | PASS |
| FO4/7: permanent Profile entry dispatches callback | `mobile/features/profile/presentation/src/commonTest/kotlin/br/com/saqz/profile/presentation/own/ui/OwnProfileRootTest.kt:38` `assertEquals(1, opens)` | PASS (author iOS execution, source independently reviewed) |
| FO7: route restores, logout removes journey | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/MemberPaymentNavigationTest.kt:22` `assertEquals(stack.toList(), restored.toList())`; :24 `assertEquals(listOf(AccessRoute.Login), restored.toList())` | PASS (author iOS execution, source independently reviewed) |
| FO7: real Koin platform picker instance | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/SaqzKoinBootstrapTest.kt:51` `kotlin.test.assertSame(dependencies.financialDocuments, koin.get<br.com.saqz.receivables.domain.port.ReceiptDocumentPicker>())`; `di/SaqzKoinModulesTest.kt:292–294` resolves gateway/picker/VM | PASS (author Android+iOS execution, source independently reviewed) |

All seven criteria have exact behavioral proof. No spec-precision gap was found within this wave; real terms/provider qualification and remote correction/delegation are explicitly excluded by the spec.

## Discrimination sensor — financial risk depth

Each mutant was injected alone in scratch, compiled, executed against the unchanged tests, and restored in a `finally` block. Failure XML was copied before the next run. No compilation failure is counted as a kill. Ten killed, zero survived, zero inconclusive.

Source aliases: **State** `mobile/features/receivables/presentation/src/commonMain/kotlin/br/com/saqz/receivables/presentation/FinancialOnboardingContract.kt`; **Validation** sibling `OnboardingValidation.kt`; **ViewModel** sibling `FinancialOnboardingViewModel.kt`; **HttpGateway** `mobile/features/receivables/data/src/commonMain/kotlin/br/com/saqz/receivables/data/KtorFinancialOnboardingGateway.kt`.

| ID | Behavior injected at original source line | Test detection (literal failed outcome) | Result |
| --- | --- | --- | --- |
| M1-consent | State:24 removed `accepted` from canCreate | VM `onlyExplicitConsentSubmitsExactFormAndMarkerPrecedesNetwork`: expected commands empty before consent, false | KILLED |
| M2-cents | Validation:28 units multiplied by10 instead of100 | VM `exactPfPjMoneyDatesAndRequiredFieldsAreValidatedWithoutFloatingPoint`: expected250001, actual25001 | KILLED |
| M3-actor | HttpGateway:94 replaced owner equality with nonblank actor | Gateway own-account test expected INVALID, actual foreign-account Success; recover expected UNCERTAIN, actual Success | KILLED |
| M4-uncertainty | ViewModel:194 clears attempt on all errors | VM recovery request equality fails (different UUID); uncertain create/upload expected persisted marker now null | KILLED |
| M5-lifecycle | ViewModel:202 removed validSession from generation guard | VM delayed-create test expected null account after logout, actual account; queued-link test expected stale effect false | KILLED |
| M6-upload-retry | HttpGateway:56 wrapped upload in IdempotentWrite retry | Gateway pending/malformed/lost upload test expected calls1, actual4 | KILLED |
| M7-old-status | ViewModel:106 changed document status comparison from none to any | VM uncertain-upload test expected original UPLOAD/PENDING marker, actual null | KILLED |
| M8-snapshot | ViewModel:76 reconstructs replay command with a new request UUID | VM uncertain-create test `assertSame(f.commands[0], f.commands[1])` fails | KILLED |
| M9-current-terms | `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcFinancialConditions.kt:33` effective order changed DESC to ASC | Conditions deterministic terms test expected v2, actual v1 | KILLED |
| M10-recover | `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/FinancialOnboarding.kt:97` removed provision call | Backend recovery HTTP expected UNDER_REVIEW, actual INCOMPLETE; recovery operation expected UNKNOWN, actual READY | KILLED |

Reproduction scripts: `/tmp/saqz-onboarding-sensors.py`, `/tmp/saqz-onboarding-backend-sensors.py`. Run records: `/tmp/saqz-independent-{mobile,backend}-sensors.json`. Per mutant log and XML: `/tmp/saqz-independent-<ID>.log` and `/tmp/saqz-independent-<ID>-xml`. These are local review artifacts, not committed product files. Source restoration was followed by the passing VM11/backend14 gates above. This is targeted manual fault injection, not exhaustive mutation coverage.

## Boundary and quality review

- Backend current terms query uses publication/effective bounds and all three sort keys. Historical route stays intact. Recovery resolves the session actor, finds only its account and runs only the recorded create operation through existing safe provision behavior. Controller result helper applies no-store uniformly to registration/documents/recovery.
- Gateway validates IDs, owner, enum, terms, document shape and write correlation. Income is Long cents all the way to exact JSON. The typed multipart method shares the existing bounded transport and actually decodes the envelope. Authentication can retry after explicit401; upload has no transport retry for lost/503 responses. CREATE/RECOVER transport retries keep the same request and payload.
- Android port reads on IO with inclusive5MiB bound, registers OpenDocument before launch, and suppresses cancelled callbacks. iOS uses single selection, security-scoped URL access with balanced release, bounded in-memory FileHandle read, and controller identity checks so stale cancellation/results cannot target a newer picker. No document bytes are persisted. Swift main-thread file processing may affect responsiveness for slow file providers; no measured UX failure was demonstrated, and it is not represented as a passed native walkthrough.
- Shared Root/Screen, string resources, DS tokens, previews/tags, scalar serializable route and Koin follow `mobile/AGENTS.md`. PII is held in the controlled form/command only; SavedState stores nonsensitive attempt metadata. Account registration has no group activation dependency or action. Profile entry is in the general account card. Navigation refreshes the receipts directory both on change and return.
- Inspected author PNGs `termos.png`, `confirmar-documento.png`, `incerto.png` under `mobile/build/reports/financial-onboarding`: exact amount/terms/unchecked consent, explicit upload confirmation, and pending recovery language were legible and consistent with the state tests. Independent Android UI tests executed; Roborazzi recording was not enabled in this rerun, so these inspected images are author-generated artifacts.
- Scope is appropriate. No source/test defect, weakened assertion, unsupported action, unnecessary framework, or financial contract regression was confirmed. No fix task or reusable failure lesson is justified from this clean run; therefore no lesson file was written.

## Limits and supporting author evidence

Author logs inspected for successful gates: `/tmp/saqz-onboarding-{data,native-final,full-ui,visual,android-final,ui-build}.log`; `/tmp/saqz-onboarding-xcode.log` ends with `BUILD SUCCEEDED`. Author reports Android+iOS data39 each, iOS network94, presentation66/70, profile12/36, DI8/9, and full/targeted detekt gates. Those broader counts were not all re-executed by this verifier and are supplementary, distinct from the independent counts above.

iOS shared UI tests and framework/Xcode compilation support integration, but do not prove a physical/native document-picker walkthrough, slow cloud-provider behavior, or real Asaas onboarding. No interactive user UAT, real credential recovery, actual published business terms, remote cadastral correction, delegation, or provider homologation is claimed. The legacy Android instrumented corrections are outside this delta and covered separately in the appendix. These limits do not turn a surviving or inconclusive mutant into PASS: there were none.


## Appendix — separate legacy Android test correction

Range **19fba8f4..03e6f2e3** was independently inspected after the primary review. It changes only `mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/AndroidAuthenticatedLifecycleTest.kt` and `docs/receivables/evidence/android-legacy-lifecycle-fix.md`; financial product source is unchanged. Appendix verdict: **PASS, test-contract alignment verified**.

- Registration test now targets current `login-create-account`, `register-name/email/phone/password`, `register-submit` tags and editable descendants, fills the required phone, and scrolls explicitly. Both existing `assertEquals(1, state.auth.registrationCalls)` assertions remain before/after recreation; submit reachability remains. This strengthens fixture validity without weakening single-flight behavior.
- The unverified-session test now asserts the current session bootstrap contract. Independent source basis: `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/VerifiedSessionCoordinator.kt:608` documents removal of the email gate; :645–661 routes a named user to Bootstrapping and invokes bootstrap regardless of email verification; :681–691 maps the non-authentication bootstrap failure to BootstrapError. Existing `mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/presentation/VerifiedSessionCoordinatorTest.kt:108` accepts an unverified identity, :114 asserts `assertIs<SessionAccessState.Ready>(fixture.machine.state.value)` and :115 `assertEquals(1, fixture.session.calls)` when its fake bootstrap succeeds.
- The instrumented fixture specifically returns `TokenResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE)` from `idToken` at `AndroidAuthenticatedLifecycleTest.kt:425–426`; therefore BootstrapError is the precise expected state here. The changed test waits for this state, asserts its error text and login absence, repeats the state/error/login assertions after recreation, and adds compositions1/observeCalls1. It does not misrepresent this token-refusal fixture as a successful authenticated bootstrap.
- No test was removed or ignored: the edited file retains11 `@Test` declarations. The only removed screen expectation was the obsolete `identity-verify` destination; the replacement asserts a specific currently reachable state, not generic screen existence.
- **Author execution, independently inspected:** `/tmp/saqz-finalize-android-full.log` says `Finished 44 tests on Saqz_API_30(AVD) - 11` and `BUILD SUCCESSFUL`. Parsed `mobile/android-app/build/outputs/androidTest-results/connected/debug/flavors/dev/TEST-Saqz_API_30(AVD) - 11-_android-app-dev.xml`: tests44, failures0, errors0, skipped0. Both corrected test names have successful testcase records (2.234s registration;0.828s unverified restoration). This verifier did not rerun the44 instrumented cases; this is artifact inspection, distinct from the independent scratch onboarding gates.

No additional financial mutation or exact-head claim is made for this appendix. The primary10-mutant result remains scoped to19fba8f4.
