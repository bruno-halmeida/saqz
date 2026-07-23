# Mobile Navigation Architecture Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the per-task cycle, atomic commits, batch delegation, final Verifier, and discrimination sensor.

**If the skill cannot be activated, STOP and tell the user.**

**Design**: `.specs/features/mobile-navigation-architecture/design.md`
**Status**: Approved — reconciled 2026-07-23 against post-presentation-MVI code

### Execution precondition

Execution SHALL NOT start until `.specs/features/mobile-solid-refactor-wave-2/validation.md` records a final `PASS`. **Satisfied 2026-07-23** (Wave-2 validation.md: final PASS, no blocker/high findings). T01 then re-baselines every provisional path and count against the current code.

### Reconciliation note (2026-07-23 — post `mobile-presentation-compose-mvi`)

The presentation-MVI feature (commits `f7d4060..382c1eb`) landed AFTER this plan was authored and changed its baseline:

- **Five pre-auth routes are already extracted** into per-route ViewModels + feature-module Roots (`LoginRoot`, `RegistrationRoot`, `PasswordResetRoot`, `VerificationRoot`, `NameCompletionRoot` in `:features:access`), each using `koinViewModel` + `collectAsStateWithLifecycle` + callback surfaces. Nav entries for these routes WRAP the existing Roots — do not recreate their ViewModels (affects T03/T11/T17).
- **Shared presentation primitives now exist and hosts must reuse them**: `MviViewModel<S,I,E>` (`:core:common`), `UiText` and `ObserveAsEvents` (`:core:design-system`), lifecycle-aware collection convention.
- **`AccessViewModel` is already slimmed** to orchestration (session/bootstrap/selection/destination) + the AD-025-deferred panels (settings/memberships/invite/create). The manual destination computation to remove in T24 is `AccessRootSnapshot.destination()` in `AuthenticatedAccessRoot.kt` (735 lines currently).
- **Deferred panels land here**: Settings/Memberships/Invite and GroupsList/GroupDetail/GroupMore intentionally still share their orchestrator ViewModels (AD-025). This feature is where they become real `NavEntry`s — the T11–T13 adapter ViewModels are their promotion path (satisfies the deferred PMVI-001 scope; see `mobile-presentation-compose-mvi/tasks.md` deferral log).
- **T02 version check**: design pins `lifecycle-viewmodel-navigation3:2.10.0` (androidx coordinates), but the catalog uses JetBrains multiplatform `lifecycle = 2.9.6`. T02 MUST resolve the exact JetBrains artifact coordinates/versions compatible with CMP 1.11.1 / Kotlin 2.4.10 before adding entries — do not assume the androidx version string.
- **T14 note**: `GameDetailViewModel` currently receives `SavedStateHandle` via a manual Koin `get()` (`cfe19b3`). Once entries use `rememberViewModelStoreNavEntryDecorator`, the handle should come from the NavEntry scope — T14 revisits that binding so restoration keys off the entry, not a singleton handle.

### Verification cadence

- Every task owns and runs only the focused tests for the code it changes.
- Tests derived from acceptance criteria are co-located with implementation; they are never deferred.
- No intermediate task runs instrumented Android, XCTest/XCUITest, complete iOS, or repository-wide aggregate gates.
- T26 alone runs end-to-end/platform suites and `rtk scripts/check-all`, applies feature-attributable adjustments, reruns affected focused suites, and repeats `check-all` until green. This deliberately broad closing task is the user-approved exception to the otherwise atomic cadence because platform and aggregate regressions can only be attributed after the complete graph is integrated.
- After T26, an independent Verifier performs the spec-anchored check and discrimination sensor and writes `validation.md`.

### Tools

- Skill for every task: `tlc-spec-driven`.
- Other skills: NONE.
- MCPs: NONE.
- Repository/file tools and official documentation may be used as required by the skill's Knowledge Verification Chain.

---

## Test Coverage Matrix

> Generated from `AGENTS.md`, the approved spec/context/design, existing KMP tests, Gradle manifests, and repository scripts. Existing tests are a floor; every acceptance criterion and listed edge case remains required.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Focused Gate |
| --- | --- | --- | --- | --- |
| Module/build boundaries | structural + compile | Module direction, registration, one exported framework, no feature → `:navigation`/Nav3 UI dependency | `mobile/*.gradle.kts`, `mobile/navigation/`, `tests/scripts/` | G1S/G1G/G1N/G1D |
| Access route contracts/adapters | common unit | Exhaustive keys; all state/intent/effect branches; no duplicated state machines | `mobile/features/access/src/commonTest/**` | G2 |
| Groups/Finance route contracts/adapters | common unit | Exhaustive keys, ID validation, role behavior, placeholders remain inert | `mobile/features/groups/src/commonTest/**` | G3 |
| Serialization | common unit | Every concrete key round-trips through explicit polymorphic configuration | `mobile/navigation/src/commonTest/**/serialization/**` | G4 |
| NavigationSession/policies | common unit | Every command/back/tab/transient/authz/restore/clear branch; all edge cases | `mobile/navigation/src/commonTest/**` | G4 |
| Entry lifecycle | common Compose | Distinct stack owners, inactive retention, definitive-pop/logout/group cleanup | `mobile/navigation/src/commonTest/**` | G4 |
| Access/Groups/Finance entries | common Compose | Every route entry, chrome, placeholders, back and reconciliation outcomes | `mobile/navigation/src/commonTest/**` | G5/G6 |
| Product display | common Compose | One display, four stacks, active-entry topology, root back, restoration | `mobile/navigation/src/commonTest/**` | G4 |
| App-local Home/Catalog | common Compose | Start, Catalog, back, reselection, retained state, exactly two routes | `mobile/compose-app/src/commonTest/**/navigation/**` | G7 |
| Composition-root integration | common unit/Compose | Post-Wave-2 orchestrator/Koin bindings feed one host without duplicate wiring | `mobile/compose-app/src/commonTest/**` | G8/G9 |
| Android navigation lifecycle | instrumented, final only | Recreation, back, retained/released ViewModels, logout/group switch | `mobile/android-app/src/androidTest/**` | G10 |
| iOS navigation lifecycle | XCTest/XCUITest, final only | Shared graph and cold relaunch; conditional snapshot fallback | `mobile/ios-app/SaqzIOSTests/**`, `SaqzIOSUITests/**` | G10 |
| Repository regression | aggregate, final only | Complete repository and platform contracts | repository-wide | G10 |

**Count rule**: T01 records exact post-Wave-2 test counts. Every later task records its pre-task count and requires the post-task count to equal that baseline plus its named cases, with no silent deletion or weakening.

## Gate Check Commands

All commands are repository-defined. Commands in one gate are separate invocations when multiple rows share the gate.

