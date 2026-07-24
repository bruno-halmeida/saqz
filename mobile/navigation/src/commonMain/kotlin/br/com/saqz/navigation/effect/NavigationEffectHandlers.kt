package br.com.saqz.navigation.effect

import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.groups.presentation.games.editor.GameEditorEffect
import br.com.saqz.groups.presentation.games.list.GamesEffect
import br.com.saqz.groups.presentation.route.GroupContentPlaceholderEffect
import br.com.saqz.groups.presentation.route.GroupHomeRouteEffect
import br.com.saqz.groups.presentation.route.GroupSelectionRouteEffect
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.ProductTab
import br.com.saqz.navigation.finance.resolveFinanceRoute

/**
 * LIFE-04: translates feature-owned typed effects into [NavigationSession] back-stack
 * mutations. Every handler is exhaustive over its sealed effect hierarchy and
 * duplicate-safe (`NavigationSession.push` never duplicates the active top key, so a
 * re-emitted effect is idempotent). Feature ViewModels emit these effects without
 * importing `:navigation` or Navigation Compose 3 UI (LIFE-03); the translation lives
 * only here.
 *
 * Effects that are NOT back-stack navigation are intentionally out of scope: platform
 * share (`GroupInviteRouteEffect.RequestShare`), the attendance/domain cases of
 * `GameDetailEffect`, and the empty `GroupAdministrationRouteEffect` carry no
 * back-stack meaning and are forwarded to the orchestrator/platform at the composition
 * root, not to a stack mutation.
 */

/**
 * GROUPNAV-02 / BACK-01: the deferred attendance deep link. `Games` is canonicalized
 * onto the GROUPS stack before `GameDetail(gameId)` is pushed, so system/TopBar back
 * from the opened game returns to `Games` (not `GroupHome`). Duplicate-safe: repeating
 * the deep link for the same game does not add a second `Games`/`GameDetail` pair.
 */
fun handleOpenAttendanceGame(session: NavigationSession, gameId: String) {
    session.selectTab(ProductTab.GROUPS)
    val target = GroupsRoute.GameDetail(gameId)
    if (session.stackFor(ProductTab.GROUPS).lastOrNull() == target) return
    session.push(GroupsRoute.Games)
    session.push(target)
}

/**
 * Exhaustive over [GamesEffect]: the games list pushes the opened game onto the GROUPS
 * stack, so back returns to the list itself (not the group home), and the owner's create
 * action pushes the editor with no game identity. Duplicate-safe through
 * [NavigationSession.push].
 *
 * There is deliberately no detail -> edit handler: editing an existing game is withheld
 * until the mobile read model carries series membership. The backend `games` table has a
 * `series_id`, but neither the read DTO, the domain `Game`, nor the list item expose it,
 * so navigation cannot tell a series occurrence from a standalone game. Routing edit would
 * let an occurrence be edited through the single-game path and silently mis-scoped, so
 * `GameEditor` is reachable only for creation until that gap is closed (see the seriesId
 * propagation ticket).
 */
fun handleGamesEffect(session: NavigationSession, effect: GamesEffect) {
    when (effect) {
        is GamesEffect.OpenGame -> session.push(GroupsRoute.GameDetail(effect.gameId))
        is GamesEffect.OpenCreate -> session.push(GroupsRoute.GameEditor)
    }
}

/**
 * Exhaustive over [GameEditorEffect]. `Saved` pops the editor, returning to the games list
 * that opened it. `Reload` re-reads instead of mutating the stack, so it is returned to the
 * composition root as unhandled.
 */
fun handleGameEditorEffect(session: NavigationSession, effect: GameEditorEffect): Boolean =
    when (effect) {
        is GameEditorEffect.Saved -> {
            session.goBack()
            true
        }
        is GameEditorEffect.Reload -> false
    }

/** Exhaustive over [GroupSelectionRouteEffect]: the only case opens Create Group. */
fun handleGroupSelectionEffect(session: NavigationSession, effect: GroupSelectionRouteEffect) {
    when (effect) {
        GroupSelectionRouteEffect.OpenCreateGroup -> session.push(GroupsRoute.CreateGroup)
    }
}

/**
 * Exhaustive over [GroupContentPlaceholderEffect]. `OpenFinance` resolves the
 * structural finance placeholder by the caller-supplied [canManageFinance] capability
 * (organizer -> Finance, athlete -> OwnCharges), mirroring the existing finance role
 * split without duplicating it.
 */
fun handleGroupContentEffect(
    session: NavigationSession,
    effect: GroupContentPlaceholderEffect,
    canManageFinance: Boolean,
) {
    when (effect) {
        GroupContentPlaceholderEffect.OpenPeople -> session.push(GroupsRoute.People)
        GroupContentPlaceholderEffect.OpenFinance -> session.push(resolveFinanceRoute(canManageFinance))
    }
}

/**
 * Exhaustive over [GroupHomeRouteEffect]. The panel-open cases (Settings/Memberships/
 * Invite) are the AD-025-deferred promotions to real routes and push their key,
 * returning `true` (consumed as navigation). `SwitchGroup`/`ConfirmLogout` are domain
 * events owned by the orchestrator, not back-stack mutations: the stack is left
 * untouched and `false` is returned so the composition root routes them onward.
 */
fun handleGroupHomeEffect(session: NavigationSession, effect: GroupHomeRouteEffect): Boolean =
    when (effect) {
        GroupHomeRouteEffect.OpenSettings -> {
            session.push(GroupsRoute.Settings)
            true
        }
        GroupHomeRouteEffect.OpenMemberships -> {
            session.push(GroupsRoute.Memberships)
            true
        }
        GroupHomeRouteEffect.OpenInvite -> {
            session.push(GroupsRoute.Invite)
            true
        }
        GroupHomeRouteEffect.SwitchGroup, GroupHomeRouteEffect.ConfirmLogout -> false
    }
