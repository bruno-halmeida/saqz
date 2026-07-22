# Mobile Navigation Architecture Context

**Gathered:** 2026-07-22
**Spec:** `.specs/features/mobile-navigation-architecture/spec.md`
**Status:** Ready for design

---

## Feature Boundary

Introduce a first-level KMP module `:navigation` (`mobile/navigation/`) hosting `AccessNavigationHost`, `GroupsNavigationHost`, `FinanceNavigationHost`, their Navigation Compose 3 `NavDisplay` composition, and navigation handlers. Navigation Compose 3 Multiplatform is the single source of truth for mobile routes through app-owned serializable back stacks. Screens and route contracts remain in their respective feature modules; no feature depends on `:navigation`. `:compose-app` remains the composition root and the only framework exported to iOS. This is a pure refactor: no behavioral change beyond the explicitly approved back-stack semantics (e.g., GameDetail back returns to Games).

The feature starts only after `mobile-solid-refactor-wave-2` is verified complete, because T21–T23 of that wave create `AccessOrchestrator` and slim `AuthenticatedAccessRoute` — the exact files this migration rewrites.

---

## Implementation Decisions

### Module placement and ownership

- **`mobile/navigation/`** is a first-level KMP module (sibling of `core/` and `features/`), not under `core/` (which is Compose-agnostic) nor `features/` (which is domain-owned). Navigation is a first-level infrastructure concern that stitches features together.
- `:navigation` depends on `:features:groups` and `:features:access` (for route contracts and screens), `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1`, `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0`, and `:core:design-system` (if chrome is needed). Features never depend on `:navigation` or Navigation Compose 3 UI.
- Feature route-contract source sets use the lightweight Navigation Compose 3 key contract needed to implement `NavKey`; they remain independent of the Navigation Compose 3 UI implementation and of `:navigation`.
- `:compose-app` depends on `:navigation` and remains the composition root, wiring VMs/gateways via Koin (AD-028) and exporting the iOS umbrella framework.

### What lives where

| Artifact | Home | Reason |
| --- | --- | --- |
| `AccessNavigationHost`, `GroupsNavigationHost`, `FinanceNavigationHost` | `:navigation` | Product Navigation Compose 3 displays are infrastructure stitching features. |
| Navigation handlers (intent/effect → back-stack mutation) | `:navigation` | Centralizes back-stack decisions; no feature imports Navigation Compose 3 UI APIs. |
| `BackHandler` (system back) | `:navigation` (inside the relevant navigation host) | Unifies with TopBar back by removing the last key from the same app-owned stack. |
| Route contracts (`GroupsRoute`, access routes, finance routes) | `:features:groups` / `:features:access` | Domain-owned `@Serializable sealed interface` hierarchies implementing `NavKey`; no dependency on `:navigation` or Navigation Compose 3 UI. |
| Screens (`GroupDetailScreen`, `LoginScreen`, etc.) | `:features:groups` / `:features:access` | AD-026 boundaries preserved. |
| Home/Catalog navigation host (currently `SaqzNavHost`) | `:compose-app` | References `SaqzHomeScreen` / `SaqzCatalogScreen`; stays app-local but migrates to Navigation Compose 3 `NavDisplay` so legacy `navigation-compose:2.9.2` can be removed. |
| VM wiring, Koin modules, photo coordinator, network client | `:compose-app` | Composition root owns lifecycle and DI (AD-028). |

### What is eliminated

- `showAppHome` (`rememberSaveable` overlay in `AuthenticatedAccessRoot.kt:451-463, 749-761`) — replaced by a real `AppHome` key in `GroupsNavigationHost`.
- `AccessPage` enum (`AuthenticatedAccessRoot.kt:139-145`) — replaced by real routes (`Settings`, `Memberships`, `Invite`, `CreateGroup`).
- `AccessDestinationStack` (`AuthenticatedAccessRoot.kt:163-170`) — already a trivial single-entry list; removed.
- `handleGroupsIntent` wrapper (`AuthenticatedAccessRoot.kt:452-463`) — replaced by a navigation handler in `:navigation`.
- `GroupsNavigationEffect` (`GroupsNavigationEffect.kt`) — dead in production (never collected in `commonMain`); removed.
- `GroupsNavigationState.destination` / `gameId` / `requestedGroupId` — app-owned Navigation Compose 3 back stacks become the source of truth.
- Legacy `navigation-compose:2.9.2` (`NavController`, `NavHost`) — removed after the app-local Home/Catalog graph also migrates to Navigation Compose 3.

