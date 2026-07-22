# Mobile Navigation Architecture Specification

**Status:** Confirmed  
**Phase:** Tasks  
**Created:** 2026-07-22  
**Sequence:** 2 of 4 in the mobile architecture alignment program

## Problem Statement

The mobile workspace currently mixes manual state-based navigation (single destination enum without a back stack), `showAppHome` overlays, `AccessPage` modal pages, and per-composable `BackHandler`s. The result is that the back button ignores the real navigation history (e.g., GameDetail back lands on the group home instead of Games), and adding independent navigation graphs for Access, Groups, and Finance requires touching several unrelated files. A new `:navigation` module should host the product Navigation Compose 3 displays and navigation handlers, with screens and route contracts staying in their respective features, while `:compose-app` remains the composition root and the only framework exported to iOS.

## Goals

- [ ] Introduce a first-level KMP module `:navigation` (`mobile/navigation/`) hosting the product Navigation Compose 3 displays and navigation handlers.
- [ ] Adopt Navigation Compose 3 Multiplatform as the single source of truth for all mobile routes, using app-owned serializable back stacks.
- [ ] Provide `AccessNavigationHost`, `GroupsNavigationHost`, and `FinanceNavigationHost` in `:navigation`.
- [ ] Unify the TopBar back button and system back so both remove the last entry from the same app-owned back stack.
- [ ] Preserve independent per-tab back stacks for the bottom-nav tabs.
- [ ] Keep screens and route contracts in their respective feature modules; no feature depends on `:navigation`.
- [ ] Preserve current UI, authorization, and observable behavior (pure refactor).

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
| --- | --- |
| Activating `FinanceScreen` / `ExpenseScreen` (wiring them to real data in this migration) | `FinanceNavigationHost` is structural only; activation is a separate product feature. |
| Changing business rules, APIs, auth, or group-selection logic | Pure refactor; no behavioral change. |
| Visual/UI changes | Screens, chrome, and strings are preserved as-is. |
| Moving the app-local Home/Catalog navigation host to `:navigation` | It references `:compose-app` demo screens and stays in the composition root, although its implementation migrates to Navigation Compose 3. |
| Creating a `features/finance` module | Finance remains inside the Groups feature per AD-026. |
| Backend, landing-page, or native iOS Swift changes | Repository boundary. |
| Reopening `mobile-solid-refactor-wave-2` requirements | Independent; this feature starts after Wave 2 is verified. |

---

## Assumptions & Open Questions

