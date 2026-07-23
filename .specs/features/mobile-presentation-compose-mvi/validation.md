# Mobile Presentation and Compose MVI Validation

**Date**: 2026-07-23
**Spec**: `.specs/features/mobile-presentation-compose-mvi/spec.md`
**Diff range**: `f7d4060^..HEAD` (30 commits)
**Verifier**: independent sub-agent (author ≠ verifier); read-only on the real tree — all sensor mutations applied in-place then reverted with `git checkout`.

---

## Verdict: PASS ✅

Delivered scope validated per the orchestrator's brief. Deferred items (T22–T24 Settings/Memberships/Invite, T26–T28 GroupsList/Detail/More, T30 god-Root thinning, GroupSetupRoot/GameDetailRoot extraction) are OUT OF SCOPE per AD-025/AD-029 (Nav3) and were NOT treated as gaps.

---

## Task Completion (in-scope)

| Task | Status | Notes |
| ---- | ------ | ----- |
| T01 MviViewModel base | ✅ Done | atomic `update`, buffered `Channel`, `receiveAsFlow` |
| T02 UiText | ✅ Done | `Res`/`Raw` + `asString()` |
| T03 ObserveAsEvents | ✅ Done | `repeatOnLifecycle(STARTED)` + `rememberUpdatedState` |
| T04–T10 8 VM base migration, testScope removal | ✅ Done | zero `testScope` in production |
| T11 UiText error mapping | ✅ Done | all branches |
| T12–T16 form state + restoration | ✅ Done | drafts reconciled; GameDetail `saved()` |
| T17–T21 5 pre-auth routes + Roots | ✅ Done | per-route VM + Root + `collectAsStateWithLifecycle` |
| T29 (partial) GameDetail SavedStateHandle forwarding | ✅ Done | `cfe19b3` |

---

## Spec-Anchored Acceptance Criteria

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| PMVI-001 typed State/Intent/Effect per route | contract types exist per route | `login/LoginContract.kt`, `registration/…`, `passwordreset/…`, `verification/…`, `namecompletion/…`; exercised by each `*ViewModelTest` | ✅ |
| PMVI-002 UI dispatches typed intent, no direct repo | intent forwarded to one entry point | `LoginViewModelTest.kt:61` — `assertEquals(listOf(LoginCall(...)), auth.logins)`; `NameCompletionViewModelTest.kt:90` — `assertNull(fixture.auth.updatedName)` on invalid | ✅ |
| PMVI-003 atomic immutable update | concurrent updates never lose writes | `MviViewModelTest.kt:32` — `assertEquals(10_000, viewModel.state.value)` | ✅ |
| PMVI-004 explicit loading/content/error states | states surfaced in State | `GameDetailViewModelTest.kt:269-271` — `assertFalse(isAttendanceMutating)`, `assertNull(attendanceError)`; `ExpenseViewModelTest.kt` load/error suite | ✅ |
| PMVI-005 UiText abstraction, no raw transport text | localized `UiText.Res` per error | `AuthUiErrorMapperTest.kt:19-25` — `assertEquals(UiText.Res(...), AuthUiError.X.message())` all branches; `LoginViewModelTest.kt:84` | ✅ |
| PMVI-006 one-off effect single owner, no dup | buffer then deliver once | `MviViewModelTest.kt:44` — `assertEquals(listOf("a","b"), delivered)`; `ObserveAsEventsTest.kt:78` — not redelivered on recomposition | ✅ |
| PMVI-007 constructor-provided deps, no test scope | no test-scope surface | grep: zero `testScope` in `commonMain`; migrated VMs use `viewModelScope`; tests via `Dispatchers.setMain` | ✅ |
| PMVI-008 tests use injected dispatcher, not prod scope | `setMain` in tests | `LoginViewModelTest.kt:35` — `Dispatchers.setMain(mainDispatcher)`; all VM tests | ✅ |
| PMVI-009 Root acquires VM, lifecycle collect, effects | Root owns DI+collection | `LoginRoot.kt:11` — `collectAsStateWithLifecycle()` + `koinViewModel()` | ✅ |
| PMVI-010 Screen receives state+callbacks only | no DI/nav in Screen | `LoginRoot.kt:12` — `LoginScreen(state, onIntent)`; `LoginScreenTest.kt` renders from state | ✅ |
| PMVI-011 semantic callback for cross-feature | callback not owned mechanism | Root callback surfaces (auth Roots); `LoginViewModelTest.kt:92` nav switches shared screen | ✅ |
| PMVI-016 lifecycle-aware collection | `collectAsStateWithLifecycle`/`repeatOnLifecycle` | `LoginRoot.kt:11`; `ObserveAsEventsTest.kt:43` — no delivery below STARTED | ✅ |
| PMVI-018 process restoration of essential input | restored after death | `RegistrationViewModelTest.kt:120-122`; `GameDetailViewModelTest.kt:265-267` — `assertEquals("member-9", overrideMemberId)` | ✅ |
| PMVI-019 restored-invalid shows corrective, no auto-submit | corrective state, no request | `GroupSetupViewModelTest.kt:184,192` — `assertTrue(creates.isEmpty())`; `RegistrationViewModelTest.kt:143-145`; `NameCompletionViewModelTest.kt:88-90` | ✅ |
| PMVI-020 reconcile with durable draft, no competing truth | draft wins; foreign/old discarded | `ExpenseViewModelTest.kt:110` — `assertNull(draft)` on foreign; `:85` matching restore; `GroupSetupViewModelTest.kt:183` | ✅ |
| PMVI-021 route-scoped effects cancelled, no stale target | lifecycle-scoped collect | `ObserveAsEventsTest.kt:30` (STARTED gating) — killed by sensor #4 | ✅ |

