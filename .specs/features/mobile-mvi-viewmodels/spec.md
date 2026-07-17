# Mobile MVI ViewModels Specification

**Status:** Approved
**Date:** 2026-07-17
**Scope:** Compose Multiplatform authentication/access presentation layer

## Problem Statement

The shared mobile presentation currently splits state and commands across
coordinators, an application runtime, composable-local state, and an
Android-only root ViewModel. A screen therefore has several command entry
points and no consistent lifecycle-aware state-machine boundary shared by
Android and iOS.

## Goals

- [ ] Give every authentication/access route one lifecycle-aware ViewModel in
  `commonMain`.
- [ ] Give each ViewModel exactly one public command entry point,
  `onIntent(Intent)`, backed by an explicit state machine.
- [ ] Keep visual composables stateless: render `UiState`, dispatch `Intent`,
  and consume one-shot `UiEffect` values at the route boundary.
- [ ] Preserve all accepted authentication/access behavior on Android and iOS.

## Out of Scope

| Item | Reason |
| --- | --- |
| Backend, HTTP, Firebase, Branch, or persistence contract changes | This is a presentation architecture migration. |
| Visual redesign or copy changes | Existing user-visible behavior must remain stable. |
| Process-death restoration for transient form fields | A separate persistence contract is required; durable session/group/invite behavior remains unchanged. |
| A single application-sized ViewModel | State is owned by the navigation screen whose lifecycle it follows. |
| Introducing a third-party MVI framework or DI container | AndroidX Lifecycle and the existing composition roots are sufficient. |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Migration scope | All currently implemented authentication/access screens | A mixed coordinator/ViewModel architecture would retain the ambiguity being removed. | Yes |
| ViewModel ownership | One ViewModel per real navigation route; the current authentication/access destinations are states within one route and therefore share one route ViewModel | Keeps lifecycle ownership aligned with actual Navigation Compose entries without duplicating session/group state. | Yes |
| Command API | Exactly one public `onIntent(Intent)` method | Implements the requested single entry point. | Yes, from user request |
| Outputs | `StateFlow<UiState>` and a one-shot `Flow<UiEffect>` | State renders repeatably; native transient actions are not persisted as state. | Yes |
| Root responsibilities | The access route ViewModel owns the access destination state machine; child visual screens receive only their state and typed intent callback | Current access destinations are state-derived views, not independent back-stack entries. | Yes |
| Lifecycle | AndroidX KMP ViewModel retrieved at the Compose route | AndroidX Lifecycle supports a shared ViewModel while Compose owns both platform UIs. | Yes |

**Open questions:** none.

## User Stories

### P1: Drive a screen through one state machine

**User Story:** As a mobile maintainer, I want each screen to accept only typed
intents so that every state transition has one discoverable and testable path.

**Acceptance Criteria:**

1. **MVI-01** - WHEN the access route creates its presentation owner THEN it
   SHALL obtain a `commonMain` AndroidX `ViewModel` scoped to that route.
2. **MVI-02** - WHEN UI or lifecycle input reaches a screen ViewModel THEN it
   SHALL enter through exactly one public function, `onIntent(Intent)`; the
   ViewModel SHALL expose no additional public command methods.
3. **MVI-03** - WHEN an intent is accepted THEN a typed state machine SHALL
   reduce it into an immutable `UiState` update and/or a declared side effect;
   asynchronous completions SHALL return through an internal state-machine
   message rather than mutate UI state from the screen.
4. **MVI-04** - WHEN a submit intent is repeated while the corresponding
   operation is pending THEN the state machine SHALL preserve the existing
   single-submit behavior and SHALL not start a duplicate operation.

**Independent Test:** Instantiate each ViewModel with fake ports, send only
typed intents, and assert the exact ordered states and invoked operations.

### P1: Render stateless Compose screens

**User Story:** As a mobile maintainer, I want composables to be renderers so
that presentation behavior does not split between Compose and the ViewModel.

**Acceptance Criteria:**

1. **MVI-05** - WHEN a route renders a screen THEN the visual screen composable
   SHALL receive `UiState` and an intent callback and SHALL not construct its
   own coordinator or own business/form state with `remember`.