### Bottom-nav tab stacks

- Each validated bottom-nav tab (`AppHome`, `Grupos`/`Selector`, `Avisos`/`Notices`, `Mais`/`More`) owns a separate serializable Navigation Compose 3 back stack. Switching tabs changes the active stack; reselecting a tab does not append a duplicate root key. `Games` remains inside group context and participates in the Groups stack rather than becoming a fifth tab.
- Back pops the active stack when it is deeper than its root. At the root of Grupos, Avisos, or Mais, back selects Início. At the Início root, the navigation layer leaves back handling to the platform.
- Reselecting the already-selected tab does not duplicate its entry.

### Transient routes (Loading / LoadError / reconciliation)

- `Setup`, `Selector`, `Loading`, `LoadError` are routes driven by `GroupSelectionState`. Reconciliation explicitly replaces transient keys in the app-owned stack so back never re-enters an obsolete transient state.
- Reconciliation is idempotent: comparing the current route before navigating prevents duplicate entries when external state and user actions race.

### Route restoration scope

- Tab and back stacks are restored while the auth session and the selected group remain valid (session-scoped).
- Logout clears all authenticated stacks; group switch clears group-scoped stacks and ViewModels.
- After process death, the graph recreates and reconciles with the current `GroupSelectionState` before showing any route.

### Navigation Compose 3 serialization across KMP targets

- Compose Multiplatform 1.10+ supports Navigation Compose 3 on Android, iOS, desktop, and web; the workspace uses Compose Multiplatform 1.11.1.
- The selected KMP UI artifact is `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1`.
- JVM reflection-based key serialization is prohibited because it is unavailable on iOS.
- `:navigation` owns one `SavedStateConfiguration` with a polymorphic `SerializersModule` that aggregates feature-owned sealed route hierarchies (for example, `subclassesOfSealed<AccessRoute>()`, `subclassesOfSealed<GroupsRoute>()`, `subclassesOfSealed<FinanceRoute>()`).
- Every route key round-trips in shared tests; missing serializer registration is a build/test failure, not a runtime fallback.

### iOS cold-relaunch persistence

- `rememberNavBackStack` with the shared `SavedStateConfiguration` is the primary restoration mechanism on every target.
- An early compatibility test verifies whether a complete iOS cold relaunch restores the session-scoped stacks.
- If library saved state is insufficient, `:navigation` defines a provider-neutral `NavigationSnapshotStore` port; Kotlin platform adapters persist the serialized snapshot without adding Swift navigation code.
- A restored snapshot is applied only after auth, membership, selected-group, and access-policy reconciliation. Logout and group switch remove the corresponding persisted snapshot before the navigation host is disposed.

### Authorization fallback

- When role/profile change invalidates the current route, the system pops to the previous allowed route.
- If no previous route is allowed, it uses `GroupHome`; if the membership was lost, it uses `Selector` or `Setup`.

### Auth flow included

- `AccessNavigationHost` represents `Starting`, `Login`, `Registration`, `PasswordReset`, `Verification`, `NameCompletion`, and `Bootstrap` as serializable `NavKey` routes rendered through `NavDisplay`.
- Today's state-driven transitions (SignedOut → AwaitingVerification → CompletingName → Bootstrapping → Ready) reconcile the current route idempotently.
- When the session becomes `Ready` and a group is selected, `NavigationSession` switches the active entries from Access to the authenticated Groups tab set instead of pushing a Groups key onto the access stack.

### FinanceNavigationHost is structural only

- `FinanceNavigationHost` represents `Finance` and `OwnCharges` as `NavKey` routes rendered by `NavDisplay`, preserving the current placeholder content (`RoutePage`).
- `FinanceScreen` and `ExpenseScreen` (already in `:features:groups`) remain disconnected until a separate feature explicitly approves their activation.

### Route-ViewModel ownership (AD-025 preserved)

