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
6. **SOLID-06** - `GameEditorViewModel.kt` SHALL declare only
   `GameEditorViewModel`; its models, MVI contracts, draft port, validation,
   and command mapping SHALL live in focused files in the same package.
7. **SOLID-07** - `AccessViewModel.kt` SHALL declare only `AccessViewModel`;
   its intents, UI state, UI effects, runtime contract, runtime intents, and
   route/core helper state types SHALL live in focused files in the same
   package.
8. **SOLID-08** - `GameDetailViewModel.kt` SHALL declare only
   `GameDetailViewModel`; its lifecycle and attendance actions, error, command
   key factory, state, MVI contracts, and attendance operation SHALL live in
   focused files in the same package.
9. **SOLID-09** - `GroupSetupViewModel.kt` SHALL declare only
   `GroupSetupViewModel`; its mode, input, error, state, MVI contracts, command
   key factory, and pure setup helpers SHALL live in focused files in the same
   package without changing photo, timezone, or draft behavior.
10. **SOLID-10** - `FinanceViewModel.kt` SHALL declare only
   `FinanceViewModel`; `FinanceOperation` SHALL live in a focused file in the
   same package without changing charge behavior or API.
11. **SOLID-11** - `ExpenseViewModel.kt` SHALL declare only
    `ExpenseViewModel`; `ExpenseOperation` SHALL live in a focused file in the
    same package without changing expense behavior or public API.
12. **SOLID-12** - `GroupsNavigationContract.kt` SHALL not aggregate destination,
    access, state, intent, effect, tags, and destination policy in one file;
    each concern SHALL live in a focused file in the same package.
13. **SOLID-13** - `FinanceChargeSupport.kt` SHALL not aggregate finance draft,
    port, factory, error, state, intent, effect, totals, and rules in one
    file; each concern SHALL live in focused files in the same package.

## Out of Scope

- Changes to games-list behavior, API contracts, or navigation policy.
- Refactoring other ViewModels or navigation hosts beyond the groups route host.
- Adding a formatting toolchain.

## Verification

- Run the groups shared test task, including `GamesViewModelTest`.
- Confirm `GamesViewModel.kt` has no top-level contract declarations.
- Run the compose-app shared tests after moving the groups route host.
