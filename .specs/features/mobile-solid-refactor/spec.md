# Mobile SOLID Refactor Specification

## Problem Statement

Mobile presentation and navigation code currently combines route ViewModels with
their contracts, UI composition, and unrelated helpers. This makes ownership
unclear and increases the cost of changing a route safely.

## Scope

This incremental feature separates the games-list route contracts from its
ViewModel and moves the groups visual host out of navigation without changing
observable behavior.

## Acceptance Criteria

1. **SOLID-01** - `GamesViewModel.kt` SHALL declare only `GamesViewModel`.
2. **SOLID-02** - The games-list state, intent, effect, item model, error type,
   and presentation mapper SHALL live in focused files in the same package.
3. **SOLID-03** - Existing games-list state transitions, authorization, and
   one-shot navigation effects SHALL remain unchanged.
4. **SOLID-04** - The groups route host SHALL live in a UI package; no file in
   `composeapp/navigation` SHALL compose `GroupsDestinationContent` or
   `GroupsRouteChrome`.
5. **SOLID-05** - `GroupsRouteChrome` SHALL compose only the shared route
   layout and navigation bars; destination-specific screen rendering SHALL live
   in `GroupsDestinationContent`.

## Out of Scope

- Changes to games-list behavior, API contracts, or navigation policy.
- Refactoring other ViewModels or navigation hosts beyond the groups route host.
- Adding a formatting toolchain.

## Verification

- Run the groups shared test task, including `GamesViewModelTest`.
- Confirm `GamesViewModel.kt` has no top-level contract declarations.
- Run the compose-app shared tests after moving the groups route host.