| Gate | Workdir | Command |
| --- | --- | --- |
| G0: post-Wave-2 baseline | `mobile/` | `rtk ./gradlew :features:access:compileAndroidMain :features:access:allTests :features:groups:compileAndroidMain :features:groups:allTests :compose-app:allTests --console=plain` |
| G1S: workspace structure | repository root | `rtk scripts/check-scope` |
| G1S: structure fixture contract | repository root | `rtk tests/scripts/check-scope.test.sh` |
| G1G: Gradle script contract | repository root | `rtk tests/scripts/check-gradle.test.sh` |
| G1N: navigation module | `mobile/` | `rtk ./gradlew :navigation:compileAndroidMain :navigation:allTests --console=plain` |
| G1D: dependency-guard fixture contract | repository root | `rtk tests/scripts/check-mobile-navigation-dependencies.test.sh` |
| G2: Access | `mobile/` | `rtk ./gradlew :features:access:compileAndroidMain :features:access:allTests --console=plain` |
| G3: Groups | `mobile/` | `rtk ./gradlew :features:groups:compileAndroidMain :features:groups:allTests --console=plain` |
| G4: Navigation | `mobile/` | `rtk ./gradlew :navigation:compileAndroidMain :navigation:allTests --console=plain` |
| G5: Access + Navigation | `mobile/` | `rtk ./gradlew :features:access:compileAndroidMain :features:access:allTests :navigation:compileAndroidMain :navigation:allTests --console=plain` |
| G6: Groups + Navigation | `mobile/` | `rtk ./gradlew :features:groups:compileAndroidMain :features:groups:allTests :navigation:compileAndroidMain :navigation:allTests --console=plain` |
| G7: Compose app | `mobile/` | `rtk ./gradlew :compose-app:allTests --console=plain` |
| G7D: resolved mobile navigation dependencies | repository root | `rtk scripts/check-mobile-navigation-dependencies --require-no-legacy` |
| G8: Navigation + Compose app | `mobile/` | `rtk ./gradlew :navigation:compileAndroidMain :navigation:allTests :compose-app:allTests --console=plain` |
| G9: Groups + Navigation + Compose app | `mobile/` | `rtk ./gradlew :features:groups:compileAndroidMain :features:groups:allTests :navigation:compileAndroidMain :navigation:allTests :compose-app:allTests --console=plain` |
| G10: final aggregate | repository root | `rtk scripts/check-all` |
| G10: affected iOS rerun after adjustment | repository root | `rtk scripts/check-ios --dev-only` |
| G10: affected Android rerun after adjustment | `mobile/` | `rtk ./gradlew :android-app:connectedDevDebugAndroidTest --console=plain` |

---

## Execution Plan

Phases are sequential and indivisible during task-budgeted batching.

```text
mobile-solid-refactor-wave-2 PASS
  |
  v
Phase 1 Foundation
T01 -> T02 -> T03 -> T04 -> T05 -> T06
  |
  v
Phase 2 State And Route Ownership
T07 -> T08 -> T09 -> T10 -> T11 -> T12 -> T13 -> T14 -> T15
  |
  v
Phase 3 Navigation Displays
T16 -> T17 -> T18 -> T19 -> T20 -> T21
  |
  v
Phase 4 Integration And Verification
T22 -> T23 -> T24 -> T25 -> T26
```

Suggested sequential batches: Worker 1 = Phase 1 (6 tasks), Worker 2 = Phase 2 (9), Worker 3 = Phase 3 (6), Worker 4 = Phase 4 (5). Never split a phase.

---

## Task Breakdown

### Phase 1: Foundation

### T01: Re-baseline after Wave-2 PASS

**What**: Confirm Wave-2 validation is `PASS`, inventory the resulting orchestrator/Koin/navigation seams, and replace every provisional task path/count with the exact post-Wave-2 baseline before production edits.
**Where**: Wave-2 `validation.md`; this `tasks.md`; post-Wave-2 `mobile/{compose-app,features/access,features/groups}/`.
**Depends on**: Wave-2 PASS precondition.
**Reuses**: Wave-2 validation and existing focused Gradle suites.
**Requirements**: REG-02, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Wave-2 PASS evidence exists.
- [x] Exact paths and test counts replace provisional values.
- [x] The post-Wave-2 Groups state-source-to-adapter inventory and exact legacy navigation artifact/removal patterns are pinned for T13 and T25.
- [x] G0 passes without production edits.

**Tests**: Focused baseline verification; no new production/test code.
**Gate**: G0.
**Commit**: `docs(mobile): rebaseline navigation after wave 2`

#### T01 baseline evidence (2026-07-23)

- Wave-2 PASS: `.specs/features/mobile-solid-refactor-wave-2/validation.md` records final `PASS` (Koin bootstrap + manual graph removal), no blocker/high findings.
- G0 command run verbatim from `mobile/`: `rtk ./gradlew :features:access:compileAndroidMain :features:access:allTests :features:groups:compileAndroidMain :features:groups:allTests :compose-app:allTests --console=plain` → `BUILD SUCCESSFUL`, no production edits made.
- **Exact post-Wave-2 test counts (JUnit XML aggregate, this run)**: `features/access` = 156, `features/groups` = 807, `compose-app` = 185. Total = 1148. These are the pre-task floors for every later count-rule check in this feature.
- **Legacy navigation artifact inventory pinned for T24/T25** (grep of `*.kt`, current tree):
  - `AccessPage`, `AccessDestination`(`Stack`), `showAppHome`, `handleGroupsIntent`: `compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/{AccessUiState.kt,AccessViewModel.kt,AccessViewModelSupport.kt,AuthenticatedAccessRoot.kt}` (+ tests `AccessViewModelTest.kt`, `AuthenticatedAccessRootTest.kt`).
  - `GroupsNavigationState`/`GroupsNavigationEffect`: `features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/navigation/{GroupsNavigationState.kt,GroupsNavigationEffect.kt}`.
  - `GroupsNavigationViewModel`: `compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/GroupsNavigationViewModel.kt` (+ test `GroupsNavigationViewModelTest.kt`).
  - `GroupsRouteHost`: `compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/ui/groups/GroupsRouteHost.kt` (+ test `GroupsRouteHostTest.kt`).
  - `GroupsDestinationContent`: `features/groups/src/commonMain/kotlin/br/com/saqz/groups/ui/GroupsDestinationContent.kt` (co-located `GroupsNavigationChrome.kt`, `GroupsRouteScreens.kt` reused for chrome/screens, not removed).
  - `AuthenticatedAccessRoot.kt` (735 lines) hosts the manual `AccessRootSnapshot.destination()` computation targeted by T24; `SaqzKoinBootstrapTest.kt` references `GroupsNavigationState`/effect wiring and is an in-scope T25 regression anchor.
- **Groups content-route state-source inventory pinned for T13** (existing state machines/adapters a T13 route adapter must project from, one adapter type per source — no new sources introduced by this batch):
  - `GroupSelectionState` (features/groups presentation) — Setup/Selector/Loading/LoadError/GroupHome selection projection (T12 scope).
  - Group administration/access/photo state surfaced through `AccessViewModel`'s AD-025-deferred panels (Settings/Memberships/Invite/CreateGroup) — remains the single source for those adapters; no duplicate coordinator permitted.
  - `GameDetailViewModel` (`features/groups/.../games/detail/`) — existing dedicated VM, factory-wrapped in T14, not adapter-projected.
  - `GroupSetupViewModel` (`features/groups/.../setup/`) — existing dedicated VM, factory-wrapped in T14.
  - Finance/OwnCharges placeholders — existing `RoutePage` placeholder content + finance role resolver, inert adapter only (T15 scope).
- No production or test files were edited to produce this baseline; this section is documentation only.

### T02: Register Navigation Compose 3 and `:navigation`