- Each route owns its lifecycle-aware ViewModel scoped to its Navigation Compose 3 entry using `lifecycle-viewmodel-navigation3` support. The exact decorator/factory API is confirmed in Design against the official 2.10.0 documentation.
- ViewModels emit typed navigation effects (e.g., `GameDetailEffect.OpenGame(gameId)`); they do not import Navigation Compose 3 UI types or `:navigation`.
- A handler in `:navigation` collects these effects at the route entry and translates them to mutations of the appropriate app-owned back stack.
- Screens remain stateless, receiving only state and `onIntent`.

### Dependency on Wave 2

- Implementation starts only after `mobile-solid-refactor-wave-2` is verified complete.
- T21 creates `AccessOrchestrator` (Kotlin-pure, non-composable) absorbing reconciliation from `AccessRuntime` + `LaunchedEffect`s.
- T22 removes the second network graph and serves game-detail from the single Koin graph.
- T23 slims `AuthenticatedAccessRoute` to collect orchestrator state and dispatch intents.
- Starting this migration before T21–T23 would cause two refactors to fight over `AuthenticatedAccessRoot.kt`.

### Verification cadence

- Every implementation task owns the tests derived from the acceptance criteria it changes and runs only the narrowest relevant module/layer suites. Tests are never deferred to a later task and are never removed or weakened.
- Intermediate tasks do not run repository-wide aggregates. Cross-module tasks run only the directly affected module suites required to prove their own outcome.
- The last task is an explicit integration and verification task. It runs `rtk scripts/check-all`, fixes all failures attributable to this feature, reruns the narrow suites affected by each adjustment, and repeats `rtk scripts/check-all` until green.
- A failure proven pre-existing or unrelated is documented with evidence and does not authorize edits outside this feature's scope.

### Agent's Discretion

- Exact file layout inside `:navigation` (package structure, handler naming).
- Whether the three conceptual navigation hosts share one `NavDisplay` with delegated entry providers or compose nested `NavDisplay`s (Design decides, preserving independent ownership and stack semantics).
- Exact shape of the typed navigation effects per feature (Design decides, preserving the "ViewModel does not import Navigation Compose 3 UI" contract).

### Declined / Undiscussed Gray Areas → Assumptions

- None declined. All gray areas were discussed and resolved in the Specify phase.

---

## Specific References

- Official Navigation Compose 3 KMP documentation: `https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-3.html` (updated 2026-07-07) — Compose Multiplatform 1.10+ support for Android/iOS/desktop/web, `navigation3-ui:1.1.1`, user-owned stacks, `NavDisplay`, and explicit `SavedStateConfiguration` for iOS.
- Existing route model to migrate: `SaqzDestination` (`mobile/compose-app/.../navigation/SaqzDestination.kt`) — serializable route objects become `NavKey`s.
- Existing legacy navigation behavior used only as regression evidence: `SaqzNavHost.navigateTopLevel` (`mobile/compose-app/.../navigation/SaqzNavHost.kt:33-38`) — idempotent reselection and state restoration must remain after migration, but its legacy `NavController` implementation is not reused.
- Active decisions to conform to: AD-001 (umbrella framework — `:navigation` types do not leak to iOS API), AD-013 (partially superseded by AD-029), AD-018 (Compose-first; navigation is common Compose, no native navigation), AD-025 (each route owns its ViewModel), AD-026 (finance stays in Groups feature), AD-028 (Koin DI), AD-029 (Navigation Compose 3 module ownership).
- Dependencies to add: `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1` and `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0`; `org.jetbrains.androidx.navigation:navigation-compose:2.9.2` is removed after Home/Catalog migrates.

---

## Deferred Ideas

- Activating `FinanceScreen` / `ExpenseScreen` with real data — separate product feature after this migration lands.
- Segregating `OrganizerFinanceGateway` / `NativeAuthPort` / `AccessRuntimeContract` fat interfaces — Wave 3 candidate (already deferred in `mobile-solid-refactor-wave-2/context.md`).
- Decomposing `AccessViewModel` per feature and collapsing `AccessIntent` / `AccessRuntimeIntent` — Wave 3 candidate.
- Moving the Home/Catalog navigation host from `:compose-app` to `:navigation` — only if a second aggregator appears that needs it.
- A `features/finance` module — only if finance outgrows the Groups feature boundary (would require superseding AD-026).
