# Mobile Presentation and Compose MVI Specification

**Status:** Draft  
**Phase:** Specify  
**Created:** 2026-07-22  
**Sequence:** 3 of 4 in the mobile architecture alignment program

## Problem

Presentation responsibilities are inconsistently distributed between route composables, screen composables, remembered UI state, and ViewModels. Some routes collect state without lifecycle awareness, production ViewModels expose test-scope injection hooks, state mutations are not consistently atomic, essential input is not systematically restored, and large composables combine dependency acquisition, effect handling, orchestration, and rendering.

The result is avoidable recomposition risk, lifecycle-sensitive behavior, difficult unit testing, weak process-restoration guarantees, and visual components that cannot be previewed or tested independently.

## Goals

- Standardize every navigable screen on a typed unidirectional MVI contract.
- Separate lifecycle/dependency orchestration from stateless visual rendering.
- Keep business and application state in ViewModels with immutable observable state.
- Make state collection lifecycle-aware and one-off effects explicit.
- Restore essential user progress after process recreation without turning transient UI details into durable state.
- Improve Compose stability, accessibility, previewability, and testability without redesigning the product.

## Out of Scope

- Data/domain module boundaries and repository error implementation.
- Route keys, navigation hosts, tab stacks, and back-stack semantics.
- Koin module ownership, Gradle convention plugins, or test-library migration.
- Product copy changes, visual redesign, new animations, or new feature behavior.
- New backend APIs, business rules, persistence guarantees, or authorization policy.

## Assumptions and Decisions

- Confirmed architectural decision AD-025 remains authoritative: every Compose route owns a lifecycle-aware KMP ViewModel with typed intent dispatch, immutable state, explicit effects, and stateless visual content.
- Navigation architecture is established before route ownership is migrated; this specification consumes route entries but does not define them.
- Application state includes input that affects validation, requests, permissions, loading, errors, and business outcomes. Purely visual interaction state MAY remain local when it does not alter application behavior.
- Essential process-restorable state SHALL use `SavedStateHandle` or the approved KMP-equivalent restoration contract. Existing durable drafts remain the source of truth when they already provide stronger recovery.
- Immutable state is updated atomically. Production code SHALL NOT expose coroutine-scope or dispatcher replacement APIs solely for tests; dependencies needed for deterministic tests are constructor-provided.
- Lifecycle-aware collection SHALL be supported on both Android and iOS by the selected Compose Multiplatform lifecycle APIs.
- Existing design-system appearance is preserved. Removal or replacement of project-defined CompositionLocals SHALL preserve theme behavior.

## User Stories and Requirements

### P1 — Typed route MVI contract

**Story:** As a feature author, I need each route to expose one predictable state-and-intent contract so behavior is easy to reason about and test.

- **PMVI-001:** WHEN a navigable screen is migrated, THEN it SHALL define an immutable `State`, a sealed or otherwise exhaustive `Intent`, and an explicit one-off `Effect` contract when one-off outcomes exist.
- **PMVI-002:** WHEN UI input occurs, THEN the route or visual content SHALL dispatch a typed intent to one ViewModel entry point and SHALL NOT directly call repositories or data sources.
- **PMVI-003:** WHEN the ViewModel changes observable state, THEN it SHALL use an atomic immutable update and SHALL NOT mutate previously emitted state instances.
- **PMVI-004:** WHEN an operation begins or completes, THEN loading, content, empty, retryable failure, validation failure, and authorization outcomes SHALL be represented explicitly in state or effects according to whether they survive re-collection.
- **PMVI-005:** WHEN a result maps to user-visible text, THEN presentation SHALL use the approved UI-text abstraction and SHALL NOT expose raw transport exceptions or backend-internal diagnostics.
- **PMVI-006:** WHEN a one-off effect is emitted, THEN it SHALL have a single documented owner and SHALL NOT be duplicated after ordinary recomposition or lifecycle re-collection.
- **PMVI-007:** WHEN a ViewModel is constructed, THEN all business dependencies and test-relevant execution dependencies SHALL be constructor-provided through production-safe abstractions.
- **PMVI-008:** WHEN tests require deterministic scheduling, THEN they SHALL configure injected dispatchers or the test scheduler and SHALL NOT mutate a production ViewModel scope.

### P1 — Root and visual-content separation

**Story:** As a UI developer, I need rendering composables to be independent of DI, navigation controllers, and ViewModels so they can be previewed and tested as pure UI.

- **PMVI-009:** WHEN a route is rendered, THEN its root composable SHALL acquire the ViewModel, collect lifecycle-aware state, observe effects, and translate cross-feature outcomes into callbacks.
- **PMVI-010:** WHEN visual content is rendered, THEN a separate screen/content composable SHALL receive immutable state and callbacks only and SHALL NOT resolve DI dependencies or navigation controllers.
- **PMVI-011:** WHEN visual content needs to navigate, close, request permission, or invoke another feature, THEN it SHALL emit a semantic callback or typed intent rather than owning the external mechanism.
- **PMVI-012:** WHEN a screen supports materially distinct loading, empty, content, error, or permission states, THEN previews or screenshot fixtures SHALL cover those states where the platform tooling supports deterministic rendering.
- **PMVI-013:** WHEN a reusable composable accepts collections or callback holders, THEN its public parameters SHALL use stable immutable types or stable wrappers appropriate to Compose.
- **PMVI-014:** WHEN derived display data can be computed from state, THEN it SHALL be derived without copying a second mutable source of truth into local remembered state.
- **PMVI-015:** WHEN a composable registers a side effect, THEN the effect keys SHALL represent every value whose change requires restart, and current callbacks SHALL be safely captured without unnecessary restart.