**What**: Add the first-level KMP module, Navigation Compose 3/lifecycle pins, module-direction contracts, and focused script fixtures while retaining legacy Nav2 until T22. Add a repository dependency-guard script whose fixture contract proves it resolves mobile graphs and whose `--require-no-legacy` mode is activated only in T22.
**Where**: `mobile/settings.gradle.kts`, version catalog, new `mobile/navigation/build.gradle.kts`, scope/Gradle scripts and tests.
**Depends on**: T01.
**Reuses**: KMP Compose module convention.
**Requirements**: MODNAV-01..03, MODNAV-06, REG-04.
**Tools**: Skill `tlc-spec-driven`; official Nav3 docs; MCP NONE.

**Done when**:
- [x] `:navigation` targets Android and iOS.
- [x] Features do not depend on `:navigation` or Nav3 UI.
- [x] Structural fixtures fail if a feature imports Navigation Compose 3 UI, declares a direct `navigation3-ui` Gradle dependency, or depends on `:navigation`.
- [x] Framework-export fixtures prove `:compose-app` remains the only iOS umbrella framework, fail if `:navigation` is exported independently, and fail if a public `SaqzMobile` API exposes a `:navigation` type.
- [x] Script fixtures recognize the module boundary.
- [x] G1S, G1G, G1N, and G1D pass while legacy Nav2 remains temporarily allowed.

**Tests**: Positive and discriminating-negative structural fixtures for module direction, forbidden Nav3 UI imports/dependencies, independent framework export, public-API leakage, plus a navigation smoke test.
**Gate**: G1S, G1G, G1N, then G1D.
**Commit**: `feat(mobile): add navigation module`

#### T02 evidence (2026-07-23)

- Verified exact JetBrains Nav3 KMP coordinates directly against Maven Central/Google Maven metadata (not assumed): `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1`, `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0` (both confirmed present in `repo1.maven.org` metadata), and the lightweight KMP key contract `androidx.navigation3:navigation3-runtime:1.1.1` (confirmed on `dl.google.com`, publishes `iosArm64`/`iosSimulatorArm64` variants, depends only on `androidx.compose.runtime` — no Compose UI) for feature route contracts.
- New module: `mobile/navigation/build.gradle.kts` (Android + iOS targets), smoke test `mobile/navigation/src/commonTest/kotlin/br/com/saqz/navigation/NavigationModuleSmokeTest.kt`.
- New dependency-guard script `scripts/check-mobile-navigation-dependencies` (module-direction, Nav3-UI-import ban, framework-export leakage, `--require-no-legacy` mode) with fixture contract `tests/scripts/check-mobile-navigation-dependencies.test.sh` (9 positive/discriminating cases).
- Gates run: G1S (`rtk scripts/check-scope` + fixture, PASS), G1G (fixture, PASS), G1N (`rtk ./gradlew :navigation:compileAndroidMain :navigation:allTests`, BUILD SUCCESSFUL, 1 test), G1D (fixture, 9/9 PASS).

### T03: Define Access route keys

**What**: Add the seven feature-owned serializable `AccessRoute : NavKey` keys.
**Where**: `features/access/src/commonMain/**/navigation/AccessRoute.kt` and co-located common tests.
**Depends on**: T02.
**Reuses**: Existing auth/session state inventory and serialization plugin.
**Requirements**: MODNAV-02, ACCESSNAV-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Route inventory is exhaustive and immutable.
- [x] Access depends only on the lightweight Nav3 key contract.
- [x] Inventory/equality tests pass under G2.

**Tests**: Common unit route inventory/equality.
**Gate**: G2.
**Commit**: `feat(access): define navigation route keys`

#### T03 evidence — `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/navigation/AccessRoute.kt`; tests `mobile/features/access/src/commonTest/kotlin/br/com/saqz/access/navigation/AccessRouteTest.kt` (5 new cases: inventory-size, NavKey-conformance, exhaustive-when, equality-matrix, serialization round-trip). G2 BUILD SUCCESSFUL; access test count 156 → 161 (+5, matches count rule). `api(libs.navigation3.runtime)` added to `features/access/build.gradle.kts`; no `navigation3-ui`/`:navigation` dependency added.

### T04: Define Groups route keys

**What**: Add feature-owned serializable `GroupsRoute` keys and a command boundary rejecting blank `GameDetail.gameId`.
**Where**: `features/groups/src/commonMain/**/navigation/GroupsRoute.kt` and co-located common tests.
**Depends on**: T03.
**Reuses**: Current destination inventory and `GroupRoutePolicy`.
**Requirements**: MODNAV-02, GROUPNAV-01, AUTHZ-03, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] All specified keys exist with stable equality.
- [x] Blank game identity cannot create a navigation command.
- [x] G3 passes.

**Tests**: Common unit inventory, equality, valid/blank game identity.
**Gate**: G3.
**Commit**: `feat(groups): define navigation route keys`

#### T04 evidence — `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/navigation/GroupsRoute.kt` (14 stable keys + `GameDetail(gameId)` with an `init { require(gameId.isNotBlank()) }` command boundary); tests `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/navigation/GroupsRouteTest.kt` (8 new cases). G3 BUILD SUCCESSFUL; groups test count 807 → 815 (+8, matches count rule). `api(libs.navigation3.runtime)` added to `features/groups/build.gradle.kts`.

### T05: Define structural Finance route keys

**What**: Add `FinanceRoute.Finance` and `OwnCharges` without activating real finance screens.
**Where**: `features/groups/src/commonMain/**/navigation/FinanceRoute.kt` and co-located common tests.
**Depends on**: T04.
**Reuses**: Existing finance role policy and placeholders.
**Requirements**: MODNAV-02, FINNAV-01, FINNAV-02, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Both keys serialize and remain gateway/ViewModel independent.
- [x] G3 passes.

**Tests**: Common unit route inventory/equality and dependency guard.
**Gate**: G3.
**Commit**: `feat(groups): define finance navigation keys`

#### T05 evidence — `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/navigation/FinanceRoute.kt` (`Finance`, `OwnCharges`); tests `mobile/features/groups/src/commonTest/kotlin/br/com/saqz/groups/navigation/FinanceRouteTest.kt` (6 new cases, including a singleton-identity dependency guard proving no gateway/ViewModel can attach to a zero-argument route object). G3 BUILD SUCCESSFUL; groups test count 815 → 821 (+6, matches count rule). No production wiring to `FinanceScreen`/`ExpenseScreen` added.

### T06: Add explicit polymorphic route serialization

**What**: Implement the shared `SavedStateConfiguration` for Access, Groups, Finance, and host-owned AppHome keys.
**Where**: `navigation/src/commonMain/**/serialization/` and co-located common tests.
**Depends on**: T05.
**Reuses**: Sealed route hierarchies and kotlinx.serialization.
**Requirements**: MODNAV-05, RESTORE-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; official KMP serialization docs; MCP NONE.

**Done when**:
- [x] Every concrete key round-trips without reflection.
- [x] Omitting a route registration fails the discrimination test.
- [x] G4 passes.

**Tests**: Exhaustive common serializer round-trip matrix.
**Gate**: G4.
**Commit**: `feat(navigation): configure route serialization`

#### T06 evidence