2. **MVI-06** - WHEN a user interacts with the screen THEN the composable SHALL
   translate the interaction into one typed intent and invoke the provided
   intent callback.
3. **MVI-07** - WHEN a ViewModel emits navigation or another transient action
   THEN the route SHALL consume one typed `UiEffect` once; recomposition SHALL
   not repeat the effect.

**Independent Test:** Render each screen from fixture states and assert that UI
actions dispatch the specified intent without directly invoking a port.

### P1: Preserve lifecycle and access behavior

**User Story:** As an Android/iOS user, I want the migration to preserve my
current authentication and group-access journey across normal lifecycle events.

**Acceptance Criteria:**

1. **MVI-08** - WHEN Compose recomposes or Android recreates configuration while
   a route remains on the back stack THEN the same route ViewModel SHALL retain
   its current state and pending-operation guard.
2. **MVI-09** - WHEN a route is permanently removed THEN its ViewModel SHALL be
   cleared and its coroutine work/listeners SHALL be cancelled exactly once.
3. **MVI-10** - WHEN existing login, registration, verification, bootstrap,
   group selection/creation/administration, invite, retry, and logout scenarios
   run THEN their specified visible outcomes SHALL remain unchanged on Android
   and iOS.

**Independent Test:** Run state-machine unit tests, shared Compose tests, and
the existing Android/iOS lifecycle suites without changing expected behavior.

## Edge Cases and Implicit-Requirement Sweep

| Dimension | Resolution |
| --- | --- |
| Input validation & bounds | Existing validation and field-preservation outcomes remain unchanged and move behind typed intents. |
| Failure / partial failure | Pending and error states are explicit `UiState` values; transient navigation/notification outcomes are `UiEffect` values. |
| Idempotency / retry / duplicates | Pending submit intents are ignored; existing request IDs and retry rules remain authoritative. |
| Auth boundaries & rate limits | N/A because backend/native authorization and throttling contracts are unchanged. |
| Concurrency / ordering | State reduction is serialized; stale async completions SHALL not overwrite a newer screen state. |
| Data lifecycle / expiry | Screen state lives until its route is removed; durable session, selected group, and invite lifetimes remain unchanged. |
| Observability | N/A because this refactor adds no production telemetry contract. |
| External-dependency failure | Existing provider/network failure states and retry behavior remain unchanged. |
| State-transition integrity | Every reachable state and guarded transition is covered through the single public intent entry point. |

## Requirement Traceability

| Requirement ID | Story | Status |
| --- | --- | --- |
| MVI-01 | Drive a screen through one state machine | In Tasks |
| MVI-02..04 | Drive a screen through one state machine | Implementing |
| MVI-05..07 | Render stateless Compose screens | In Tasks |
| MVI-08..10 | Preserve lifecycle and access behavior | In Tasks |

**Coverage:** 10 requirements, 0 mapped to tasks until Design is confirmed.

## Success Criteria

- [ ] Every migrated screen ViewModel has only `onIntent` as a public command.
- [ ] Every migrated screen's observable transitions are proven through
  intent-driven unit tests.
- [ ] Visual composables contain no business/form state that belongs to their
  ViewModel.
- [ ] Relevant shared, Android, and iOS gates pass without weakening tests.

## §V Verification Invariants

- **V1** - Every test-owned long-lived ViewModel collector SHALL run in the
  test's `backgroundScope` or be explicitly cancelled, so the relevant gate
  terminates without live child jobs.

## §B Backpropagation Log

| ID | Date | Failure | Root cause | Invariant |
| --- | --- | --- | --- | --- |
| B1 | 2026-07-17 | `AccessViewModelTest` did not compile for Kotlin/Native | Expected singleton lists inferred concrete sealed subtypes instead of the declared `AccessRuntimeIntent` supertype | None; mechanical common-test typing issue with no product-behavior class to constrain |
| B2 | 2026-07-17 | Quick Compose waited on ViewModel collectors owned by `runTest` | The fixture passed the foreground `TestScope` to eager `stateIn` and reconciliation collectors | V1 |
