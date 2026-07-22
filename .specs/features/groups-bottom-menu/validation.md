# Groups Bottom Menu Validation

Date: 2026-07-21
Spec: `.specs/features/groups-bottom-menu/spec.md`

## Evidence

| Requirement | Evidence | Result |
| --- | --- | --- |
| MENU-01 | `GroupsRouteHostTest.bottom menu renders five fixed tabs for every role` (athlete renders Início, Jogos, Grupos, Avisos, Mais) | Pass |
| MENU-02 | `selected group home renders one shared top bar and bottom menu`, `game detail keeps games selected and shows its exact title`, `notices destination selects its tab...`; selected semantics asserted in `SaqzBottomNavTest.selectedStateIsAnnounced` | Pass |
| MENU-03 | `bottom menu emits each fixed tab intent once` emits `OpenGroups`; `GroupsNavigationViewModelTest.single membership still opens the selector from groups tab` | Pass |
| MENU-04 | `notices destination selects its tab and shows the placeholder` (title Avisos, tab 3 selected, placeholder copy) | Pass |
| MENU-05 | `more destination lists owner shortcuts and emits typed intents` (OpenPeople/OpenFinance); `more destination hides people and labels own charges for athletes` | Pass |
| MENU-06 | `SaqzBottomNavTest.baseHeightIs56Dp`, `bottomInsetIsAdded`; hairline/indicator removed from `SaqzBottomNav.kt`, bar clipped with 24dp top radius | Pass |

## Gates

- `rtk ./gradlew :features:groups:allTests :compose-app:allTests` (mobile/) — BUILD SUCCESSFUL, 154 compose-app tests, 0 failed.
- `rtk scripts/check-credentials` — ok.
- `rtk scripts/check-scope` — ok.
- `rtk scripts/check-gradle` (JDK 21 via `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/...`) — ok; 55 Android instrumented tests, 0 failed/skipped.

## Notes

- Concurrent session work (`mobile-solid-refactor-wave-2`) renamed the selector
  title to "Meus grupos"; the `GroupsRouteHostTest` selector assertion was
  updated to match the current worktree copy. That session's files
  (`GroupsRouteScreens.kt`, extra strings) were left uncommitted and untouched.
- `.specs/STATE.md` and `README.md` carry unrelated uncommitted edits and were
  intentionally excluded from every commit of this feature.
