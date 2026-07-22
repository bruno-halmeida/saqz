# Groups Bottom Menu Specification

## Problem Statement

The group bottom menu exposes role-gated destinations (Início, Jogos, Pessoas,
Financeiro) with a flat hairline-topped bar. The approved visual direction is a
fixed five-tab menu (Início, Jogos, Grupos, Avisos, Mais) on a rounded-top bar
where selection is shown by the primary-colored icon and label, matching the
approved reference image. Pessoas and Financeiro move behind a Mais shortcut
screen, and Avisos lands as a placeholder destination until the notices
feature ships.

## Goals

- [ ] Render exactly five bottom items in a fixed order: Início, Jogos,
      Grupos, Avisos, Mais.
- [ ] Restyle the shared bottom bar with a rounded-top surface and
      color-driven selection (icon above label), removing the hairline and the
      selection indicator bar.
- [ ] Keep Pessoas and Financeiro/Minhas cobranças reachable through a Mais
      shortcut screen that preserves existing role gating.
- [ ] Introduce Avisos as a chrome-wrapped placeholder destination.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Real notices feed | Avisos is a placeholder until the notices feature exists. |
| Badge counts on tabs | No unread-counter data source exists yet. |
| Changes to authentication or setup screens | They remain chrome-free. |
| Selector screen redesign | Grupos reuses the existing group selector. |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Menu composition | The five items always render, regardless of role; gating stays inside destinations and Mais shortcuts. | User confirmed fixed five-item menu matching the reference image. | Yes, user-confirmed |
| Grupos behavior | `OpenGroups` always opens the existing selector when a group is selected, even with a single membership; the Grupos tab never renders selected because the selector is chrome-free. | The tab must always respond; the selector already supports switching and creating groups. | Yes, user-confirmed mapping |
| Avisos behavior | New `NOTICES` destination with a placeholder page, wrapped in group chrome and selectable in the menu. | Placeholder destination without a real screen, per user direction. | Yes, user-confirmed |
| Mais behavior | New `MORE` destination listing shortcuts to Pessoas (when `showPeople`) and Financeiro/Minhas cobranças (label from `financeDestination`), wrapped in group chrome and selectable. | User confirmed Mais lists the relocated shortcuts. | Yes, user-confirmed |
| Visual selection signal | Selection is the primary color on icon and label; `selected` semantics remain the non-color accessibility signal, replacing the removed indicator bar. | Matches the reference image while preserving accessible selection. | Yes, user-confirmed image |
| Bar styling | Rounded top corners over the existing chrome surface; hairline and indicator bar removed; safe-area bottom inset behavior unchanged. | Reference image shows a rounded-top bar. | Yes, user-confirmed image |

**Open questions:** none.

## User Stories

### P1: Fixed Five-Tab Group Menu

**User Story**: As an authenticated group member, I want a stable five-tab
bottom menu so that I can reach every primary area from any group route.

**Acceptance Criteria**:

1. **MENU-01** - WHEN any chrome-wrapped group destination renders THEN the
   bottom menu SHALL show exactly five items in the order Início, Jogos,
   Grupos, Avisos, Mais, independent of role or profile status.
2. **MENU-02** - WHEN Início, Jogos, Avisos, or Mais is the current
   destination THEN its item SHALL expose selected semantics and render icon
   and label in the primary color; game detail SHALL select Jogos.
3. **MENU-03** - WHEN Grupos is activated THEN the app SHALL emit `OpenGroups`
   exactly once and the selector SHALL open even with one membership.
4. **MENU-04** - WHEN Avisos is activated THEN the app SHALL navigate to the
   placeholder notices destination showing the chrome and the selected Avisos
   tab.
5. **MENU-05** - WHEN Mais renders THEN it SHALL list a Pessoas shortcut only
   when `showPeople` and a finance shortcut labeled Financeiro or Minhas
   cobranças matching `financeDestination`, each emitting the existing typed
   intent exactly once.
6. **MENU-06** - WHEN the bar renders THEN it SHALL have rounded top corners,
   no hairline, and no selection indicator bar, and SHALL keep consuming the
   bottom window inset.

## Edge Cases

- WHEN a nested route (game detail) is open THEN the top bar back action
  remains unchanged and Jogos stays selected.
- WHEN the user is on the selector THEN the group chrome (including the menu)
  SHALL not render, as today.
- WHEN role policy hides Pessoas THEN the Mais screen SHALL omit that
  shortcut and the direct `OpenPeople` intent SHALL remain rejected by the
  navigation policy.
- WHEN the profile is incomplete and profile completion is the current
  destination THEN the menu still renders its five fixed items.

## Requirement Traceability

| Requirement ID | Story | Status |
| --- | --- | --- |
| MENU-01 | P1 | Implementing |
| MENU-02 | P1 | Implementing |
| MENU-03 | P1 | Implementing |
| MENU-04 | P1 | Implementing |
| MENU-05 | P1 | Implementing |
| MENU-06 | P1 | Implementing |

## Success Criteria

- [ ] Focused Compose navigation and design-system tests pass with no skipped
      tests.
- [ ] `rtk scripts/check-gradle` and repository safety gates pass.