- `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/ProductRoute.kt` — host-owned `ProductRoute.AppHome` (no Home feature module exists; co-located with the serialization config it feeds since T06 is the first task needing it).
- `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/serialization/NavigationSavedStateConfiguration.kt` — one `navigationSavedStateConfiguration: SavedStateConfiguration` (`androidx.savedstate.serialization`) registering all 25 concrete `NavKey` leaves (7 Access + 15 Groups + 2 Finance + 1 Product) via explicit `subclass(X::class, X.serializer())` calls per the official KMP doc example (verified: `polymorphic(NavKey::class) { subclass(RouteA::class, RouteA.serializer()) }`), grouped into one private registration function per family for readability. No reflection (`kotlin-reflect`/`sealedSubclasses`) is used anywhere — portable to iOS.
- Tests: `mobile/navigation/src/commonTest/kotlin/br/com/saqz/navigation/serialization/NavigationSavedStateConfigurationTest.kt` (3 new cases: exhaustive 25-key round trip, `GameDetail` argument preservation, and a discrimination case proving an unregistered `NavKey` fails to encode under the exact configuration — this is what proves the 25-key pass is not vacuous).
- G4 BUILD SUCCESSFUL; navigation test count 1 → 4 (+3, matches count rule).

### Phase 2: State And Route Ownership

### T07: Implement core `NavigationSession`

**What**: Implement the in-memory single-writer command queue, four retained stacks, duplicate-safe forward navigation, tab selection, and all back branches. Persistence, restoration validation, and scope clearing remain exclusively in T10.
**Where**: `navigation/src/commonMain/**/NavigationSession.kt` and co-located tests.
**Depends on**: T06.
**Reuses**: `NavBackStack`, `ProductTab`, approved back algorithm.
**Requirements**: GROUPNAV-03, BACK-02..04, TAB-01..02, STATE-03, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] One writer applies commands atomically.
- [x] Nested back pops; non-home root selects Início; Início root returns false.
- [x] Duplicate keys/tab roots are suppressed.
- [x] G4 passes.

**Tests**: Common unit command-order, push, tab, duplicate, and back matrix.
**Gate**: G4.
**Commit**: `feat(navigation): implement navigation session`

#### T07 evidence — `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/NavigationSession.kt` (`ProductTab`, `NavigationSession` with `selectedTab`, `canGoBack`, `stackFor`, `selectTab`, `push`, `goBack`); tests `mobile/navigation/src/commonTest/kotlin/br/com/saqz/navigation/NavigationSessionTest.kt` (11 new cases). G4 BUILD SUCCESSFUL; navigation test count 4 → 15 (+11, matches count rule).

### T08: Implement transient route reconciliation

**What**: Reconcile GroupSelectionState while replacing Loading/LoadError and preserving Setup/Selector as stable roots.
**Where**: Navigation reconciliation policy and co-located tests.
**Depends on**: T07.
**Reuses**: Authoritative `GroupSelectionState`.
**Requirements**: GROUPNAV-06, STATE-01..03, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Rapid Loading/Error transitions are idempotent.
- [x] Back cannot reveal obsolete transient keys.
- [x] G4 passes.

**Tests**: Common unit transition table and race ordering.
**Gate**: G4.
**Commit**: `feat(navigation): reconcile transient routes`

#### T08 evidence — `NavigationSession.reconcileGroupSelection(GroupSelectionState)` added to `NavigationSession.kt`, replacing the GROUPS root in place (never pushing) so Loading/LoadError can never remain in back history; tests `mobile/navigation/src/commonTest/kotlin/br/com/saqz/navigation/NavigationSessionGroupReconciliationTest.kt` (9 new cases: NoGroup/Selector/Loading/LoadError/Selected mapping, flapping idempotency, back-cannot-reveal-transient, no-op once stack has grown past root). G4 BUILD SUCCESSFUL; navigation test count 15 → 24 (+9, matches count rule).

### T09: Implement authorization pruning

**What**: Reconcile every retained stack against group policy, prune disallowed suffixes, and apply previous-route/GroupHome/Selector/Setup fallbacks.
**Where**: `navigation/src/commonMain/**/authorization/` and co-located tests.
**Depends on**: T08.
**Reuses**: `GroupRoutePolicy`.
**Requirements**: AUTHZ-01..03, RESTORE-04, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Active and inactive stacks cannot retain disallowed visible suffixes.
- [x] Membership loss and invalid IDs follow exact fallbacks.
- [x] G6 passes.

**Tests**: Common unit predecessor, all-stack pruning, fallback, invalid-ID cases.
**Gate**: G6.
**Commit**: `feat(navigation): prune unauthorized routes`

#### T09 evidence — `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/authorization/RouteAuthorizationPruning.kt` (pure `pruneDisallowedSuffix(stack, isAllowed, fallback)`) plus `NavigationSession.pruneDisallowed(isAllowed, membershipActive, fallback, membershipLostFallback)`, applied to every non-HOME stack identically. Tests: `RouteAuthorizationPruningTest.kt` (4 cases) + `NavigationSessionPruningTest.kt` (5 cases, including a RESTORE-04 case). G6 (`:features:groups` + `:navigation`) BUILD SUCCESSFUL; navigation test count 24 → 33 (+9, matches count rule). AUTHZ-03 (blank `gameId` rejection) remains fully covered by T04's `GroupsRouteTest`; no duplicate test added here.

### T10: Implement saved tab, restoration, and scope clearing

**What**: Add the lifecycle boundary around the T07 in-memory session: save the selected tab, restore validated stacks through the library saved-state mechanism, and clear authenticated/group scope before host disposal. Do not add the conditional persistent snapshot port reserved for T26.
**Where**: Navigation session/restoration files and co-located tests; no persistent snapshot port yet.
**Depends on**: T09.
**Reuses**: Shared serializer configuration.
**Requirements**: TAB-03, RESTORE-01..04, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Selected tab and stacks restore together.
- [x] Unauthorized restored state is pruned.
- [x] Logout/group switch clear the proper stack/session data; entry-owner release is verified only after decorators exist in T16 and at integration in T23.
- [x] G4 passes.

**Tests**: Common restoration, selected-tab, logout, same/different-group cases.
**Gate**: G4.
**Commit**: `feat(navigation): restore and clear navigation state`

#### T10 evidence — `NavigationSession.clearAuthenticated()` (resets every tab stack to its captured initial root, selects Início) and `clearGroupScope(groupId)` (resets GROUPS/NOTICES/MORE, idempotent per groupId) added to `NavigationSession.kt`; restoration reuses the existing `initialTab`/`stacks` constructor parameters. Tests: `NavigationSessionRestorationTest.kt` (6 new cases: restore-together, RESTORE-04 reuse, clearAuthenticated, clearGroupScope, idempotent same-group, different-group re-clear). G4 BUILD SUCCESSFUL; navigation test count 33 → 39 (+6, matches count rule).

### T11: Add Access route adapter ViewModel

**What**: Add one reusable Access route adapter type whose entry-owned instances project the seven Access routes from shared auth/session sources.
**Where**: Exact post-Wave-2 Access presentation paths pinned by T01 and co-located tests.
**Depends on**: T10.
**Reuses**: Existing authentication and verified-session coordinators.
**Requirements**: ACCESSNAV-01, LIFE-01, LIFE-03, LIFE-05, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Every Access entry obtains its own adapter instance.
- [x] Adapter owns no auth/session state machine and imports no Nav3 UI/`:navigation`.
- [x] G2 passes.

**Tests**: Common unit state/intent/effect coverage for each Access route mode.
**Gate**: G2.
**Commit**: `feat(access): add route view model adapter`