Every ambiguity is resolved or recorded here — nothing is left silently unclear.

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Module placement | `mobile/navigation/` (sibling of `core/` and `features/`) | Navigation is a first-level concern, not Compose-agnostic core nor a domain feature. | y |
| Screens stay in features | Screens and route contracts remain in `:features:groups` / `:features:access` | Preserves AD-026 boundaries and avoids circular dependencies (`:navigation` depends on features, never the reverse). | y |
| Home/Catalog navigation host stays in `:compose-app` | Migrated to Navigation Compose 3 but not moved | It references `SaqzHomeScreen` / `SaqzCatalogScreen` which live in `:compose-app`; moving would force moving demo screens too. Migrating it removes legacy `navigation-compose:2.9.2` from the workspace. | y |
| Bottom-nav tabs preserve independent stacks | Four serializable app-owned stacks: Início, Grupos, Avisos, Mais | Preserves the validated MENU-13 composition; Jogos remains inside group context and is not a bottom-menu tab. | y |
| Loading / LoadError / reconciliation are transient | Explicit app-owned stack replacement; never remain in back history | Avoids re-entering obsolete transient states on back. | y |
| Route restoration scope | Session-scoped (tab + stacks restored while auth session and selected group are valid); cleared on logout or group switch | User chose session-saved state. | y |
| Access revoked fallback | Pop to previous allowed route; if none, GroupHome; if membership lost, Selector/Setup | User chose "previous allowed route". | y |
| Auth flow included | Login, Registration, PasswordReset, Verification, NameCompletion, Bootstrap become `AccessRoute` keys | User chose to include access navigation in this feature. | y |
| Finance navigation scope | Structural only; preserves current placeholders (`RoutePage`) for Finance/OwnCharges | User chose not to activate existing finance screens in this migration. | y |
| Route-ViewModel ownership | Each route owns its lifecycle-aware ViewModel scoped to its Navigation Compose 3 entry (AD-025 preserved); ViewModels emit typed navigation effects | User chose typed effects + handler; avoids features importing `:navigation` or Navigation Compose 3 UI APIs. | y |
| Navigation library | Navigation Compose 3 Multiplatform `navigation3-ui:1.1.1`; route ViewModel support uses `lifecycle-viewmodel-navigation3:2.10.0` | Official JetBrains documentation confirms support for Android, iOS, desktop, and web from Compose Multiplatform 1.10; the workspace uses 1.11.1. | y |
| Back at a non-home tab root | Select Início; at the Início root, leave back to the platform | Matches the official multiple-back-stack recipe and the user's decision. | y |
| iOS cold-relaunch restoration | Validate library restoration first; if insufficient, persist a session-scoped serialized snapshot through a KMP port | RESTORE-01 remains guaranteed without requiring native Swift UI/navigation. | y |
| iOS route serialization | `SavedStateConfiguration` aggregates feature-owned sealed `NavKey` serializers | Reflection-based route serialization is unavailable on iOS; explicit polymorphic serialization is required by the official KMP documentation. | y |
| BackHandler + showAppHome wrapper | Move to `:navigation`; `showAppHome`, `AccessPage`, and `AccessDestinationStack` are eliminated | User chose to eliminate the overlay and use real routes. | y |
| Dependency on Wave 2 | Implementation starts only after `mobile-solid-refactor-wave-2` is verified complete (T21–T23 create `AccessOrchestrator` and slim `AuthenticatedAccessRoute`) | Avoids two refactors fighting over the same files. | y |
| Observable behavior preservation | No visual/behavioral change beyond the back-stack semantics explicitly approved (GameDetail back returns to Games, etc.) | Pure refactor; suites remain the behavioral contract (NAV-05 style). | y |

**Open questions:** none — all resolved or logged above (spec confirmed 2026-07-22).

---

## User Stories

### P1: First-level `:navigation` module boundaries ⭐ MVP

**User Story**: As a maintainer, I want a dedicated `:navigation` module hosting the product Navigation Compose 3 displays and handlers, so that Access, Groups, and Finance navigation graphs have a common home without coupling feature ViewModels to navigation UI APIs.

**Why P1**: Without the module, the migration cannot start; it is the structural foundation for every other story.

**Acceptance Criteria**:

1. **MODNAV-01** — WHEN the `:navigation` KMP module is created THEN it SHALL host `AccessNavigationHost`, `GroupsNavigationHost`, `FinanceNavigationHost`, their `NavDisplay` composition, and navigation handlers.
2. **MODNAV-02** — WHEN a screen or route contract belongs to a feature THEN it SHALL remain in that feature; no feature module SHALL depend on `:navigation`. Feature route contracts MAY depend on the lightweight Navigation Compose 3 key contract required to implement `NavKey`, but SHALL NOT depend on Navigation Compose 3 UI.
3. **MODNAV-03** — WHEN `:compose-app` integrates navigation THEN it SHALL remain the composition root and the only framework exported to iOS; `:navigation` types SHALL NOT leak to the exported API.
4. **MODNAV-04** — WHEN the app-local Home/Catalog graph is migrated THEN it SHALL remain in `:compose-app`, render through Navigation Compose 3, and SHALL NOT retain legacy `navigation-compose:2.9.2`.
5. **MODNAV-05** — WHEN route keys from Access, Groups, and Finance are restored on iOS THEN `:navigation` SHALL provide a `SavedStateConfiguration` whose polymorphic `SerializersModule` registers every sealed feature route hierarchy; reflection-only serialization SHALL NOT be used.
6. **MODNAV-06** — WHEN the dependency baseline is migrated THEN the version catalog SHALL pin Navigation Compose 3 Multiplatform UI `1.1.1` and lifecycle Navigation Compose 3 support `2.10.0`, SHALL remove legacy `navigation-compose:2.9.2`, and all Android/iOS targets SHALL resolve the same KMP navigation implementation.