**Status**: ✅ All in-scope ACs covered with `file:line` + spec-matching assertions. No spec-precision gaps flagged.

Note: PMVI-012–015, 022–029 (previews/accessibility/CompositionLocal audit — T31/T32/T33) are folded into the Verifier pass per tasks.md and remain verification/gap-fill items scheduled at closure; the migrated screens already carry `@Preview` + `testTag`/semantics from prior features. Not gaps for the delivered MVI scope.

---

## Discrimination Sensor

| # | File:line | Mutation | Killed? |
| - | --------- | -------- | ------- |
| 1 | `core/common/.../MviViewModel.kt:20` | atomic `mutableState.update{}` → non-atomic `value = transform(value)` | ✅ `MviViewModelTest.concurrent updates never lose writes` FAILED |
| 2 | `access/.../AuthUiErrorMapper.kt:15` | `INVALID_CREDENTIALS` → `UiText.Raw` instead of `Res` | ✅ `AuthUiErrorMapperTest` + `LoginViewModelTest.invalid credentials` FAILED |
| 3 | `groups/.../GameDetailViewModel.kt:46-48` | ignore `inputSnapshot` on restore (blank fields) | ✅ `GameDetailViewModelTest.organizer input restores…` FAILED |
| 4 | `design-system/.../ObserveAsEvents.kt:23` | drop `repeatOnLifecycle(STARTED)` gating | ✅ `ObserveAsEventsTest.no delivery below STARTED…` FAILED |
| 5 | `access/.../RegistrationViewModel.kt:27-28` | swap name/email on restore | ✅ 2 `RegistrationViewModelTest` restoration tests FAILED |
| 6 | `groups/.../ExpenseViewModel.kt:68` | `takeIf{schema&&group}` → `takeIf{true}` (accept foreign draft) | ✅ `ExpenseViewModelTest.foreign or old draft is discarded` FAILED |

**Sensor depth**: lightweight (6 behavior-level mutations across MVI base, UiText, lifecycle effect, and all three restoration/reconciliation modes — auth `saved()`, GameDetail `saved()`, drafts reconcile).
**Result**: 6/6 killed — PASS ✅. All mutations reverted; real tree confirmed clean.

---

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code (no speculative abstraction) | ✅ MVI base is 28 lines, no unused generality |
| Surgical changes / matches patterns | ✅ per-route VMs mirror one template |
| Spec-anchored outcome check (asserted values match spec) | ✅ |
| Per-layer coverage (VM 1:1 ACs; restoration edge cases) | ✅ rapid-intent, backgrounded-effect, restored-invalid all covered |
| Every test maps to a spec requirement | ✅ |
| Documented guidelines followed | ✅ `android-presentation-mvi` / `android-testing` skill patterns; README verification commands |

---

## Edge Cases

- [x] One-off effect while backgrounded/removed — `ObserveAsEventsTest.kt:30` (buffer, deliver on STARTED)
- [x] Process recreation mid multi-step form — Registration/GameDetail restoration tests
- [x] Restored input fails current validation — corrective-state tests (PMVI-019 row)
- [x] Rapid intents race with completion — existing single-flight guards preserved (`isMutating`/`isLoading` checks)
- [x] Callback changes while long-lived effect active — `ObserveAsEventsTest.kt:77` (no restart)
- [ ] List reorder with focus / Android↔iOS lifecycle divergence — deferred to T32 accessibility pass (out of delivered scope)

---

## Gate Check

- **Command**: `mobile/gradlew -p mobile :core:common:allTests :core:design-system:allTests :features:access:allTests :features:groups:allTests :compose-app:allTests --console=plain`
- **Result**: BUILD SUCCESSFUL (exit 0), 0 failed, 0 skipped
- **Per-module counts (from `--rerun-tasks` sensor runs)**: core:common 48, design-system 132, access 114, groups 448 (all green at baseline; each sensor run reproduced the count with exactly the injected failure)
- **Delta**: +5 restoration/corrective tests in Batch 2/3 + per-route VM suites (LoginVM 5, RegistrationVM 7, PasswordResetVM, VerificationVM, NameCompletionVM); no test count decreased.

---

## Requirement Traceability Update

| Requirement | Previous | New |
| --- | --- | --- |
| PMVI-001–011, 016, 018–021 (delivered scope) | Implementing | ✅ Verified |
| PMVI-012–015, 017, 022–029 | Implementing | ⏳ Scheduled (T31/T32/T33 closure + AD-029 Nav3 for deferred routes) |

---

## Summary

**Overall**: ✅ Ready (delivered scope)
**Spec-anchored check**: 16/16 in-scope ACs matched spec outcome; 0 spec-precision gaps
**Sensor**: 6/6 mutations killed
**Gate**: 5 modules green, 0 failed

**What works**: MVI base guarantees atomic state + buffered single-delivery effects; UiText fully localizes every auth error; all three restoration modes (auth `SavedStateHandle.saved()`, GameDetail `saved()`, durable-draft reconciliation) restore essential input, reconcile with the durable source, and never auto-submit stale/invalid data; 5 pre-auth routes render through lifecycle-aware Roots with pure state-only Screens; GameDetail Koin binding forwards the real `SavedStateHandle`.

**Issues found**: none blocking. Non-blocking: PMVI-012/022–029 (previews/accessibility/CompositionLocal audit) are the closure tasks T31–T33, and PMVI-001 for the 6 deferred panels awaits AD-029/Nav3 — both intentionally out of this validation's scope.

**Next steps**: proceed to closure tasks (T31–T33) and Nav3 (AD-029) for the deferred route promotions.