#### T11 evidence — Login/Registration/PasswordReset/Verification/NameCompletion already have dedicated ViewModels+Roots (pre-existing, not recreated). The two remaining routes without one -- Starting and Bootstrap -- get one reusable `AccessRouteViewModel(mode: AccessRouteMode, session: SessionAccessStateMachine)` in `mobile/features/access/src/commonMain/kotlin/br/com/saqz/access/presentation/route/{AccessRouteContract.kt,AccessRouteViewModel.kt}`; STARTING mode is a static projection, BOOTSTRAP mode projects `SessionAccessState.Bootstrapping/BootstrapError`. Tests `AccessRouteViewModelTest.kt` (7 new cases). G2 BUILD SUCCESSFUL; access test count 119 → 126 (+7, matches count rule; this batch's own measured baseline, since `:features:access` has only one Gradle test task, `iosSimulatorArm64Test`).

### T12: Add Groups selection route adapter ViewModel

**What**: Add one reusable entry-owned adapter for Setup, Selector, Loading, and LoadError projections.
**Where**: Groups presentation package and co-located tests.
**Depends on**: T11.
**Reuses**: `GroupSelectionState`.
**Requirements**: GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Each state-root entry has immutable projected state and typed commands.
- [x] Selection business logic remains in the existing machine.
- [x] G3 passes.

**Tests**: Common unit projections and intent delegation.
**Gate**: G3.
**Commit**: `feat(groups): add selection route adapter`

#### T12 evidence — `GroupSelectionRouteViewModel` in `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/route/GroupSelectionRouteViewModel.kt`: one reusable type for Setup/Selector/Loading/LoadError, projecting the existing `GroupSelectionState` and reusing the existing `GroupOnboardingIntent` contract directly (no new state/intent types), forwarding Select/Retry to `GroupSelectionStateMachine` and emitting a typed `GroupSelectionRouteEffect.OpenCreateGroup` instead of navigating directly. Tests `GroupSelectionRouteViewModelTest.kt` (5 new cases). G3 BUILD SUCCESSFUL; groups test count 462 → 467 (+5, matches count rule).

### T13: Add Groups content route adapter ViewModels

**What**: Implement the source-to-adapter inventory pinned by T01: exactly one reusable adapter type per underlying Groups content state source, with entry-owned instances for GroupHome and current secondary/placeholder/admin route projections.
**Where**: Groups presentation package and co-located tests.
**Depends on**: T12.
**Reuses**: Administration/access/photo state and existing placeholder contracts.
**Requirements**: GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05, REG-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Route instances expose only their relevant immutable state/typed intents.
- [x] Routes sharing a state source reuse its single adapter type; routes backed by different sources do not acquire a catch-all coordinator.
- [x] A structural/unit inventory test fails for a duplicate adapter type, missing source mapping, or catch-all coordinator relative to the T01 inventory.
- [x] No coordinator/business state is duplicated.
- [x] G3 passes.

**Tests**: Common unit source-to-adapter inventory, route-mode projections, role gating, and typed effects.
**Gate**: G3.
**Commit**: `feat(groups): add content route adapter`

#### T13 evidence — Four adapter types, one per underlying source, in `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/route/`: `GroupHomeRouteViewModel` (administration + photo, the sole two-source route), `GroupAdministrationRouteViewModel` (mode SETTINGS/MEMBERSHIPS, reusing `GroupAdministrationState`/`GroupAdministrationIntent` directly), `GroupContentPlaceholderRouteViewModel` (mode PROFILE_COMPLETION/PEOPLE/GAMES/NOTICES/MORE, deriving an immutable `GroupRouteAccess` via the existing `GroupRoutePolicy`), `GroupInviteRouteViewModel` (`InviteToolStateMachine`). `GroupContentRouteInventoryTest.kt` proves routes sharing a source produce instances of the identical `::class` and distinct sources produce distinct classes. Tests: `GroupHomeRouteViewModelTest.kt` (6), `GroupAdministrationRouteViewModelTest.kt` (4), `GroupContentPlaceholderRouteViewModelTest.kt` (6), `GroupInviteRouteViewModelTest.kt` (4), `GroupContentRouteInventoryTest.kt` (1) = 21 new cases. G3 BUILD SUCCESSFUL; groups test count 467 → 488 (+21, matches count rule).

### T14: Add existing route ViewModel factories

**What**: Provide entry-compatible factory bindings for existing `GameDetailViewModel` and `GroupSetupViewModel` without changing their behavior.
**Where**: Owning Groups presentation/factory files and co-located tests.
**Depends on**: T13.
**Reuses**: Existing ViewModels, typed intents/effects, and Wave-2 Koin dependencies.
**Requirements**: LIFE-01..05, GROUPNAV-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Factories accept route identity and shared dependencies.
- [x] Existing ViewModel tests remain unchanged in outcome.
- [x] G3 passes.

**Tests**: Common unit factory identity and existing VM regressions.
**Gate**: G3.
**Commit**: `feat(groups): add route view model factories`

#### T14 evidence — `GameDetailViewModelFactory` (`games/detail/GameDetailViewModelFactory.kt`) and `GroupSetupViewModelFactory` (`setup/GroupSetupViewModelFactory.kt`): thin `create(...)` bindings threading route identity into the unchanged existing ViewModels. `GameDetailViewModelFactory.create` accepts an explicit `SavedStateHandle` parameter instead of the current manual singleton Koin `get()` (`cfe19b3`), so a future entry decorator (T16/T21) can supply its own entry-scoped handle. Existing `GameDetailViewModelTest.kt`/`GroupSetupViewModelTest.kt` untouched. Tests: `GameDetailViewModelFactoryTest.kt` (3), `GroupSetupViewModelFactoryTest.kt` (3) = 6 new cases. G3 BUILD SUCCESSFUL; groups test count 488 → 494 (+6, matches count rule).

### T15: Add Finance placeholder route adapter ViewModel

**What**: Add an inert entry-owned adapter for Finance/OwnCharges placeholder routes.
**Where**: Groups finance presentation route package and co-located tests.
**Depends on**: T14.
**Reuses**: Existing placeholder labels and finance-destination role resolver.
**Requirements**: FINNAV-02, LIFE-01, LIFE-03, LIFE-05, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Adapter never resolves finance/expense gateways or real finance screens.
- [x] Owner/athlete labels and typed back/navigation effects remain correct.
- [x] G3 passes.

**Tests**: Common unit inertness and role-label cases.
**Gate**: G3.
**Commit**: `feat(groups): add finance placeholder adapter`

#### T15 evidence — `FinancePlaceholderRouteViewModel` in `mobile/features/groups/src/commonMain/kotlin/br/com/saqz/groups/presentation/route/FinancePlaceholderRouteViewModel.kt`: a dependency-free adapter carrying only its `FinancePlaceholderMode` (FINANCE/OWN_CHARGES); `FinancePlaceholderIntent`/`Effect` are empty sealed interfaces (no command, no navigation -- these are leaf routes). Which mode a user reaches is decided elsewhere by the existing `GroupRoutePolicy`-based resolver (T13's More adapter, T20's effect handler), not duplicated here. Tests `FinancePlaceholderRouteViewModelTest.kt` (4 new cases). G3 BUILD SUCCESSFUL; groups test count 494 → 498 (+4, matches count rule).

### Phase 3: Navigation Displays

### T16: Add stack-scoped entry lifecycle infrastructure

