# Mobile MVI ViewModels Context

**Gathered:** 2026-07-17
**Spec:** `.specs/features/mobile-mvi-viewmodels/spec.md`
**Status:** Approved; ready for design

## Feature Boundary

Migrate the existing Compose Multiplatform authentication/access presentation
layer to screen-scoped AndroidX KMP ViewModels with a single typed intent entry
point, immutable state, explicit one-shot effects, and stateless screen
composables. Preserve all accepted product behavior and native adapter edges.

## Implementation Decisions

### Screen Contract

- One lifecycle-aware `ViewModel` per real navigation route. The existing
  authentication/access destinations remain states inside their current route.
- The only public command is `onIntent(Intent)`.
- Repeatable rendering data is exposed as immutable `StateFlow<UiState>`.
- Navigation and truly transient actions are exposed as typed `UiEffect`
  values and consumed once by the route.
- Async results enter an internal reducer message; they do not create more
  public ViewModel methods.

### Compose Boundary

- Route composables retrieve/own ViewModels and collect their outputs.
- Visual screen composables receive state plus `onIntent` and stay stateless
  with respect to form/business behavior.
- Preview and UI tests use fixture state and an intent recorder without a real
  ViewModel.

### Lifecycle Boundary

- ViewModel scope follows the access route on Android and iOS Compose.
- Recomposition does not recreate state; removal from the back stack clears
  the ViewModel and cancels its work/listeners.
- The access route ViewModel owns session/bootstrap plus its state-derived
  destinations; future independent Navigation Compose routes get independent
  ViewModels.

### Agent's Discretion

- Internal reducer/message naming and whether a small shared MVI base type is
  justified after at least two concrete screen migrations.
- Factory wiring details that preserve the current explicit dependency
  composition and avoid a DI framework.

### Declined / Undiscussed Gray Areas → Assumptions

- The migration covers all implemented authentication/access screens, rather
  than leaving mixed coordinator and ViewModel presentation contracts.
- One-shot effects use a non-replaying stream and are not encoded as nullable
  state that the UI must manually clear.
- Transient form state is retained across recomposition/configuration but not
  promised across process death.

## Specific References

- User direction: "viewModel style with the MVI pattern" and "only 1 entry
  point with state machine on screen side."
- Existing accepted product behavior remains defined by
  `.specs/features/authentication-access/`.
- `AD-018` keeps product UI Compose-first on Android and iOS.

## Deferred Ideas

- Process-death restoration for transient form fields.
- A reusable MVI framework beyond the minimum types proven necessary here.
- Third-party dependency injection.
