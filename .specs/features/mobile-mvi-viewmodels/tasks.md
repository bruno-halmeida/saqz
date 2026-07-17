# Mobile MVI ViewModels Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name
and follow its Execute flow and Critical Rules.** If the skill cannot be
activated, stop before implementation.

**Design:** `.specs/features/mobile-mvi-viewmodels/design.md`
**Status:** In Progress

## Test Coverage Matrix

> Generated from `AGENTS.md`, the accepted authentication/access spec, existing
> KMP reducer/Compose/lifecycle tests, and repository gates. Existing tests are
> the style/depth floor; every MVI acceptance criterion and listed edge case is
> mapped explicitly.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Access state machines | common unit | All intent branches, guards, errors, stale-result behavior; existing scenario count never decreases | `mobile/features/access/src/commonTest/**/presentation/*Test.kt` | `rtk mobile/gradlew -p mobile :features:access:allTests --console=plain` |
| Access route ViewModel | common unit | MVI-02..04, MVI-07, state reconciliation, native-effect result, close-once behavior | `mobile/compose-app/src/commonTest/**/navigation/AccessViewModelTest.kt` | `rtk mobile/gradlew -p mobile :compose-app:allTests --console=plain` |
| Stateless access UI/root | Compose common UI | Every modified action dispatches the exact typed intent; existing visible/error/accessibility outcomes remain | `mobile/**/src/commonTest/**/{ui,navigation}/*Test.kt` | `rtk mobile/gradlew -p mobile :features:access:allTests :compose-app:allTests --console=plain` |
| Android lifecycle composition | JUnit + instrumented | Configuration retention, one subscription/submit, no protected-content flash, cleanup contract | `mobile/android-app/src/{test,androidTest}/**/*Test.kt` | `rtk scripts/check-gradle` |
| iOS lifecycle composition | XCTest + XCUITest | Existing Compose controller lifecycle/accessibility/auth journey remains green | `mobile/ios-app/SaqzIOSTests/**`, `mobile/ios-app/SaqzIOSUITests/**` | `rtk scripts/check-ios` |
| Dependency/docs configuration | build/contract | Direct lifecycle dependency resolves for Android and both iOS targets; docs match delivered architecture | Gradle files, `README.md` | relevant Build gate below |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick Access | Feature state-machine tasks | `rtk mobile/gradlew -p mobile :features:access:allTests --console=plain` |
| Quick Compose | ViewModel/root tasks | `rtk mobile/gradlew -p mobile :compose-app:allTests --console=plain` |
| Mobile Build | End of shared implementation phase | `rtk mobile/gradlew -p mobile :features:access:compileAndroidMain :features:access:allTests :compose-app:allTests :android-app:testDevDebugUnitTest --console=plain` |
| Full Android/KMP | Native Android integration/final | `rtk scripts/check-gradle` |
| Full iOS | Native iOS integration/final | `rtk scripts/check-ios` |
| Safety | Before final validation | `rtk scripts/check-credentials` and `rtk scripts/check-scope` |

## Execution Plan

### Phase 1: Typed state-machine inputs

```text
T1 -> T2 -> T3 -> T4
```

### Phase 2: Lifecycle ViewModel and UI boundary

```text
T5 -> T6 -> T7
```

### Phase 3: Aggregate closure

```text
T8
```

## Task Breakdown

### T1: Give authentication one typed intent entry

**Status:** Complete

**What:** Replace action-specific authentication commands with
`AuthenticationIntent` and `onIntent` while preserving every current state and
provider outcome.
**Where:** `AuthenticationCoordinator.kt`, `AuthenticationCoordinatorTest.kt`.
**Depends on:** None.
**Reuses:** Current auth transitions, guards, password clearing, and 19 tests.
**Requirements:** MVI-02..04, MVI-10.
**Tools:** local filesystem/Gradle; skill `tlc-spec-driven`.
**Tests:** common unit; all 19 baseline scenarios enter through `onIntent`, with
no deletions/skips.
**Gate:** Quick Access.
**Commit:** `refactor(mobile): route authentication through typed intents`

### T2: Give verified-session flow one typed intent entry

