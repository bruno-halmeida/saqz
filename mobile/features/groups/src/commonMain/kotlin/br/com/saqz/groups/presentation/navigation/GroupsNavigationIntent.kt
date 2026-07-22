package br.com.saqz.groups.presentation.navigation

import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.network.SessionMembershipDto

sealed interface GroupsNavigationIntent {
    data class Reconcile(
        val selection: GroupSelectionState,
        val memberships: List<SessionMembershipDto>,
    ) : GroupsNavigationIntent

    data class OpenGroup(val groupId: String) : GroupsNavigationIntent

    data object OpenGroups : GroupsNavigationIntent

    data object OpenHome : GroupsNavigationIntent

    data object OpenProfileCompletion : GroupsNavigationIntent

    data object OpenPeople : GroupsNavigationIntent

    data object OpenGames : GroupsNavigationIntent

    data class OpenGameDetail(val gameId: String) : GroupsNavigationIntent

    data object OpenFinance : GroupsNavigationIntent

    data object OpenNotices : GroupsNavigationIntent

    data object OpenMore : GroupsNavigationIntent
}