**Independent Test**: `:navigation` compiles for all KMP targets; route keys round-trip through the shared serialization configuration on JVM and iOS; `:compose-app` consumes it; the iOS framework build succeeds; no feature module depends on `:navigation`; legacy `navigation-compose:2.9.2` is absent from the resolved workspace graph.

---

### P1: `AccessNavigationHost` ⭐ MVP

**User Story**: As a user, I want login, registration, password reset, verification, name completion, and bootstrap to be real navigation routes, so that back behavior is consistent and the auth flow has a single source of truth.

**Why P1**: Auth is the entry point of every session; today it is state-driven without a back stack, and `AccessDestination`/`AccessDestinationStack` is a trivial single-entry list. Making it route-driven removes the first exception to the new architecture.

**Acceptance Criteria**:

1. **ACCESSNAV-01** — `AccessNavigationHost` SHALL represent `Starting`, `Login`, `Registration`, `PasswordReset`, `Verification`, `NameCompletion`, and `Bootstrap` as serializable `NavKey` routes rendered by `NavDisplay`.
2. **ACCESSNAV-02** — WHEN the user is on `Registration` or `PasswordReset` and presses back (system or TopBar) THEN the system SHALL return to `Login`, preserving the current behavior.
3. **ACCESSNAV-03** — WHEN the auth/session state transitions (SignedOut → AwaitingVerification → CompletingName → Bootstrapping → Ready) THEN `AccessNavigationHost` SHALL reconcile its app-owned back stack with the new state idempotently (no duplicate entries).
4. **ACCESSNAV-04** — WHEN the session becomes `Ready` and a group is selected THEN `NavigationSession` SHALL switch the active entry set from Access to authenticated Groups entries instead of pushing Groups onto the access stack.
5. **ACCESSNAV-05** — `AccessPage`, `AccessDestination`, and `AccessDestinationStack` SHALL be removed.

**Independent Test**: App boots into `Starting`; navigating Login → Registration → back returns to Login; reaching `Ready` swaps to `GroupsNavigationHost`.

---

### P1: `GroupsNavigationHost` ⭐ MVP

**User Story**: As a user, I want group navigation (home, games, game detail, people, finance, notices, more, settings, memberships, invite, create-group, selector, loading, error) to use a real back stack, so that back from GameDetail returns to Games — not to the group home.

**Why P1**: This is the primary bug the feature fixes; the current single-destination model ignores the navigation history.

**Acceptance Criteria**:

1. **GROUPNAV-01** — `GroupsNavigationHost` SHALL represent `AppHome`, `Setup`, `Selector`, `Loading`, `LoadError`, `GroupHome`, `ProfileCompletion`, `People`, `Games`, `GameDetail(gameId)`, `Notices`, `More`, `Settings`, `Memberships`, `Invite`, and `CreateGroup` as serializable `NavKey` routes rendered by `NavDisplay`.
2. **GROUPNAV-02** — WHEN the user navigates `Games → GameDetail` and presses back THEN the system SHALL return to `Games` (not `GroupHome`).
3. **GROUPNAV-03** — WHEN the TopBar back button or the system back is pressed THEN both SHALL invoke the same navigation handler that removes the last key from the active app-owned back stack.
4. **GROUPNAV-04** — WHEN the user is on `GroupHome` with a single membership THEN the TopBar SHALL NOT show a back button (preserving current behavior).
5. **GROUPNAV-05** — `showAppHome`, its `rememberSaveable` state, and the `handleGroupsIntent` overlay wrapper SHALL be removed; `AppHome` is a real `NavKey`.
6. **GROUPNAV-06** — WHEN `GroupSelectionState` transitions between `NoGroup`, `Selector`, `Loading`, `LoadError`, and `Selected` THEN `GroupsNavigationHost` SHALL reconcile its app-owned back stack to the corresponding key, replacing the previous transient key (no back to Loading/LoadError).