**Status:** Complete

**What:** Replace verification/name/bootstrap/logout commands with
`SessionIntent` and `onIntent`, keeping invalidation as a port adapter that
forwards into the same state machine.
**Where:** `VerifiedSessionCoordinator.kt`, `VerifiedSessionCoordinatorTest.kt`.
**Depends on:** T1.
**Reuses:** Current identity routing, bootstrap, refresh, logout, and 17 tests.
**Requirements:** MVI-02..04, MVI-10.
**Tools:** local filesystem/Gradle; skill `tlc-spec-driven`.
**Tests:** common unit; all 17 baseline scenarios enter through `onIntent`, with
exact state/error outcomes preserved.
**Gate:** Quick Access.
**Commit:** `refactor(mobile): route session flow through typed intents`

### T3: Give group selection one typed intent entry

**Status:** Complete

**What:** Replace selection commands with `GroupSelectionIntent` and
`onIntent`, adding an active-selection token so stale completions cannot replace
a newer state.
**Where:** `GroupSelectionCoordinator.kt`, `GroupSelectionCoordinatorTest.kt`.
**Depends on:** T2.
**Reuses:** Current membership reconciliation/storage behavior and 14 tests.
**Requirements:** MVI-02..04, MVI-10; concurrency edge case.
**Tools:** local filesystem/Gradle; skill `tlc-spec-driven` and `backprop` only
if a test failure reveals a missing invariant.
**Tests:** common unit; 14 baseline scenarios plus stale-completion coverage.
**Gate:** Quick Access.
**Commit:** `refactor(mobile): make group selection intent driven`

### T4: Give group administration and invite flows typed inputs

**Status:** Complete

**What:** Replace group/invite action methods with their respective intent
types and one `onIntent` per state machine.
**Where:** `GroupAdministrationCoordinator.kt`,
`DeferredInviteCoordinator.kt`, and their co-located tests.
**Depends on:** T3.
**Reuses:** Current role/settings/invite guards, errors, and 38 baseline tests.
**Requirements:** MVI-02..04, MVI-10.
**Tools:** local filesystem/Gradle; skill `tlc-spec-driven`.
**Tests:** common unit; all baseline scenarios use `onIntent`, preserving exact
payload/state assertions and adding no skipped cases.
**Gate:** Mobile Build (phase boundary).
**Commit:** `refactor(mobile): make group access flows intent driven`

### T5: Introduce the KMP access route ViewModel

**Status:** Complete

**What:** Add `AccessViewModel`, `AccessIntent`, immutable `AccessUiState`, and
one-shot `AccessUiEffect`; move route-local fields, reconciliation, runtime
scope, and cleanup from Compose/manual runtime into the ViewModel.
**Where:** new `AccessViewModel.kt`, new `AccessViewModelTest.kt`,
`libs.versions.toml`, `compose-app/build.gradle.kts`.
**Depends on:** T4.
**Reuses:** Current `AccessRuntime` composition, destination rules, request IDs,
native ports, and network cleanup.
**Requirements:** MVI-01..04, MVI-07..09.
**Tools:** local filesystem/Gradle; official AndroidX/JetBrains documentation
already researched; skill `tlc-spec-driven`.
**Tests:** common unit; at least 12 focused cases proving the sole command
surface behavior, exact state updates, single-flight guards, effect one-shot,
reconciliation, stale-state protection, and cleanup exactly once.
**Gate:** Quick Compose.
**Commit:** `feat(mobile): add lifecycle-aware access viewmodel`

### T6: Make authentication and identity screens intent-only

**Status:** Complete

**What:** Change login/registration/reset/verification/name/bootstrap screens to
controlled state plus one typed intent callback and move validation-attempted
business state out of Compose.
**Where:** auth/identity/group-onboarding UI files and their common UI tests.
**Depends on:** T5.
**Reuses:** Existing layouts, semantics, resources, previews, and UI assertions.
**Requirements:** MVI-05, MVI-06, MVI-10.
**Tools:** local filesystem/Gradle; skill `tlc-spec-driven`.
**Tests:** Compose common UI; every existing visible outcome remains and each
interaction asserts the exact typed intent rather than an independent callback.
**Gate:** Quick Access.
**Commit:** `refactor(mobile): make access forms intent-only renderers`

