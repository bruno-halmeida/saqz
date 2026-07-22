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

## Placement Amendment Validation (2026-07-22)

**Verdict: PASS**

The final user clarification establishes a four-item menu: Início, Grupos,
Avisos, and Mais. Jogos is intentionally absent from the menu and remains
reachable inside group context. The isolated gate and discrimination sensor
both pass against the current amendment behavior.

### Spec-Anchored Evidence

| Requirement | Spec-defined outcome | Exact test evidence | Result |
| --- | --- | --- | --- |
| MENU-07 | The selector menu renders directly below the group list with exactly four items ordered Início, Grupos, Avisos, Mais; Grupos exposes selected semantics. | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/ui/groups/GroupsRouteHostTest.kt:157-160` - exact indexed `assertTextEquals` outcomes; `:210-212` - menu displayed, Início not selected, Grupos selected; `:213-217` - `assertEquals(4, ...Role.Tab...size)`; `:218-221` - `assertEquals(List.bounds.bottom, BottomMenu.bounds.top)`. | PASS |
| MENU-08 | Every group-scoped destination omits the bottom menu. | `GroupsRouteHostTest.kt:103-113` enumerates HOME, PROFILE_COMPLETION, PEOPLE, GAMES, GAME_DETAIL, FINANCE, OWN_CHARGES, NOTICES, and MORE; `:114-115` updates each state and executes `onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()`. | PASS |
| MENU-09 | Each selector item emits its existing typed intent exactly once. | `GroupsRouteHostTest.kt:131-134` performs one click per item; `:136-144` executes `assertEquals(listOf(OpenHome, OpenGroups, OpenNotices, OpenMore), intents)`, proving exact values, order, one emission each, and no extras. | PASS |
| MENU-10 | Authenticated product content starts on the "Home screen" placeholder with the four-item menu selecting Início. | `mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/AuthenticatedAccessRootTest.kt:351-353` - home displayed, item 0 `assertIsSelected()`, selector content absent; four-item composition is asserted by `GroupsRouteHostTest.kt:157-160,213-217` on the same shared menu. | PASS |
| MENU-11 | Activating Grupos from authenticated home renders the selector with Grupos selected when memberships exist, or onboarding when none exist. | Membership path: `AuthenticatedAccessRootTest.kt:355-360` - click item 1, exact `OpenGroups` list, home absent, Alpha present, item 1 selected. No-membership path: `:369-383` - click item 1, home absent, "Criar grupo" exists. | PASS |
| MENU-12 | Activating Início from the selector returns to authenticated home without changing group selection. | `AuthenticatedAccessRootTest.kt:362-365` - click item 0, home displayed, and `assertEquals(listOf(OpenGroups), groupsIntents)` proves no additional navigation/selection intent was emitted. | PASS |
| MENU-13 | Every menu has exactly Início, Grupos, Avisos, Mais in that order; Jogos is absent from the menu but reachable in group context. | Composition/count: `GroupsRouteHostTest.kt:157-160,213-217`. Group-context Jogos path: `:504-508` clicks `ShortcutGames` and executes `assertEquals(listOf(OpenGames), intents)`. | PASS |

**Evidence-or-zero result:** 7/7 current amendment requirements have precise
direct assertion evidence matching the spec-defined outcomes.

### Implementation Inspection

- `GroupsNavigationChrome.kt:94-97` places weighted selector content before one
  bottom menu. `GroupsNavigationChrome.kt:131-156` defines exactly the four
  specified items, selected states, and typed callbacks in order.
- `GroupsRouteHost.kt:55-68` uses mutually exclusive selector, group-scoped,
  and chrome-free branches. `GroupsRouteChrome` contains no menu
  (`GroupsNavigationChrome.kt:68-85`), so no duplicate or group-scoped menu path
  was found.
- Authenticated home reuses `GroupsSelectorChrome` with destination HOME
  (`AuthenticatedAccessRoot.kt:754-764`), while the intent handler toggles home
  and selector without changing selection (`AuthenticatedAccessRoot.kt:451-463`).
- The no-membership path remains onboarding, and deferred game detail removes
  authenticated home; both are covered at `AuthenticatedAccessRootTest.kt:369-415`.
- `AuthenticatedHomeScreen.kt:18-27` renders the placeholder, backed by
  `strings.xml:4` with the exact value "Home screen".

### Gate Outcomes

- Isolation: a detached scratch worktree received the current tracked dirty
  amendment files plus the untracked `AuthenticatedHomeScreen.kt`. File hashes
  matched the real workspace before and after the isolated run.
- Command: `rtk env JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home" ./gradlew :compose-app:allTests` from isolated `mobile/`.
- Result: BUILD SUCCESSFUL; **161 tests, 161 passed, 0 failed, 0 errors, 0 skipped**.
- Focused classes: `GroupsRouteHostTest` 28/28 passed;
  `AuthenticatedAccessRootTest` 29/29 passed.