**Independent Test**: Navigate `Selector → GroupHome → Games → GameDetail`; back returns to `Games`; back returns to `GroupHome`; back from single-membership `GroupHome` is not offered.

---

### P1: `FinanceNavigationHost` (structural) ⭐ MVP

**User Story**: As a maintainer, I want a `FinanceNavigationHost` scaffold with the current Finance/OwnCharges placeholders, so that future finance activation lands in a dedicated display without rewriting navigation again.

**Why P1**: Establishing the host now keeps the architecture uniform and avoids a second migration when finance is activated.

**Acceptance Criteria**:

1. **FINNAV-01** — `FinanceNavigationHost` SHALL represent `Finance` and `OwnCharges` as serializable `NavKey` routes rendered by `NavDisplay`, preserving the current placeholder content (`RoutePage`).
2. **FINNAV-02** — `ExpenseScreen` and `FinanceScreen` SHALL remain disconnected (no wiring to real data) until a separate feature explicitly approves their activation.
3. **FINNAV-03** — WHEN the user navigates to Finance or OwnCharges THEN the back behavior SHALL remove the finance key and reveal the previous key in the parent Groups stack (typically `GroupHome` or `More`).

**Independent Test**: Owner reaches Finance from `More`; back returns to `More`; athlete reaches OwnCharges; back returns to previous route; no finance screens are rendered.

---

### P1: Back behavior and tab stacks ⭐ MVP

**User Story**: As a user, I want the back button (TopBar and system) to mutate the real app-owned back stack and the bottom-nav tabs to keep their own serializable stacks, so that navigation behaves like a standard app.

**Why P1**: This is the observable fix the feature delivers; it is the success criterion users feel.

**Acceptance Criteria**:

1. **BACK-01** — WHEN the user opens `Games` and then `GameDetail` THEN back SHALL return to `Games`, not to `GroupHome`.
2. **BACK-02** — WHEN the TopBar back button or the system back is pressed THEN both SHALL invoke the same handler that removes the last key from the active back stack.
3. **BACK-03** — WHEN there is no valid previous entry THEN the TopBar back button SHALL NOT be displayed; `GroupHome` for a single-membership user remains without back.
4. **TAB-01** — WHEN the user switches between bottom-nav tabs THEN each tab SHALL preserve its own serializable app-owned back stack and state.
5. **TAB-02** — WHEN the user reselects the already-selected tab THEN the system SHALL NOT duplicate its entry.
6. **TAB-03** — WHEN the user returns to a previously visited tab THEN its Navigation Compose 3 back stack SHALL be restored from saved state.
7. **BACK-04** — WHEN the active stack is at the root of Grupos, Avisos, or Mais and the system back is invoked THEN the system SHALL select Início; WHEN Início is already at its root THEN navigation SHALL leave back handling to the platform.

**Independent Test**: Switch among `Início`, `Grupos`, `Avisos`, and `Mais`; each tab restores its retained stack without duplicate roots. Separately, navigate `GroupHome → Games → GameDetail`; back returns to `Games`.

---

### P1: Reconciliation, authorization, and lifecycle ⭐ MVP

**User Story**: As a maintainer, I want external state changes (group selection, role/profile updates, logout) to reconcile with the navigation back stack safely, so that disallowed routes are popped and ViewModels are scoped correctly.

**Why P1**: These are the implicit-requirement dimensions that, if missed, cause crashes, blank screens, or stale state.

**Acceptance Criteria**:

1. **STATE-01** — WHEN `GroupSelectionState` changes to `NoGroup`, `Selector`, `Loading`, `LoadError`, or `Selected` THEN the corresponding route SHALL replace the previous transient route.
2. **STATE-02** — WHEN back is pressed THEN old `Loading` and `LoadError` routes SHALL never reappear in the back stack.
3. **STATE-03** — WHEN an external state change and a user action happen concurrently THEN the reconciliation SHALL be idempotent and SHALL NOT create duplicate back-stack entries.
4. **AUTHZ-01** — WHEN the role or profile change invalidates the current route THEN the system SHALL pop to the previous allowed route.
5. **AUTHZ-02** — WHEN no previous route is allowed THEN the system SHALL use `GroupHome`; if the membership was lost, it SHALL use `Selector` or `Setup`.
6. **AUTHZ-03** — WHEN a typed route receives a missing or blank identifier (e.g., blank `gameId`) THEN it SHALL be rejected without composing a screen with an invalid identity.
7. **LIFE-01** — WHEN a route is composed THEN it SHALL own its lifecycle-aware ViewModel scoped to its Navigation Compose 3 entry using the multiplatform Navigation Compose 3 ViewModel support artifact (AD-025 preserved).
8. **LIFE-02** — WHEN the route is removed from the back stack definitively THEN its ViewModel SHALL be released.
9. **LIFE-03** — WHEN a ViewModel requests navigation THEN it SHALL emit a typed effect; it SHALL NOT import Navigation Compose 3 UI types or `:navigation`.
10. **LIFE-04** — WHEN the effect is collected at the route entry THEN a handler in `:navigation` SHALL translate it to a mutation of the correct app-owned back stack.
11. **LIFE-05** — Screens SHALL remain stateless, receiving only state and `onIntent`.

**Independent Test**: Remove membership externally while on `People`; the system pops to `Selector`; the `People` ViewModel is released; no crash.

---

### P1: Restoration and regression preservation ⭐ MVP

**User Story**: As a user, I want my tab and back stack restored while my session is valid, and as a maintainer, I want the migration to preserve all current behavior except the explicitly approved back-stack semantics.

**Why P1**: Restoration is the session-lifecycle dimension; regression preservation is the safety contract of a pure refactor.

**Acceptance Criteria**:

1. **RESTORE-01** — WHEN the composition or process is recreated during a valid session THEN the saved tab and back stacks SHALL be restored.
2. **RESTORE-02** — WHEN logout occurs THEN all authenticated back stacks SHALL be cleared.
3. **RESTORE-03** — WHEN the selected group changes THEN back stacks and ViewModels scoped to the previous group SHALL be cleared.
4. **RESTORE-04** — WHEN the restored state is no longer authorized THEN the reconciliation SHALL apply AUTHZ-01/02.
5. **RESTORE-05** — WHEN Navigation Compose 3 saved state does not survive a complete iOS cold relaunch THEN the app SHALL restore a session-scoped serialized navigation snapshot through a provider-neutral KMP persistence port, validate it against the current auth/group/access state before rendering, and clear it on logout or group switch.
6. **REG-01** — The migration SHALL preserve current texts, chrome (top bars, bottom nav), permissions, loading/error states, and screen contents.
7. **REG-02** — Tests SHALL NOT be removed or weakened; expectations MAY change only where they explicitly reflect the new back-stack semantics (e.g., GameDetail back → Games).
8. **REG-03** — Android and iOS SHALL consume the same Compose graph; no platform-specific navigation code SHALL be introduced.
9. **REG-04** — WHEN any implementation task before the final integration task completes THEN it SHALL add or update the tests for its own acceptance criteria and SHALL run only the narrowest relevant test commands for the modules and layers changed by that task; the repository-wide aggregate SHALL NOT be required at intermediate task boundaries.
10. **REG-05** — WHEN the final integration task runs THEN it SHALL execute `rtk scripts/check-all`, fix every failure attributable to this feature without weakening tests, rerun the narrow affected suites after each adjustment, and rerun `rtk scripts/check-all` until it passes. Failures proven unrelated or pre-existing SHALL be reported with evidence and SHALL NOT authorize unrelated changes.

**Independent Test**: Recreate the app on `GameDetail`; the route and its ViewModel are restored; perform logout; all group stacks are cleared; intermediate tasks show focused green suites and the final integration task records a passing `rtk scripts/check-all`.