**What**: Implement stack-namespaced content identity and the saveable-state-before-ViewModel decorator chain.
**Where**: `navigation/src/commonMain/**/entry/` and co-located Compose tests.
**Depends on**: T15.
**Reuses**: Official Navigation Compose 3/lifecycle decorators.
**Requirements**: LIFE-01..02, REG-04.
**Tools**: Skill `tlc-spec-driven`; official lifecycle docs; MCP NONE.

**Done when**:
- [x] Equal route keys in distinct stacks have distinct owners/state.
- [x] Inactive entries retain owners; definitive pop clears them.
- [x] Removing logout- or group-cleared keys releases their entry owners.
- [x] G4 passes.

**Tests**: Common Compose lifecycle and collision cases.
**Gate**: G4.
**Commit**: `feat(navigation): scope entries by stack`

#### T16 evidence — `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/entry/StackScopedEntries.kt` (`NavigationEntryId(stackId, routeIdentity)`, `scopeEntryProvider` namespacing every entry's contentKey by owning stack, `rememberStackEntryDecorators` = saveable-state-then-ViewModel-store chain). Tests `StackScopedEntriesTest.kt` (3 new cases: equal singleton key in two stacks gets distinct `ViewModelStore`s, an inactive stack retains its store across recomposition, removing a key from its backStack releases the store). G4 BUILD SUCCESSFUL; navigation test count 39 → 42 (+3, matches count rule).

### T17: Implement `AccessNavigationHost`

**What**: Install Access entries and idempotently reconcile auth/session transitions.
**Where**: `navigation/.../access/AccessNavigationHost.kt` and co-located tests.
**Depends on**: T16.
**Reuses**: Access screens and T11 adapter bindings.
**Requirements**: MODNAV-01, ACCESSNAV-01..04, LIFE-05, REG-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] All Access routes render through entries.
- [x] Registration/PasswordReset back to Login.
- [x] Ready switches navigation mode rather than pushing Groups.
- [x] G5 passes.

**Tests**: Common Compose entry inventory, back, and transition cases.
**Gate**: G5.
**Commit**: `feat(navigation): add access navigation host`

#### T17 evidence — `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/access/AccessNavigationHost.kt`: `installAccessEntries(session)` installs all 7 routes (5 wrap existing Roots unchanged; Starting/Bootstrap get entry-scoped `AccessRouteViewModel` via `viewModel(initializer = ...)`, Bootstrap reusing the unmodified `BootstrapAccessScreen` driven by the adapter's projected state); `reconcileAccessStack` (Login/Registration/PasswordReset canonicalize to a Login root + at most one pushed sub-route so back always resolves to Login; every other session state canonicalizes to its single matching route; idempotent no-op); `isAccessSession` (false only for `Ready`, per ACCESSNAV-04). Added `implementation(project(":core:common"))` to `navigation/build.gradle.kts` (required transitively for `MviViewModel`). Tests `AccessNavigationHostTest.kt` (9 new cases: entry inventory via `entryProvider` without composing content, Login/Registration/PasswordReset canonicalization, back-to-Login, idempotency, non-SignedOut states, Ready/non-Ready mode). G5 (`:features:access` + `:navigation`) BUILD SUCCESSFUL; navigation test count 42 → 51 (+9, matches count rule); access test count unchanged at 126 (no access files touched).

### T18: Implement `GroupsNavigationHost`

**What**: Install AppHome/Groups entries, preserve chrome, and connect actual predecessor back chains.
**Where**: `navigation/.../groups/GroupsNavigationHost.kt` and co-located tests.
**Depends on**: T17.
**Reuses**: Existing Groups screens, `SaqzTopBar`, four-item `SaqzBottomNav`, adapters/factories.
**Requirements**: MODNAV-01, GROUPNAV-01..04, GROUPNAV-06, BACK-01..03, REG-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] GameDetail back returns Games; next back returns predecessor.
- [x] Single-membership GroupHome has no TopBar back; multiple membership returns Selector.
- [x] The Groups TopBar delegates to the supplied `NavigationSession.goBack` callback and removes the expected key; actual `NavDisplay.onBack` equivalence is owned by T21.
- [x] MENU-08/13 chrome and four tabs remain exact.
- [x] G6 passes.

**Tests**: Common Compose route inventory, TopBar callback delegation, chrome, transient, and membership cases.
**Gate**: G6.
**Commit**: `feat(navigation): add groups navigation host`

#### T18 evidence — `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/groups/GroupsNavigationHost.kt`: `installGroupsEntries(session, titleFor, bottomBar, content)` installs the host-owned `ProductRoute.AppHome` slot plus all 15 `GroupsRoute` keys (16 distinct entries). Localized titles, the four-item tab bar, and each route's screen are composition-root bindings (Groups strings/icons/screens are module-internal — no `publicResClass` — so `:navigation` cannot import them); the host owns only chrome + the shared back callback. `chromeFor` classifies SELECTOR (AppHome/Selector) / BARE (Setup/Loading/LoadError/CreateGroup) / SCOPED (everything else, top bar, no bottom menu — MENU-08). `canonicalizeSelectedGroup` gives single membership `[GroupHome]` (root, no back) and multiple `[Selector, GroupHome]` (back → Selector); `groupsBackVisible(depth)` = depth > 1. `GroupsScopedScaffold` renders `SaqzTopBar` whose back delegates to `session::goBack` and is hidden when `!canGoBack` (BACK-03). Tests `GroupsNavigationHostTest.kt` (9 new cases: entry inventory via `entryProvider` without composing content, chrome classification, single/multiple membership shape, idempotency, GameDetail→Games→GroupHome→Selector back chain, scoped TopBar back delegation + hidden-back, selector scaffold bottom-bar/no-top-bar). G6 BUILD SUCCESSFUL; navigation test count 51 → 60 (+9, matches count rule); groups untouched.

### T19: Implement structural `FinanceNavigationHost`

**What**: Install Finance/OwnCharges placeholder entries in the launching source stack.
**Where**: `navigation/.../finance/FinanceNavigationHost.kt` and co-located tests.
**Depends on**: T18.
**Reuses**: Existing `RoutePage` placeholders and role resolver.
**Requirements**: MODNAV-01, FINNAV-01..03, LIFE-05, REG-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Back reveals GroupHome or More according to actual predecessor.
- [x] No real finance/expense screen/gateway is composed.
- [x] G6 passes.

**Tests**: Common Compose owner/athlete predecessor and inertness cases.
**Gate**: G6.
**Commit**: `feat(navigation): add structural finance host`

#### T19 evidence — `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/finance/FinanceNavigationHost.kt`: `installFinanceEntries(session, titleFor, content)` installs Finance/OwnCharges as two distinct entries wrapped in the shared `GroupsScopedScaffold` (top bar, back → `session::goBack`); placeholder content and titles are composition-root bindings. `resolveFinanceRoute(canManageFinance)` = Finance for finance managers, OwnCharges otherwise (same owner/athlete split as the existing policy resolver). A finance key is pushed onto the active launching stack, so back reveals the real predecessor (GroupHome or More). No `FinanceScreen`/`ExpenseScreen`/gateway is referenced (FINNAV-02). Tests `FinanceNavigationHostTest.kt` (5 new cases: entry inventory, role resolution, Finance→GroupHome back, OwnCharges→More back, inert placeholder-only composition). G6 BUILD SUCCESSFUL; navigation test count 60 → 65 (+5, matches count rule); groups untouched.

### T20: Implement typed navigation effect handlers

