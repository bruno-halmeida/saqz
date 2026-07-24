package br.com.saqz.navigation.effect

import br.com.saqz.groups.navigation.GroupsRoute
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
 * share (`GroupInviteRouteEffect.RequestShare`), game-editor/attendance/domain effects
 * (`GameDetailEffect`), and the empty `GroupAdministrationRouteEffect` carry no
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