### T7: Wire the intent-only root and native lifecycle

**What:** Retrieve `AccessViewModel` in the Compose route, collect state/effects,
convert group/root screens to one intent callback, and remove manual
`SaqzAppRuntime` screen-state ownership while retaining native adapter lifetime.
**Where:** `AuthenticatedAccessRoot.kt`, `SaqzApp.kt`, group UI files/tests,
`MainActivity.kt`, related compose/Android lifecycle tests.
**Depends on:** T6.
**Reuses:** Existing root destination tests, Android `MainActivityModel`, iOS
Compose controller lifecycle, semantic tags.
**Requirements:** MVI-01, MVI-05..10.
**Tools:** local filesystem/Gradle/Xcode gates; skill `tlc-spec-driven`.
**Tests:** Compose common + Android unit/instrumented + existing iOS suites;
root interactions assert exact `AccessIntent`, configuration retains the KMP
ViewModel, effects are handled once, and route removal closes resources once.
**Gate:** Full Android/KMP, then Full iOS.
**Commit:** `refactor(mobile): drive access ui from kmp viewmodel`

### T8: Document and close the MVI migration

**What:** Document the route ViewModel/MVI convention, run safety and complete
mobile gates, update traceability/status, and prepare independent validation.
**Where:** `README.md`, this feature's spec/tasks files; validation is written
later by the independent verifier.
**Depends on:** T7.
**Reuses:** Existing architecture/testing README sections and repository gates.
**Requirements:** MVI-01..10.
**Tools:** local filesystem/Gradle/Xcode; skill `tlc-spec-driven`.
**Tests:** documentation/build contracts plus all existing relevant suites.
**Gate:** Safety, Full Android/KMP, Full iOS.
**Commit:** `docs(mobile): document mvi viewmodel architecture`

## Phase Execution Map

```text
Phase 1: T1 -> T2 -> T3 -> T4
                              |
Phase 2:                    T5 -> T6 -> T7
                                           |
Phase 3:                                  T8
```

## Task Granularity Check

| Task | Deliverable | Status |
| --- | --- | --- |
| T1 | Authentication state-machine input | Granular |
| T2 | Verified-session state-machine input | Granular |
| T3 | Group-selection state-machine input | Granular |
| T4 | Cohesive privileged group/invite state-machine inputs | Cohesive; both share the selected-group boundary |
| T5 | Access route ViewModel | Granular |
| T6 | Auth/identity screen input boundary | Cohesive UI boundary |
| T7 | Root/group route integration | Cohesive lifecycle integration |
| T8 | Documentation and aggregate closure | Granular |

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | None | Match |
| T2 | T1 | T1 -> T2 | Match |
| T3 | T2 | T2 -> T3 | Match |
| T4 | T3 | T3 -> T4 | Match |
| T5 | T4 | T4 -> T5 | Match |
| T6 | T5 | T5 -> T6 | Match |
| T7 | T6 | T6 -> T7 | Match |
| T8 | T7 | T7 -> T8 | Match |

## Test Co-location Validation

| Task | Layer | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Authentication state machine | common unit | common unit, 19 baseline | OK |
| T2 | Session state machine | common unit | common unit, 17 baseline | OK |
| T3 | Selection state machine | common unit | common unit, 14 baseline + stale | OK |
| T4 | Administration/invite state machines | common unit | common unit, 38 baseline | OK |
| T5 | Route ViewModel/config | common unit + build | 12+ common unit + Quick Compose | OK |
| T6 | Auth/identity UI | Compose common UI | Compose common UI | OK |
| T7 | Root/group/native lifecycle | Compose + native lifecycle | Compose + Android + iOS gates | OK |
| T8 | Docs/config | build/contract | docs/build + aggregate gates | OK |

## Execution Approval

Eight tasks fit one primary-agent batch, so no implementation worker delegation
is proposed. After T8, an independent verifier sub-agent runs automatically.
Default tools are local filesystem edits plus repository Gradle/Xcode scripts;
no MCP is required during Execute.