**What**: Translate feature-owned effects into serialized session commands, preserving deferred attendance → Games → GameDetail behavior.
**Where**: Domain handler packages in `:navigation` and co-located tests.
**Depends on**: T19.
**Reuses**: Existing Access/Groups typed effects.
**Requirements**: LIFE-04, GROUPNAV-02, BACK-01, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [x] Handlers are exhaustive and duplicate-safe.
- [x] Feature ViewModels import neither `:navigation` nor Nav3 UI.
- [x] Deferred GameDetail has Games as predecessor.
- [x] G5 and G6 pass.

**Tests**: Common unit effect matrix and duplicate/deep-link cases.
**Gate**: G5, then G6.
**Commit**: `feat(navigation): handle navigation effects`

#### T20 evidence — `mobile/navigation/src/commonMain/kotlin/br/com/saqz/navigation/effect/NavigationEffectHandlers.kt`: pure translators from feature-owned typed effects to `NavigationSession` mutations. `handleOpenAttendanceGame` canonicalizes `Games` onto the GROUPS stack before pushing `GameDetail(gameId)` so back returns to Games (GROUPNAV-02/BACK-01), guarding the already-open game for duplicate-safety. Exhaustive `when` handlers: `handleGroupSelectionEffect` (OpenCreateGroup→push), `handleGroupContentEffect` (OpenPeople→push, OpenFinance→`resolveFinanceRoute` by role), `handleGroupHomeEffect` (Settings/Memberships/Invite→push returning `true`; SwitchGroup/ConfirmLogout are orchestrator-owned domain events returning `false`, stack untouched). Non-back-stack effects (GroupInviteRouteEffect.RequestShare share, GameDetailEffect domain/edit, empty GroupAdministrationRouteEffect) are documented out of scope and forwarded at the composition root, not here. Feature ViewModels import neither `:navigation` nor Nav3 UI — structurally guaranteed by the T02 dependency guard (no feature depends on `:navigation`). Tests `NavigationEffectHandlersTest.kt` (7 new cases: deep-link Games-predecessor, deep-link duplicate-safety, create-group, people, finance-by-role, panel pushes+consumed, domain effects not navigation). G5 and G6 BUILD SUCCESSFUL; navigation test count 65 → 72 (+7, matches count rule); access/groups untouched.

### T21: Implement one `ProductNavigationHost`

**What**: Create/decorate all stacks, combine Home with non-home entries, and render exactly one active `NavDisplay`.
**Where**: `navigation/.../ProductNavigationHost.kt` and co-located tests.
**Depends on**: T20.
**Reuses**: T07-T20 state, serializers, entries, decorators, handlers.
**Requirements**: MODNAV-01, ACCESSNAV-04, BACK-02, BACK-04, TAB-01, TAB-03, LIFE-01..02, RESTORE-01, RESTORE-04, REG-03..04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [ ] Exactly one `NavDisplay` renders.
- [ ] Non-home roots display Home + active entries and back to Início.
- [ ] `NavDisplay.onBack` and the TopBar callback are wired to the same session mutation; equivalent invocations from the same snapshot remove the same key and produce identical snapshots.
- [ ] Four stacks/selected tab restore without empty display.
- [ ] G4 passes.

**Tests**: Common Compose display count, topology, TopBar/`NavDisplay.onBack` equivalence, tabs, restoration, and lifecycle.
**Gate**: G4.
**Commit**: `feat(navigation): compose product navigation host`

### Phase 4: Integration And Verification

### T22: Migrate app-local Home/Catalog to Navigation Compose 3

**What**: Replace legacy Nav2 with app-owned local stacks, route ViewModels, serializers, and `NavDisplay`, then remove the legacy dependency.
**Where**: Existing `compose-app` Home/Catalog navigation files, tests, and version catalog dependency entries.
**Depends on**: T21.
**Reuses**: Existing `SaqzNavHostTest` outcomes and app-local screens.
**Requirements**: MODNAV-04, MODNAV-06, LIFE-01, LIFE-03, LIFE-05, REG-01..02, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [ ] Exactly two routes remain with all existing behaviors.
- [ ] A focused resolved-dependency check proves legacy `navigation-compose:2.9.2` is absent from every mobile resolvable graph, and its discriminating fixture fails when the legacy alias/dependency is restored.
- [ ] G7 passes.

**Tests**: Rewritten common Compose tests preserving all existing outcomes plus resolved-dependency and discriminating-negative legacy-dependency checks.
**Gate**: G7, then G7D.
**Commit**: `refactor(compose-app): migrate local navigation to nav3`

### T23: Integrate product navigation at the composition root

**What**: Bind the post-Wave-2 orchestrator, Koin route factories, AppHome slot, platform effects, and `ProductNavigationHost` without exported navigation types.
**Where**: Exact post-Wave-2 compose-app paths pinned by T01; Koin binding tests.
**Depends on**: T22.
**Reuses**: Wave-2 orchestrator, singleton graph, route factories.
**Requirements**: MODNAV-03, ACCESSNAV-04, LIFE-02, RESTORE-02..03, REG-01, REG-03..04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [ ] Compose app remains sole framework exporter.
- [ ] The T02 public-API leakage guard passes against the integrated `SaqzMobile` framework surface.
- [ ] One DI/network graph remains.
- [ ] Logout/group switch clear navigation and release affected entry owners before host disposal.
- [ ] G8 passes.

**Tests**: Focused composition-root binding/integration tests.
**Gate**: G8, then G1S.
**Commit**: `refactor(compose-app): integrate product navigation`

### T24: Remove Access manual navigation state

**What**: Remove `AccessPage`, `AccessDestination`, `AccessDestinationStack`, `showAppHome`, and `handleGroupsIntent` after their Nav3 replacements are active.
**Where**: Post-Wave-2 compose-app navigation files pinned by T01 and focused tests.
**Depends on**: T23.
**Reuses**: Existing `AuthenticatedAccessRootTest` outcomes.
**Requirements**: ACCESSNAV-05, GROUPNAV-05, REG-01..02, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [ ] A structural source-scan guard fails if `AccessPage`, `AccessDestination`, `AccessDestinationStack`, `showAppHome`, or `handleGroupsIntent` exists outside an explicitly allowlisted test fixture.
- [ ] Observable Access/AppHome behavior remains tested.
- [ ] G7 passes.

**Tests**: Focused compose-app outcome regressions plus a discriminating-negative structural fixture for every forbidden artifact.
**Gate**: G7, then G1S.
**Commit**: `refactor(compose-app): remove manual access navigation`

### T25: Remove Groups manual destination state

**What**: Remove `GroupsNavigationState.destination`, `GroupsNavigationState.gameId`, `GroupsNavigationState.requestedGroupId`, dead `GroupsNavigationEffect`, `GroupsNavigationViewModel`, and superseded `GroupsRouteHost`/`GroupsDestinationContent` switching, re-baselined to exact post-Wave-2 symbols by T01.
**Where**: Groups presentation navigation package, compose-app seams pinned by T01, migrated tests.
**Depends on**: T24.
**Reuses**: Group policy/screens and Nav3 behavior tests.
**Requirements**: GROUPNAV-05, LIFE-03, REG-01..02, REG-04.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [ ] Route history exists only in `NavigationSession`.
- [ ] A structural source-scan guard fails if any T01-pinned legacy artifact is reintroduced outside an explicitly allowlisted negative fixture, including the three `GroupsNavigationState` fields, `GroupsNavigationEffect`, `GroupsNavigationViewModel`, `GroupsRouteHost`, and `GroupsDestinationContent`.
- [ ] No test is removed or weakened.
- [ ] G9 passes.

