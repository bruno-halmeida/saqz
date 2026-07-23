# Mobile Presentation and Compose MVI — Tasks

## Execution Protocol (MANDATORY — do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user — do not proceed without it.**

---

**Design**: `.specs/features/mobile-presentation-compose-mvi/design.md`
**Status**: In Progress — Batch 1 (Phases 1-2, T01-T10) complete

---

## Progress Log

### Batch 1 — Phases 1-2 (T01-T10) — ✅ complete
- T01 `f7d4060` feat(core): add MviViewModel MVI base contract
- T02 `01a566a` feat(design-system): add UiText presentation-text abstraction
- T03 `76293b5` feat(design-system): add lifecycle-aware ObserveAsEvents
- T04 `8f59903` refactor(groups): GroupSetupViewModel on MviViewModel without test scope
- T05 `e7d67ba` refactor(groups): ExpenseViewModel on MviViewModel without test scope
- T06 `ae30dca` refactor(groups): FinanceViewModel on MviViewModel without test scope
- T07 `161dbd0` refactor(groups): GameDetailViewModel on MviViewModel without test scope
- T08 `9384c69` refactor(groups): games ViewModels on MviViewModel without test scope
- T09 `abac098` refactor(compose): GroupsNavigationViewModel on MviViewModel
- T10 `b4d7bee` refactor(compose): AccessViewModel on MviViewModel without test scope
- Tests: all green, full gate passed, per-suite floors preserved.
- Deviations: test determinism uses `StandardTestDispatcher` + `runCurrent()` instead of `UnconfinedTestDispatcher` (Unconfined broke existing single-flight assertions); `AccessViewModel`'s derived `combine`/`stateIn` state adapted onto the base via an `onEach { update {} }` collector. Both zero production test-scope surface (PMVI-007/008 intact).

### Batch 2 — Phase 3 (T11-T16) — ✅ complete
- T11 `3391286` refactor(access): error mapping through UiText
- T12 `f1011af` feat(groups): group setup error labels via UiText with restoration corrective-state test
- T13 `72fe387` feat(groups): expense form state reconciled with drafts
- T14 `cb17535` feat(groups): finance form state reconciled with existing draft
- T15 `3a2396e` feat(groups): game detail input state with process restoration
- T16 `572d406` test(groups): game editor restoration corrective-state coverage
- Tests: full gate green (iosSimulatorArm64), +5 new tests, floors preserved.
- Corrections applied (per design/tasks fix): T12/T14/T16 reconcile with existing durable drafts, NO SavedStateHandle (draft key derives from restorable nav args). Only T13 (Expense edit-draft reload key) and T15 (GameDetail — no draft) use `SavedStateHandle.saved()`. Delegate import is `androidx.lifecycle.serialization.saved`; added `lifecycle-viewmodel-savedstate` + kotlin.serialization plugin to `:features:groups` at T13.
- **Open follow-up → folded into T29:** GameDetail's Koin binding (`ComposePresentationModule.kt:81`) builds the VM manually and does NOT pass the real `SavedStateHandle` (Koin 4.2 `viewModel{}` doesn't auto-inject it), so on-device GameDetail restoration won't fire until the binding forwards `savedStateHandle`. VM-level restoration is correct + tested. T29 (GameDetailRoot extraction) must fix this binding.

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec — confirm before Execute. Guidelines found: `README.md` (verification commands §125–142), `scripts/check-gradle` (zero-test detection), existing commonTest suites (VM tests with `runTest`, Compose tests with `runComposeUiTest`, `testTag` semantics).

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| MVI base / core helpers (`MviViewModel`, `UiText`, `ObserveAsEvents`) | unit | All branches; atomic update, effect buffering, lifecycle re-collection edge cases | `mobile/core/*/src/commonTest/**` | `mobile/gradlew -p mobile :core:common:allTests :core:design-system:allTests --console=plain` |
| ViewModels (existing + split-off) | unit | 1:1 to spec ACs per route; every listed edge case (rapid intents, backgrounded effect, restored-invalid input) | `mobile/features/*/src/commonTest/**`, `mobile/compose-app/src/commonTest/**` | `mobile/gradlew -p mobile :features:groups:allTests` / `:features:access:allTests` / `:compose-app:allTests` |
| Root/screen composables | unit (Compose `runComposeUiTest`) | Happy + per materially-distinct state (loading/empty/content/error); enabled-state semantics parity (PMVI-024) | same `commonTest` trees | same per-module `allTests` |
| Gradle/catalog/config changes | none | — (build gate only) | — | build gate only |

