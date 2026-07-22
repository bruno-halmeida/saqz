# Groups Bottom Menu Tasks

Spec: `.specs/features/groups-bottom-menu/spec.md`

## T01 — Restyle `SaqzBottomNav`

- Rounded top corners on the bar surface; remove hairline and selection
  indicator bar; icon above label; selection via primary color; keep
  `selected` semantics and bottom inset handling.
- Files: `core/design-system/.../component/SaqzBottomNav.kt`,
  `core/design-system/.../component/SaqzBottomNavTest.kt`.
- Requirements: MENU-02, MENU-06.
- Gate: design-system tests
  (`./gradlew :core:design-system:allTests` in `mobile/`).

## T02 — Navigation model: NOTICES and MORE destinations

- Add `NOTICES`, `MORE` to `GroupsDestination`; `OpenNotices`/`OpenMore`
  intents; include both in `showsGroupChrome()`/`isGroupScoped()`; allow both
  in `isAllowed`; handle intents in `GroupsNavigationViewModel`; relax
  `openGroups()` to open the selector with any membership count.
- Files: `GroupsDestination.kt`, `GroupsNavigationIntent.kt`,
  `GroupsNavigationDestinationPolicy.kt`, `GroupsNavigationViewModel.kt`,
  `GroupsNavigationViewModelTest.kt`.
- Requirements: MENU-03, MENU-04.
- Gate: compose-app common tests for the navigation ViewModel.

## T03 — Resources: labels, placeholder copy, icons

- Strings: `nav_groups` (Grupos), `nav_notices` (Avisos), `nav_more` (Mais),
  notices placeholder body. Drawables: outline bell (`material_notifications`)
  and outline people (`material_group`) matching the existing 1.8 stroke
  style.
- Files: `features/groups/.../composeResources/values/strings.xml`,
  `composeResources/drawable/material_notifications.xml`,
  `composeResources/drawable/material_group.xml`.
- Requirements: MENU-01, MENU-04.
- Gate: groups feature compiles (`:features:groups:compileKotlinAndroid`
  or equivalent).

## T04 — Fixed five-item menu and new screens

- `GroupBottomMenu`: fixed five items (Início, Jogos, Grupos, Avisos, Mais)
  with new icons; remove role-gated item building. Top bar titles for
  NOTICES/MORE. `GroupsDestinationContent`: notices placeholder page; Mais
  shortcut screen (Pessoas gated by `showPeople`; finance shortcut labeled by
  `financeDestination`).
- Files: `GroupsNavigationChrome.kt`, `GroupsDestinationContent.kt`,
  `GroupsRouteScreens.kt`, `GroupsNavigationTags.kt` (new screen tags).
- Requirements: MENU-01, MENU-02, MENU-04, MENU-05.
- Gate: groups feature tests.

## T05 — App-level test updates

- Update `GroupsRouteHostTest` (five tabs, Grupos intent, Avisos/Mais
  navigation, Mais shortcuts), `SaqzAppShellTest` if the shell nav rendering
  changed, and any catalog fixtures affected by the bar restyle.
- Files: `compose-app/src/commonTest/.../GroupsRouteHostTest.kt`,
  `shell/SaqzAppShellTest.kt`, catalog fixtures if needed.
- Requirements: MENU-01..MENU-06.
- Gate: compose-app tests.

## T06 — Gates and validation

- Run `rtk scripts/check-gradle`, `rtk scripts/check-credentials`,
  `rtk scripts/check-scope`; record evidence in
  `.specs/features/groups-bottom-menu/validation.md`; update spec status and
  STATE handoff (uncommitted STATE.md already carries unrelated edits — leave
  it out of commits).