- Two live-workspace author attempts previously ended in report-writer EOF
  while Android Studio Gradle workers were active. The isolated run produced
  complete XML and passed, so that contention is recorded as infrastructure,
  not an amendment behavior failure.
- The unchanged full `check-gradle` blocker remains unrelated:
  `AndroidAuthenticatedLifecycleTest.kt:84` and `:314` cannot resolve
  `NetworkEnvironment`. It was not changed or treated as a feature failure.

### Discrimination Sensor

- Mutation in isolated state only: removed the Mais item from
  `GroupBottomMenu`, changing the required four-item menu to three items.
- Focused command: `rtk env JAVA_HOME="/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home" ./gradlew :compose-app:iosSimulatorArm64Test --tests 'br.com.saqz.composeapp.ui.groups.GroupsRouteHostTest'`.
- Result: **KILLED**; 28 tests ran and 3 relevant tests failed:
  `bottom menu emits each fixed tab intent once`,
  `group list bottom menu renders four fixed tabs`, and
  `group list renders its top bar and selected groups menu`.
- The sensor's menu source and focused test hashes were identical to the final
  isolated baseline. Both scratch worktrees and all mutations were discarded.

### Diff Surface

- Current amendment-relevant tracked changes reviewed and isolated:
  `.specs/features/groups-bottom-menu/spec.md`, compose resource strings,
  `AuthenticatedAccessRoot.kt`, `AuthenticatedAccessRootTest.kt`,
  `GroupsRouteHostTest.kt`, and `GroupsNavigationChrome.kt`.
- The required untracked `AuthenticatedHomeScreen.kt` was included. The
  unchanged `GroupsRouteHost.kt` was independently re-inspected.
- Other dirty files belong to unrelated concurrent work and were preserved.
- This verifier edited only
  `.specs/features/groups-bottom-menu/validation.md`; no source, test, or spec
  file was edited.

### Ranked Gaps

None.

## Authenticated Start And Four-Tab Supersession (2026-07-22)

**Verdict: PASS for MENU-07 through MENU-13 as superseded by the confirmed
four-tab amendment.** This section independently corroborates the Placement
Amendment PASS verdict above; the earlier evidence remains as decision history.

The user confirmed that the latest four-item menu is intentional. Its fixed
order is Início, Grupos, Avisos, Mais; Jogos remains available from group
context and is no longer a bottom-menu item.

| Requirement | Evidence | Result |
| --- | --- | --- |
| MENU-07, MENU-09, MENU-13 | `GroupsRouteHostTest.group list bottom menu renders four fixed tabs`, `bottom menu emits each fixed tab intent once`, and `group list renders its top bar and selected groups menu` assert exact order, four tab semantics, typed intents, placement, and selected Grupos state. | Pass |
| MENU-08 | `GroupsRouteHostTest.every group scoped destination omits bottom menu` covers every group-scoped destination. | Pass |
| MENU-10 | `AuthenticatedAccessRootTest.authenticated product starts on home and switches to the group selector` asserts the `Home screen` placeholder and selected Início tab. | Pass |
| MENU-11 | The same test covers selector entry with memberships; `groups from authenticated home preserves no membership onboarding` covers the zero-membership branch. | Pass |
| MENU-12 | The authenticated-start test returns from the selector to Home without forwarding a group-selection-changing intent. | Pass |

Additional regression evidence:

- `AuthenticatedAccessRootTest.deferred game detail replaces authenticated home`
  proves an accepted deferred game-detail destination dismisses the Home
  placeholder instead of remaining hidden behind it.
- `rtk ./gradlew :compose-app:iosSimulatorArm64Test --tests
  'br.com.saqz.composeapp.navigation.AuthenticatedAccessRootTest' --tests
  'br.com.saqz.composeapp.ui.groups.GroupsRouteHostTest'` passed 57 tests.
- `rtk ./gradlew :compose-app:allTests` completed successfully.
- `rtk git diff --check` passed.
- `rtk scripts/check-gradle` remains blocked by unrelated pre-existing unresolved
  `NetworkEnvironment` references in
  `AndroidAuthenticatedLifecycleTest.kt:84,314`; all preceding backend and KMP
  work in that gate passed.

Independent fresh-eyes review found no high- or medium-severity regression in
the authenticated Home, four-tab order, selector/Home transitions,
zero-membership onboarding, or accepted game-detail presentation. A pre-existing
deferred-link reconciliation race remains outside this amendment: an
`OpenAttendanceGame` intent can be rejected if emitted before Groups has a
selected `groupId`.