**Floor**: existing suites (e.g. `FinanceViewModelTest`, `AuthenticatedAccessRootTest`, `GameEditorScreenTest`) — never less thorough per layer. Test counts must never silently decrease across a migration task.

## Gate Check Commands

> Generated from codebase — confirm before Execute.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | After a task touching one module | `mobile/gradlew -p mobile :<module>:allTests --console=plain` (module = task's Where) |
| Full | After tasks spanning features + compose-app | `mobile/gradlew -p mobile :core:common:allTests :core:design-system:allTests :features:access:allTests :features:groups:allTests :compose-app:allTests --console=plain` |
| Build | Phase completion / final task | `scripts/check-gradle` (repo root; requires adb) |

---

## Execution Plan

Phases are ordered and run sequentially — each phase completes before the next begins, and tasks within a phase execute in order.

### Phase 1: Core foundations (3 tasks)

```
T01 → T02 → T03
```

### Phase 2: Base migration + testScope removal (7 tasks)

```
T04 → T05 → T06 → T07 → T08 → T09 → T10
```

### Phase 3: UiText adoption + form state and restoration (6 tasks)

```
T11 → T12 → T13 → T14 → T15 → T16
```

### Phase 4: Access god-ViewModel split — one route at a time (9 tasks)

```
T17 → T18 → T19 → T20 → T21 → T22 → T23 → T24 → T25
```

### Phase 5: Groups split + god-Root thinning (5 tasks)

```
T26 → T27 → T28 → T29 → T30
```

### Phase 6: Compose polish + closure (3 tasks)

```
T31 → T32 → T33
```

---

## Task Breakdown

### T01: Create MviViewModel base

**What**: `abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel()` — private `MutableStateFlow`/`Channel(BUFFERED)`, public `state`/`effects`, `protected update {}` (atomic) / `emit()`, `abstract fun onIntent(i: I)`. Add `lifecycle-viewmodel` to `:core:common`.
**Where**: `mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/mvi/MviViewModel.kt` (+ `build.gradle.kts`)
**Depends on**: None
**Reuses**: scaffolding shape from `GroupSetupViewModel.kt` (extracted verbatim)
**Requirement**: PMVI-001, PMVI-003, PMVI-006
**Tools**: Skill: `android-presentation-mvi`
**Done when**:
- [ ] Concurrent `update` calls never lose writes (test with parallel intents)
- [ ] Effects buffer while un-collected, deliver once on re-collection
- [ ] Quick gate passes: `:core:common:allTests`
**Tests**: unit | **Gate**: quick
**Commit**: `feat(core): add MviViewModel MVI base contract`

### T02: Create UiText

**What**: `sealed interface UiText` (`Res(StringResource, args)`, `Raw(String)`) + `@Composable fun UiText.asString()`.
**Where**: `mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/text/UiText.kt`
**Depends on**: None
**Reuses**: `compose.components.resources` already in design-system
**Requirement**: PMVI-005
**Tools**: NONE
**Done when**:
- [ ] `asString` resolves Res with args and Raw; Compose test renders both
- [ ] Quick gate passes: `:core:design-system:allTests`
**Tests**: unit (Compose) | **Gate**: quick
**Commit**: `feat(design-system): add UiText presentation-text abstraction`

### T03: Create ObserveAsEvents + lifecycle-runtime-compose

**What**: Catalog entry `lifecycle-runtime-compose` (version.ref `lifecycle`); `@Composable fun <T> ObserveAsEvents(flow, key1, onEvent)` collecting in `repeatOnLifecycle(STARTED)` with `rememberUpdatedState(onEvent)`.
**Where**: `mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/effects/ObserveAsEvents.kt`, `mobile/gradle/libs.versions.toml`, design-system `build.gradle.kts`
**Depends on**: None
**Reuses**: replaces pattern at `AuthenticatedAccessRoot.kt:283/290/315`
**Requirement**: PMVI-006, PMVI-016, PMVI-021
**Tools**: Skill: `android-compose-ui`
**Done when**:
- [ ] Event delivered once across recomposition; callback change does not restart collection
- [ ] No delivery while lifecycle below STARTED (Compose test)
- [ ] Quick gate passes: `:core:design-system:allTests`
**Tests**: unit (Compose) | **Gate**: quick
**Commit**: `feat(design-system): add lifecycle-aware ObserveAsEvents`

### T04: Migrate GroupSetupViewModel to base, drop testScope

**What**: Extend `MviViewModel`; delete `testScope` (constructor + `GroupSetupViewModelParameters.testScope` + Koin binding); tests use `Dispatchers.setMain(UnconfinedTestDispatcher())`.
**Where**: `mobile/features/groups/.../presentation/GroupSetupViewModel.kt` + params + `ComposePresentationModule.kt:70` + test
**Depends on**: T01
**Reuses**: T01 base
**Requirement**: PMVI-003, PMVI-007, PMVI-008
**Tools**: Skill: `android-testing`
**Done when**:
- [ ] No `CoroutineScope` in production constructor surface
- [ ] Existing test count preserved, all green via `setMain`
- [ ] Quick gate passes: `:features:groups:allTests` + `:compose-app:allTests`
**Tests**: unit | **Gate**: quick
**Commit**: `refactor(groups): GroupSetupViewModel on MviViewModel without test scope`

### T05: Migrate ExpenseViewModel to base, drop testScope

**What/Where/Done when**: same shape as T04 for `ExpenseViewModel.kt:34/37` + test.
**Depends on**: T01 | **Requirement**: PMVI-003, PMVI-007, PMVI-008
**Tools**: Skill: `android-testing`
**Tests**: unit | **Gate**: quick (`:features:groups:allTests`)
**Commit**: `refactor(groups): ExpenseViewModel on MviViewModel without test scope`

### T06: Migrate FinanceViewModel to base, drop testScope

**What/Where/Done when**: same shape as T04 for `FinanceViewModel.kt:40/43` + test.
**Depends on**: T01 | **Requirement**: PMVI-003, PMVI-007, PMVI-008
**Tools**: Skill: `android-testing`
**Tests**: unit | **Gate**: quick (`:features:groups:allTests`)
**Commit**: `refactor(groups): FinanceViewModel on MviViewModel without test scope`

### T07: Migrate GameDetailViewModel to base, drop testScope

**What/Where/Done when**: same shape as T04 for `GameDetailViewModel.kt:31/37` + Koin binding `ComposePresentationModule.kt:81` + test.
**Depends on**: T01 | **Requirement**: PMVI-003, PMVI-007, PMVI-008
**Tools**: Skill: `android-testing`
**Tests**: unit | **Gate**: quick (`:features:groups:allTests` + `:compose-app:allTests`)
**Commit**: `refactor(groups): GameDetailViewModel on MviViewModel without test scope`

### T08: Migrate GamesViewModel + GameEditorViewModel to base, drop testScope

**What/Where/Done when**: same shape as T04 for `GamesViewModel.kt:19/21` and `GameEditorViewModel.kt:25/27` + tests (cohesive pair, same games package).
**Depends on**: T01 | **Requirement**: PMVI-003, PMVI-007, PMVI-008
**Tools**: Skill: `android-testing`
**Tests**: unit | **Gate**: quick (`:features:groups:allTests`)
**Commit**: `refactor(groups): games ViewModels on MviViewModel without test scope`

### T09: Migrate GroupsNavigationViewModel to base

**What**: Extend `MviViewModel` (no testScope exists); adapt tests.
**Where**: `mobile/compose-app/.../navigation/GroupsNavigationViewModel.kt` + test
**Depends on**: T01 | **Requirement**: PMVI-001, PMVI-003
**Tools**: NONE
**Tests**: unit | **Gate**: quick (`:compose-app:allTests`)
**Commit**: `refactor(compose): GroupsNavigationViewModel on MviViewModel`

### T10: Migrate AccessViewModel to base, remove testScope overloads

**What**: Extend `MviViewModel`; delete `testScope` overloads (`AccessViewModel.kt:31-45`); tests via `setMain`.
**Where**: `mobile/compose-app/.../navigation/AccessViewModel.kt` + `ComposePresentationModule.kt:64` + tests
**Depends on**: T01 | **Requirement**: PMVI-003, PMVI-007, PMVI-008
**Tools**: Skill: `android-testing`
**Done when**:
- [ ] Single production constructor, no scope params
- [ ] `AuthenticatedAccessRootTest` suite count preserved, green
- [ ] Quick gate passes: `:compose-app:allTests`
**Tests**: unit | **Gate**: quick
**Commit**: `refactor(compose): AccessViewModel on MviViewModel without test scope`

### T11: AuthUiErrorMapper returns UiText

**What**: `messageRes(): StringResource` → `message(): UiText`; update all call sites in access screens; inline `stringResource(when…)` blocks route through the mapper.
**Where**: `mobile/features/access/.../presentation/AuthUiErrorMapper.kt` + consuming screens
**Depends on**: T02 | **Requirement**: PMVI-005
**Tools**: NONE
**Done when**:
- [ ] No raw transport/exception text reachable from screens; mapper unit tests cover every error branch
- [ ] Quick gate passes: `:features:access:allTests`
**Tests**: unit | **Gate**: quick
**Commit**: `refactor(access): error mapping through UiText`

### T12: GroupSetup form state into ViewModel + draft reconciliation

**CORRECTED (found during Batch 2 implementation, 2026-07-23):** `GroupDraftStorePort` (`DraftsModule`) already durably persists this form and already restores it in `GroupSetupViewModel.init` via `restoreDraft()`/`persistDraft()` (`GroupSetupViewModel.kt:44,128-148`), keyed off nav args that navigation itself restores. A competing full-form `saved()` snapshot would violate PMVI-020 (two sources of truth). No `SavedStateHandle` needed here — the existing draft already satisfies PMVI-018.
**What**: Move validation/submission-affecting fields from `GroupSetupScreen.kt:128-130` `remember` into `State` via field intents (PMVI-002), routed through the existing draft-backed VM state; confirm/extend the existing `restoreDraft()` path so restored-but-invalid input shows the normal corrective state and never auto-submits (PMVI-019); error labels (`GroupSetupScreen.kt:438`) → UiText. Purely visual state (sheets) stays local.
**Where**: `GroupSetupViewModel.kt`, `GroupSetupScreen.kt`, tests
**Depends on**: T04, T02 | **Requirement**: PMVI-002, PMVI-019, PMVI-020
**Tools**: Skill: `android-presentation-mvi`
**Done when**:
- [ ] Draft-restored invalid input shows normal validation state, does not auto-submit
- [ ] Test count ≥ current; quick gate passes: `:features:groups:allTests`
**Tests**: unit | **Gate**: quick
**Commit**: `feat(groups): group setup form state reconciled with existing draft`

### T13: Expense form state into ViewModel + draft reconciliation

**What**: Same shape as T12 for `ExpenseScreen.kt:215-220`, reconciled with `ExpenseDraftStorePort`; where the draft key needs a value the draft itself can't supply on cold start, `SavedStateHandle.saved()` carries only that reload identifier (never the full form) — drafts repo stays source of truth (PMVI-020); errors → UiText.
**Where**: `ExpenseViewModel.kt`, `ExpenseScreen.kt`, tests
**Depends on**: T05, T12 | **Requirement**: PMVI-002, PMVI-018, PMVI-020
**Tools**: Skill: `android-presentation-mvi`
**Done when**:
- [ ] With existing draft, restoration loads draft (draft wins over any reload-key snapshot); without draft, form starts empty
- [ ] Quick gate passes: `:features:groups:allTests`
**Tests**: unit | **Gate**: quick
**Commit**: `feat(groups): expense form state reconciled with drafts`

### T14: Finance form state into ViewModel + draft reconciliation

**What/Where**: same shape as T12 for `FinanceScreen.kt:53-56,71` fields → `FinanceViewModel`, reconciled with `MonthlyChargeDraftStorePort` + UiText errors + tests.
**Depends on**: T06, T12 | **Requirement**: PMVI-002, PMVI-019, PMVI-020
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit | **Gate**: quick (`:features:groups:allTests`)
**Commit**: `feat(groups): finance form state reconciled with existing draft`

### T15: GameDetail input state into ViewModel + saved() restoration

**What**: The only one of the five forms with **no durable draft** — `GameDetailViewModel` gets an authoritative `@Serializable GameDetailFormSnapshot` + `savedStateHandle.saved {}` (add `lifecycle-viewmodel-savedstate` catalog entry here, first task that needs it). Move validation/submission-affecting fields from `GameDetailScreen.kt:100-102` `remember` into `State`; restored input re-validates, never auto-submits.
**Where**: `GameDetailViewModel.kt`, `GameDetailScreen.kt`, catalog, groups `build.gradle.kts`, Koin binding, tests
**Depends on**: T07, T12 | **Requirement**: PMVI-002, PMVI-018, PMVI-019
**Tools**: Skill: `android-presentation-mvi`
**Done when**:
- [ ] VM recreated with populated `SavedStateHandle` restores fields; invalid restored input shows normal validation state
- [ ] Quick gate passes: `:features:groups:allTests`
**Tests**: unit | **Gate**: quick
**Commit**: `feat(groups): game detail input state with process restoration`

### T16: GameEditor form state into ViewModel + draft reconciliation

**What/Where**: same shape as T12 for GameEditor weekly-slot form → `GameEditorViewModel`, reconciled with `GameDraftStorePort` + tests (`GameEditorScreenTest` updated).
**Depends on**: T08, T12 | **Requirement**: PMVI-002, PMVI-019, PMVI-020
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit | **Gate**: quick (`:features:groups:allTests`)
**Commit**: `feat(groups): game editor form state reconciled with existing draft`

### T17: Extract LoginViewModel + LoginRoot

**What**: Per-route `LoginViewModel : MviViewModel<LoginState, LoginIntent, LoginEffect>` in `:features:access` wrapping `AuthenticationCoordinator`/`AccessValidators`; `LoginRoot` (koinViewModel + `collectAsStateWithLifecycle` + `ObserveAsEvents` + semantic callbacks); `LoginScreen` narrows to `LoginState`; dispatcher `when` invokes Root; Koin binding; tests migrate from `AuthenticatedAccessRootTest` login coverage. Adds koin-compose + lifecycle-runtime-compose to `:features:access` (first access Root).
**Where**: `mobile/features/access/.../presentation/login/`, `.../ui/LoginRoot.kt`, `LoginScreen.kt`, `AuthenticatedAccessRoot.kt`, Koin module, tests
**Depends on**: T03, T10, T11 | **Requirement**: PMVI-001, PMVI-002, PMVI-009, PMVI-010, PMVI-011, PMVI-016
**Tools**: Skill: `android-presentation-mvi`
**Done when**:
- [ ] Login flow behavior unchanged (existing scenarios green against new VM)
- [ ] `LoginScreen` receives only `LoginState` + callbacks; no DI/nav inside
- [ ] Full gate passes
**Tests**: unit + Compose | **Gate**: full
**Commit**: `refactor(access): login route with dedicated MVI ViewModel and Root`

### T18: Extract RegistrationViewModel + Root, standard restoration

**What**: Same shape as T17 for Registration; replace the manual `restore()`/snapshot at `RegistrationScreen.kt:85` with `saved()` snapshot in the new VM.
**Depends on**: T17 | **Requirement**: PMVI-001, PMVI-009, PMVI-018, PMVI-019
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick (`:features:access:allTests` + `:compose-app:allTests`)
**Commit**: `refactor(access): registration route with dedicated ViewModel and restoration`

### T19: Extract PasswordResetViewModel + Root

**What**: Same shape as T17 for PasswordReset.
**Depends on**: T17 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(access): password reset route with dedicated ViewModel`

### T20: Extract VerificationViewModel + Root

**What**: Same shape as T17 for Verification.
**Depends on**: T17 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(access): verification route with dedicated ViewModel`

### T21: Extract NameCompletionViewModel + Root

**What**: Same shape as T17 for NameCompletion.
**Depends on**: T17 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(access): name completion route with dedicated ViewModel`

### T22: Extract SettingsViewModel + Root

**What**: Same shape as T17 for GroupSettings.
**Depends on**: T17 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(access): settings route with dedicated ViewModel`

### T23: Extract MembershipAdministrationViewModel + Root

**What**: Same shape as T17 for MembershipAdministration (state slice `state.administration` becomes the route State).
**Depends on**: T17 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(access): membership administration route with dedicated ViewModel`

### T24: Extract InviteManagementViewModel + Root

**What**: Same shape as T17 for InviteManagement.
**Depends on**: T17 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(access): invite management route with dedicated ViewModel`

### T25: Slim AccessViewModel to orchestration

**What**: Remove migrated per-route state/intents/effects; retain session/bootstrap/group-selection/destination; Bootstrap/Starting screens keep working; cross-route outcomes (login success → destination) arrive via Root callbacks wired in the dispatcher.
**Where**: `AccessViewModel.kt`, `AuthenticatedAccessRoot.kt`, tests
**Depends on**: T17, T18, T19, T20, T21, T22, T23, T24 | **Requirement**: PMVI-001, PMVI-006
**Tools**: Skill: `android-presentation-mvi`
**Done when**:
- [ ] AccessState contains no per-route form/screen slices
- [ ] All access flows green end-to-end; full gate passes
**Tests**: unit + Compose | **Gate**: full
**Commit**: `refactor(compose): AccessViewModel reduced to access orchestration`

### T26: Extract GroupsListViewModel + Root

**What**: Same shape as T17 for GroupsList (from `GroupsNavigationViewModel`); Root in `:features:groups` (adds koin-compose + lifecycle-runtime-compose if absent).
**Depends on**: T03, T09 | **Requirement**: PMVI-001, PMVI-009, PMVI-010
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick (`:features:groups:allTests` + `:compose-app:allTests`)
**Commit**: `refactor(groups): groups list route with dedicated ViewModel and Root`

### T27: Extract GroupDetailViewModel + Root

**What**: Same shape as T26 for GroupDetail; split its composables out of `GroupsRouteScreens.kt` (1163 lines) into per-screen files while touched.
**Depends on**: T26 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(groups): group detail route with dedicated ViewModel and Root`

### T28: Extract GroupMoreViewModel + Root, slim GroupsNavigationViewModel

**What**: Same shape as T26 for GroupMore; `GroupsNavigationViewModel` retains only groups-destination/selection orchestration.
**Depends on**: T27 | **Requirement**: PMVI-001, PMVI-009
**Tools**: Skill: `android-presentation-mvi`
**Tests**: unit + Compose | **Gate**: quick
**Commit**: `refactor(groups): group more route split and navigation orchestrator slimmed`

### T29: Extract GroupSetupRoot + GameDetailRoot

**What**: Roots for the two already-dedicated VMs; move their `koinViewModel`/collection/effect blocks out of `AuthenticatedAccessRoot.kt` (lines 197/213/290/315); cross-VM rerouting (`GroupSetupEffect.SelectGroup` → `AccessIntent.Selection`) becomes an explicit Root callback. **Also fix the GameDetail Koin binding** (`ComposePresentationModule.kt:81`) so it forwards the real `SavedStateHandle` into the VM (Koin 4.2 manual `viewModel{}` does not auto-inject it) — without this, T15's tested restoration never fires on-device (PMVI-018).
**Where**: `mobile/features/groups/.../ui/GroupSetupRoot.kt`, `GameDetailRoot.kt`, `AuthenticatedAccessRoot.kt`, `ComposePresentationModule.kt`, tests
**Depends on**: T12, T15, T03 | **Requirement**: PMVI-006, PMVI-009, PMVI-011, PMVI-018
**Tools**: Skill: `android-compose-ui`
**Done when**:
- [ ] GameDetail Koin binding forwards `savedStateHandle`; a binding/wiring test asserts the VM receives a real handle
- [ ] Full gate passes
**Tests**: unit + Compose | **Gate**: full
**Commit**: `refactor(groups): group setup and game detail Roots own their ViewModels`

### T30: Thin AuthenticatedAccessRoot

**What**: Move `AccessDestination`/`GroupsDestination` enums to own files; every remaining collection `collectAsStateWithLifecycle`; every effect via `ObserveAsEvents`; file reduced to AccessViewModel acquisition + destination `when` delegating to Roots.
**Where**: `AuthenticatedAccessRoot.kt` + new destination files + tests
**Depends on**: T25, T28, T29 | **Requirement**: PMVI-009, PMVI-016, PMVI-017, PMVI-021
**Tools**: Skill: `android-compose-ui`
**Done when**:
- [ ] No raw `collectAsState`/raw effect `LaunchedEffect` left in compose-app navigation
- [ ] Backgrounded-emission edge case has a test (effect delivered once on return)
- [ ] Full gate passes
**Tests**: unit + Compose | **Gate**: full
**Commit**: `refactor(compose): AuthenticatedAccessRoot reduced to thin dispatch`

### T31: Previews for materially distinct states

**What**: Add missing loading/empty/error/content previews (with `SaqzTheme` wrapper, parameterized/localized error fixtures) for migrated screens; all constructible without app DI.
**Where**: feature screen files
**Depends on**: T30 | **Requirement**: PMVI-012, PMVI-029
**Tools**: Skill: `android-compose-ui`
**Tests**: none (previews; build-verified) | **Gate**: quick (compile via `allTests`)
**Commit**: `feat(mobile): state previews for migrated screens`

### T32: Accessibility and stability audit of migrated controls

**What**: Verify/fix on migrated screens: semantic role/label/enabled parity with click handlers (PMVI-023/024), effect keys correctness, duplicate-work-on-recomposition, stable collection params (PMVI-013), derived state without duplicated sources (PMVI-014); add Compose tests for enabled-state parity where missing.
**Where**: feature UI files + tests
**Depends on**: T30 | **Requirement**: PMVI-013, PMVI-014, PMVI-015, PMVI-023, PMVI-024, PMVI-025, PMVI-026
**Tools**: Skill: `android-compose-ui`
**Tests**: unit + Compose | **Gate**: full
**Commit**: `fix(mobile): accessibility and stability parity on migrated screens`

### T33: Documentation + closure

**What**: README mobile section notes the MVI contract (base, Roots, UiText, restoration); document PMVI-027/028 resolution (CompositionLocals audited theme-only — no migration); run Build gate; spec/design/tasks status → Done.
**Where**: `README.md`, `.specs/features/mobile-presentation-compose-mvi/*`
**Depends on**: T31, T32 | **Requirement**: PMVI-027, PMVI-028
**Tools**: NONE
**Done when**:
- [ ] Build gate passes: `scripts/check-gradle`
- [ ] Docs updated; statuses Done
**Tests**: none (docs/config) | **Gate**: build
**Commit**: `docs(mobile): presentation MVI contract documented and feature closed`

---

## Phase Execution Map

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6

Phase 1:  T01 ──→ T02 ──→ T03
Phase 2:  T04 ──→ T05 ──→ T06 ──→ T07 ──→ T08 ──→ T09 ──→ T10
Phase 3:  T11 ──→ T12 ──→ T13 ──→ T14 ──→ T15 ──→ T16
Phase 4:  T17 ──→ T18 ──→ T19 ──→ T20 ──→ T21 ──→ T22 ──→ T23 ──→ T24 ──→ T25
Phase 5:  T26 ──→ T27 ──→ T28 ──→ T29 ──→ T30
Phase 6:  T31 ──→ T32 ──→ T33
```

Execution is strictly sequential — no intra-phase parallelism.

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T01–T03 | 1 file + its tests each | ✅ Granular |
| T04–T10 | 1 VM migration each (T08: 2 sibling VMs, cohesive) | ✅ Granular |
| T11 | 1 mapper + call sites | ✅ Granular |
| T12–T16 | 1 form screen/VM pair each | ✅ Granular |
| T17–T24 | 1 route (VM + Root + rewire) each | ✅ Granular |
| T25, T28, T30 | 1 orchestrator slimdown each | ✅ Granular |
| T26–T27, T29 | 1–2 Roots each, cohesive | ✅ Granular |
| T31–T33 | 1 cross-cutting concern each | ✅ Granular |

## Diagram-Definition Cross-Check

| Task | Depends On (body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T01–T03 | None | Phase-1 sequence only | ✅ |
| T04–T10 | T01 | Phase 2 after Phase 1 | ✅ |
| T11 | T02 | Phase 3 after Phase 1 | ✅ |
| T12 | T04, T02 | Phase 3 after Phases 1–2 | ✅ |
| T13–T16 | own VM task (T05–T08) + T12 | Phase-3 sequence | ✅ |
| T17 | T03, T10, T11 | Phase 4 after Phases 1–3 | ✅ |
| T18–T24 | T17 | Phase-4 sequence | ✅ |
| T25 | T17–T24 | last in Phase 4 | ✅ |
| T26 | T03, T09 | Phase 5 after earlier phases | ✅ |
| T27, T28 | T26, T27 | Phase-5 sequence | ✅ |
| T29 | T12, T15, T03 | Phase 5 after Phase 3 | ✅ |
| T30 | T25, T28, T29 | last in Phase 5 | ✅ |
| T31, T32 | T30 | Phase 6 after Phase 5 | ✅ |
| T33 | T31, T32 | last | ✅ |

No forward dependencies; all arrows point backward. ✅

## Test Co-location Validation

| Task | Layer | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T01–T03 | core helpers | unit | unit | ✅ |
| T04–T10 | ViewModel | unit | unit | ✅ |
| T11 | presentation mapper | unit | unit | ✅ |
| T12–T16 | ViewModel + screen | unit | unit | ✅ |
| T17–T29 | ViewModel + Root/screen | unit + Compose | unit + Compose | ✅ |
| T30 | Root/dispatch | unit + Compose | unit + Compose | ✅ |
| T31 | previews | none (build gate) | none | ✅ |
| T32 | screen composables | Compose | unit + Compose | ✅ |
| T33 | docs/config | none | none (build gate) | ✅ |

No violations. ✅