**Tests**: Focused Groups/navigation/compose-app outcome suites plus one discriminating-negative fixture per exact T01-pinned legacy field, type, and switching function.
**Gate**: G9, then G1S.
**Commit**: `refactor(groups): remove legacy navigation state`

### T26: Final platform integration and complete verification

**What**: Add final Android/iOS journey coverage, run the iOS cold-relaunch test, conditionally implement the snapshot fallback, and drive the complete gate to green. This is the explicit user-approved broad closing exception: complete-graph platform evidence determines whether integration adjustment or fallback work is necessary.
**Where**: Existing Android lifecycle and iOS unit/UI tests; conditional snapshot port/envelope/adapters; feature-attributable integration files.
**Depends on**: T25.
**Reuses**: Platform lifecycle fixtures, shared serializers, authoritative auth/group policy.
**Requirements**: MODNAV-03, MODNAV-06, RESTORE-01..05, REG-01..03, REG-05.
**Tools**: Skill `tlc-spec-driven`; MCP NONE.

**Done when**:
- [ ] Cold-relaunch restoration is tested only in this task.
- [ ] If Nav3 restoration fails, the conditional snapshot fallback stores route identity only, validates schema/session/group/access, excludes transient keys, and clears correctly.
- [ ] `rtk scripts/check-all` runs first.
- [ ] Each feature-attributable failure is adjusted without weakening tests; its focused suite is rerun.
- [ ] `rtk scripts/check-all` is repeated until passing.
- [ ] Unrelated/pre-existing failures are evidenced and not edited.

**Tests**: Final-only common, Android instrumented, XCTest/XCUITest, and repository aggregate coverage.
**Gate**: G10; use focused G2-G9 and affected platform commands after adjustments, then repeat G10.
**Commit**: `test(mobile): verify navigation integration`

---

## Requirement Coverage

| Requirements | Owning tasks |
| --- | --- |
| MODNAV-01..06 | T02-T06, T17-T23, T26 |
| ACCESSNAV-01..05 | T03, T11, T17, T21, T23-T24 |
| GROUPNAV-01..06 | T04, T08, T12-T14, T18, T20, T24-T25 |
| FINNAV-01..03 | T05, T15, T19 |
| BACK-01..04 | T07, T18, T20-T21 |
| TAB-01..03 | T07, T10, T21 |
| STATE-01..03 | T07-T08 |
| AUTHZ-01..03 | T04, T09 |
| LIFE-01..05 | T11-T23, T25 |
| RESTORE-01..05 | T06, T09-T10, T21, T23, T26 |
| REG-01..05 | T01-T26 |

**Coverage**: 48/48 mapped; 0 unmapped.

---

## Phase Execution Map

```text
Wave-2 PASS -> P1 -> P2 -> P3 -> P4

P1: T01 -> T02 -> T03 -> T04 -> T05 -> T06
P2: T07 -> T08 -> T09 -> T10 -> T11 -> T12 -> T13 -> T14 -> T15
P3: T16 -> T17 -> T18 -> T19 -> T20 -> T21
P4: T22 -> T23 -> T24 -> T25 -> T26
```

---

## Task Granularity Check

| Tasks | Cohesive deliverable | Status |
| --- | --- | --- |
| T01 | One post-Wave-2 baseline | PASS |
| T02 | One module/build registration | PASS |
| T03-T05 | One route hierarchy each | PASS |
| T06 | One serializer configuration | PASS |
| T07 | One navigation-session core | PASS |
| T08-T10 | One policy concern each | PASS |
| T11-T13, T15 | One reusable adapter component per state source | PASS |
| T14 | One existing-VM factory surface | PASS |
| T16 | One entry-lifecycle facility | PASS |
| T17-T19 | One domain entry installer each | PASS |
| T20 | One typed effect-handler layer | PASS |
| T21 | One product display | PASS |
| T22 | One app-local graph migration | PASS |
| T23 | One composition-root integration | PASS |
| T24-T25 | One legacy-state removal domain each | PASS |
| T26 | One final platform integration outcome | PASS |

---

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram shows | Status |
| --- | --- | --- | --- |
| T01 | Wave-2 PASS | Wave-2 PASS → T01 | PASS |
| T02 | T01 | T01 → T02 | PASS |
| T03 | T02 | T02 → T03 | PASS |
| T04 | T03 | T03 → T04 | PASS |
| T05 | T04 | T04 → T05 | PASS |
| T06 | T05 | T05 → T06 | PASS |
| T07 | T06 | T06 → T07 | PASS |
| T08 | T07 | T07 → T08 | PASS |
| T09 | T08 | T08 → T09 | PASS |
| T10 | T09 | T09 → T10 | PASS |
| T11 | T10 | T10 → T11 | PASS |
| T12 | T11 | T11 → T12 | PASS |
| T13 | T12 | T12 → T13 | PASS |
| T14 | T13 | T13 → T14 | PASS |
| T15 | T14 | T14 → T15 | PASS |
| T16 | T15 | T15 → T16 | PASS |
| T17 | T16 | T16 → T17 | PASS |
| T18 | T17 | T17 → T18 | PASS |
| T19 | T18 | T18 → T19 | PASS |
| T20 | T19 | T19 → T20 | PASS |
| T21 | T20 | T20 → T21 | PASS |
| T22 | T21 | T21 → T22 | PASS |
| T23 | T22 | T22 → T23 | PASS |
| T24 | T23 | T23 → T24 | PASS |
| T25 | T24 | T24 → T25 | PASS |
| T26 | T25 | T25 → T26 | PASS |

---

## Test Co-location Validation

| Tasks | Layer | Matrix requires | Included | Status |
| --- | --- | --- | --- | --- |
| T01 | Baseline | Focused verification | G0 | PASS |
| T02 | Build/module | Structural + compile | Scope/Gradle/dependency-guard fixtures + module test | PASS |
| T03-T05 | Route contracts | Common unit | Co-located inventory/equality tests | PASS |
| T06 | Serialization | Common unit | Exhaustive round trips | PASS |
| T07-T10 | Session/policies | Common unit | Co-located branch/edge tests | PASS |
| T11-T15 | Route adapters/factories | Common unit | Co-located state/intent/effect tests | PASS |
| T16 | Entry lifecycle | Common Compose | Co-located lifecycle/collision tests | PASS |
| T17-T19 | Domain entries | Common Compose | Co-located route/chrome/back tests | PASS |
| T20 | Effect handlers | Common unit | Co-located exhaustive handler tests | PASS |
| T21 | Product display | Common Compose | Co-located display/tab/restore tests | PASS |
| T22 | App-local graph | Common Compose + resolved dependency | Migrated Home/Catalog tests + no-legacy resolved-graph guard | PASS |
| T23 | Composition root | Unit/Compose | Co-located binding/integration tests | PASS |
| T24-T25 | Legacy removal | Unit/Compose + structural | Migrated outcomes + exact source-scan absence fixtures | PASS |
| T26 | Platform integration | Instrumented/XCTest/aggregate | Final-only platform and full gates | PASS |

No tests are deferred from an implementation task. T26 adds only the final platform/end-to-end layer and conditional restoration adjustment that cannot be decided before the final iOS cold-relaunch evidence.