---

## Edge Cases

- WHEN a typed route (e.g., `GameDetail(gameId)`) receives a blank `gameId` THEN the system SHALL reject it without composing a screen (AUTHZ-03).
- WHEN `GroupSelectionState` flips rapidly between `Loading` and `LoadError` THEN the back stack SHALL never retain transient routes (STATE-01/02).
- WHEN the user is on `GameDetail` and the group membership is externally removed THEN the system SHALL pop to `Selector` (AUTHZ-02), release the `GameDetailViewModel`, and not crash.
- WHEN the user is on a tab stack with multiple entries and logs out THEN all tab stacks SHALL be cleared and `AccessNavigationHost` SHALL show `Login` (RESTORE-02).
- WHEN the process is killed and recreated while the user was on `More` inside `GroupsNavigationHost` THEN the restored state SHALL reconcile with the current `GroupSelectionState` before showing any route (RESTORE-01/04).
- WHEN route-key restoration runs on iOS THEN it SHALL use the explicit polymorphic serializer configuration and SHALL NOT rely on JVM reflection (MODNAV-05).
- WHEN the bottom-nav "Grupos" tab is selected from inside a group (multi-membership) THEN the system SHALL navigate to `Selector` without nesting the previous group stack under it (TAB-01).
- WHEN the iOS app is killed and relaunched during an authenticated session THEN the Compose graph SHALL recreate and reconcile identically to Android (REG-03).

---

## Requirement Traceability

Each requirement gets a unique ID for tracking across design, tasks, and validation.

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| MODNAV-01..06 | P1: `:navigation` module boundaries | Design | Pending |
| ACCESSNAV-01..05 | P1: `AccessNavigationHost` | Design | Pending |
| GROUPNAV-01..06 | P1: `GroupsNavigationHost` | Design | Pending |
| FINNAV-01..03 | P1: `FinanceNavigationHost` (structural) | Design | Pending |
| BACK-01..04 | P1: Back behavior | Design | Pending |
| TAB-01..03 | P1: Tab stacks | Design | Pending |
| STATE-01..03 | P1: Reconciliation | Design | Pending |
| AUTHZ-01..03 | P1: Authorization | Design | Pending |
| LIFE-01..05 | P1: Lifecycle & effects | Design | Pending |
| RESTORE-01..05 | P1: Restoration | Design | Pending |
| REG-01..05 | P1: Regression preservation | Design | Pending |

**Coverage:** 48 total, 0 mapped to tasks (Tasks phase pending), 48 unmapped (expected at this phase).

---

## Success Criteria

How we know the feature is successful:

- [ ] Back from `GameDetail` returns to `Games` (not `GroupHome`) — the primary user-visible fix.
- [ ] TopBar back and system back invoke the same handler and remove the same active-stack key.
- [ ] Bottom-nav tabs preserve and restore independent back stacks.
- [ ] `:navigation` compiles for all KMP targets; feature modules do not depend on `:navigation` or Navigation Compose 3 UI; iOS route keys restore through explicit polymorphic serialization; the iOS framework build succeeds.
- [ ] Legacy `navigation-compose:2.9.2` is absent from the resolved workspace dependencies; the app-local Home/Catalog graph also renders with Navigation Compose 3.
- [ ] `showAppHome`, `AccessPage`, `AccessDestination`, and `AccessDestinationStack` are removed.
- [ ] Each route owns its lifecycle-aware ViewModel (AD-025 preserved); ViewModels emit typed navigation effects.
- [ ] Logout clears all authenticated stacks; group switch clears group-scoped stacks.
- [ ] Every non-final task runs only its focused changed-module tests, with acceptance-criteria tests co-located in that task.
- [ ] The final integration task runs `rtk scripts/check-all`, applies all feature-attributable adjustments, and reruns the complete gate until it passes.
- [ ] Existing suites pass (expectations may change only where they explicitly reflect new back-stack semantics).