### P1 — Lifecycle and restoration

**Story:** As a user, I need screens to stop unnecessary work when inactive and recover essential progress after process recreation.

- **PMVI-016:** WHEN observable state is collected by a route, THEN collection SHALL be lifecycle-aware on every supported platform.
- **PMVI-017:** WHEN a route leaves the active lifecycle state, THEN non-durable collectors and UI-owned work SHALL stop or suspend according to the approved lifecycle contract.
- **PMVI-018:** WHEN the process is recreated, THEN essential identifiers, in-progress user input required to resume the task, and selected business-relevant options SHALL be restored.
- **PMVI-019:** WHEN restored input no longer satisfies current validation or authorization rules, THEN the screen SHALL expose the normal corrective state and SHALL NOT silently submit stale data.
- **PMVI-020:** WHEN durable server- or repository-backed draft state exists, THEN restoration SHALL reconcile with that source instead of creating an independent competing truth.
- **PMVI-021:** WHEN a route is removed permanently, THEN route-scoped work and effects SHALL be cancelled and no stale effect SHALL target a later route instance.

### P1 — Compose behavior and accessibility

**Story:** As a user, I need stable, accessible screens that preserve interaction behavior throughout the refactor.

- **PMVI-022:** WHEN a lazy collection displays mutable or reorderable items, THEN each item SHALL have a stable unique key and, where content types differ materially, a stable content type.
- **PMVI-023:** WHEN a control is interactive, THEN it SHALL expose the correct semantic role, label, enabled state, and touch target without relying only on color or icon shape.
- **PMVI-024:** WHEN content or controls are loading or unavailable, THEN click handlers and accessibility semantics SHALL reflect the same enabled state.
- **PMVI-025:** WHEN animation or focus effects exist, THEN they SHALL preserve current accepted behavior, respect reduced-motion/accessibility expectations where applicable, and avoid launching duplicate work on recomposition.
- **PMVI-026:** WHEN a custom modifier or reusable UI primitive is retained, THEN it SHALL preserve modifier chaining, accessibility semantics, and stable behavior under recomposition.

### P2 — Explicit design-system dependencies

**Story:** As a design-system maintainer, I need UI dependencies to be visible and testable instead of hidden in project-defined ambient state.

- **PMVI-027:** WHEN a project-defined CompositionLocal supplies behavior, controller, service, or feature state, THEN it SHALL be replaced by an explicit parameter or approved owner.
- **PMVI-028:** WHEN a project-defined CompositionLocal supplies theme values, THEN the design SHALL either migrate it to explicit stable theme APIs or document why the Compose-provided ambient mechanism is required; feature-specific state SHALL never be placed there.
- **PMVI-029:** WHEN a visual composable is previewed or tested, THEN all required theme and behavior inputs SHALL be constructible without starting the application composition root.

## Edge Cases

- A one-off effect is emitted while the route is backgrounded or being removed.
- Process recreation occurs midway through a multi-step form.
- Restored identifiers reference content the user can no longer access.
- Rapid intents race with loading completion or route removal.
- A list reorders while an item has focus or an animation in progress.
- A callback changes while a long-lived Compose effect remains active.
- A platform lifecycle transitions differently between Android and iOS.
- A preview must represent an error containing localized or parameterized text.

## Implicit-Dimension Closure

- **Authentication and authorization:** Existing states are preserved by PMVI-004, PMVI-019, and the behavior-preservation goal; policy changes are out of scope.
- **Persistence and recovery:** Essential process restoration is covered by PMVI-018 through PMVI-020; new durable storage is out of scope.
- **External calls and idempotency:** Presentation triggers typed intents only; request semantics belong to the domain/data specification.
- **Concurrency and ordering:** Atomic updates, duplicate-effect prevention, cancellation, and rapid-intent cases are covered by PMVI-003, PMVI-006, PMVI-015, and PMVI-021.
- **Failure and retry:** Explicit failure and retry states are covered by PMVI-004 and PMVI-005.
- **Accessibility:** Covered by PMVI-023 through PMVI-026.
- **Migration and compatibility:** Existing product behavior and design-system appearance are explicit assumptions and success criteria.

## Traceability

| Requirement group | IDs | Planned downstream artifact |
|---|---|---|
| Typed MVI contract | PMVI-001–PMVI-008 | Contract design, ViewModel migration tasks, unit tests |
| Root/content separation | PMVI-009–PMVI-015 | Compose design rules, screen tasks, previews |
| Lifecycle/restoration | PMVI-016–PMVI-021 | Lifecycle design, restoration matrix, tests |
| Compose/accessibility | PMVI-022–PMVI-026 | UI migration tasks and Compose tests |
| Design-system dependencies | PMVI-027–PMVI-029 | Design-system decision and migration tasks |

All 29 requirements are assigned to a downstream design or task category. Task identifiers will be added only when this feature enters the Tasks phase.

## Success Criteria

- Every navigable feature route has an immutable State, typed Intent, explicit Effect when needed, and one ViewModel intent entry point.
- Every migrated route separates lifecycle/DI orchestration from stateless visual rendering.
- All route state collection is lifecycle-aware on Android and iOS.
- Essential task progress survives process recreation and reconciles with durable drafts where present.
- Production ViewModels expose no test-only scope mutation hooks.
- Migrated lazy lists, effects, controls, and modifiers satisfy the stability and accessibility requirements.
- Representative previews and tests render visual states without application-level DI or navigation setup.
- Existing accepted product behavior and appearance remain unchanged unless superseded by another confirmed specification.
