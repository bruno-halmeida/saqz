package br.com.saqz.composeapp.navigation

import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupProfileStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionMembership
import br.com.saqz.groups.presentation.GroupFinanceVisibility
import br.com.saqz.groups.presentation.GroupRoutePolicy
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationAccess
import br.com.saqz.groups.presentation.navigation.GroupsNavigationEffect
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.navigation.isGroupScoped

internal class GroupsNavigationViewModel :
    MviViewModel<GroupsNavigationState, GroupsNavigationIntent, GroupsNavigationEffect>(GroupsNavigationState()) {

    override fun onIntent(intent: GroupsNavigationIntent) {
        when (intent) {
            is GroupsNavigationIntent.Reconcile -> reconcile(intent.selection, intent.memberships)
            is GroupsNavigationIntent.OpenGroup -> openGroup(intent.groupId)
            GroupsNavigationIntent.OpenGroups -> openGroups()
            GroupsNavigationIntent.OpenHome -> navigate(GroupsDestination.HOME)
            GroupsNavigationIntent.OpenProfileCompletion -> navigate(GroupsDestination.PROFILE_COMPLETION)
            GroupsNavigationIntent.OpenPeople -> navigate(GroupsDestination.PEOPLE)
            GroupsNavigationIntent.OpenGames -> navigate(GroupsDestination.GAMES)
            is GroupsNavigationIntent.OpenGameDetail -> openGameDetail(intent.gameId)
            GroupsNavigationIntent.OpenFinance -> openFinance()
            GroupsNavigationIntent.OpenNotices -> navigate(GroupsDestination.NOTICES)
            GroupsNavigationIntent.OpenMore -> navigate(GroupsDestination.MORE)
        }
    }

    private fun reconcile(
        selection: GroupSelectionState,
        memberships: List<GroupSelectionMembership>,
    ) {
        when (memberships.size) {
            0 -> replaceUnscoped(GroupsDestination.SETUP)
            1 -> reconcileSingle(selection, memberships.single())
            else -> reconcileMultiple(selection, memberships)
        }
    }

    private fun reconcileSingle(
        selection: GroupSelectionState,
        membership: GroupSelectionMembership,
    ) {
        val memberships = listOf(membership)
        when (selection) {
            GroupSelectionState.NoGroup,
            is GroupSelectionState.Selector,
            -> replaceUnscoped(
                destination = GroupsDestination.LOADING,
                memberships = memberships,
                requestedGroupId = membership.groupId,
            )
            is GroupSelectionState.Loading -> replaceUnscoped(
                destination = GroupsDestination.LOADING,
                memberships = memberships,
                requestedGroupId = membership.groupId,
            )
            is GroupSelectionState.LoadError -> replaceUnscoped(
                destination = if (selection.groupId == membership.groupId) {
                    GroupsDestination.LOAD_ERROR
                } else {
                    GroupsDestination.LOADING
                },
                memberships = memberships,
                requestedGroupId = membership.groupId,
            )
            is GroupSelectionState.Selected -> if (selection.group.group.id.value == membership.groupId) {
                select(selection.group.group, memberships)
            } else {
                replaceUnscoped(
                    destination = GroupsDestination.LOADING,
                    memberships = memberships,
                    requestedGroupId = membership.groupId,
                )
            }
        }
    }

    private fun reconcileMultiple(
        selection: GroupSelectionState,
        memberships: List<GroupSelectionMembership>,
    ) {
        when (selection) {
            GroupSelectionState.NoGroup,
            is GroupSelectionState.Selector,
            -> showGroups(memberships)
            is GroupSelectionState.Loading -> reconcilePending(
                destination = GroupsDestination.LOADING,
                groupId = selection.groupId,
                memberships = memberships,
            )
            is GroupSelectionState.LoadError -> reconcilePending(
                destination = GroupsDestination.LOAD_ERROR,
                groupId = selection.groupId,
                memberships = memberships,
            )
            is GroupSelectionState.Selected -> reconcileSelected(selection.group.group, memberships)
        }
    }

    private fun reconcilePending(
        destination: GroupsDestination,
        groupId: String,
        memberships: List<GroupSelectionMembership>,
    ) {
        val requestedGroupId = state.value.requestedGroupId
        if (memberships.size > 1 && requestedGroupId != groupId) {
            showGroups(memberships)
            return
        }
        replaceUnscoped(destination, memberships, requestedGroupId)
    }

    private fun reconcileSelected(group: Group, memberships: List<GroupSelectionMembership>) {
        if (memberships.none { it.groupId == group.id.value }) {
            showGroups(memberships)
            return
        }
        val current = state.value
        val detailAlreadyOpen = current.groupId == group.id.value && current.destination.isGroupScoped()
        val requestedGroupLoaded = current.requestedGroupId == group.id.value
        if (memberships.size > 1 && !detailAlreadyOpen && !requestedGroupLoaded) {
            showGroups(memberships)
            return
        }
        select(group, memberships)
    }

    private fun select(group: Group, memberships: List<GroupSelectionMembership>) {
        val current = state.value
        if (current.groupId == group.id.value) {
            update {
                current.copy(
                    access = accessFor(group),
                    memberships = memberships,
                    requestedGroupId = null,
                )
            }
            enforceAllowedDestination(group)
            return
        }
        val destination = initialDestination(group)
        update {
            GroupsNavigationState(
                destination = destination,
                groupId = group.id.value,
                access = accessFor(group),
                memberships = memberships,
            )
        }
        emit(GroupsNavigationEffect.DestinationChanged(destination, group.id.value))
    }

    private fun replaceUnscoped(
        destination: GroupsDestination,
        memberships: List<GroupSelectionMembership> = emptyList(),
        requestedGroupId: String? = null,
    ) {
        val next = GroupsNavigationState(
            destination = destination,
            memberships = memberships,
            requestedGroupId = requestedGroupId,
        )
        if (state.value == next) return
        update { next }
    }

    private fun showGroups(memberships: List<GroupSelectionMembership>) {
        replaceUnscoped(GroupsDestination.SELECTOR, memberships)
    }

    private fun openGroup(groupId: String) {
        val current = state.value
        if (current.destination != GroupsDestination.SELECTOR) return
        if (current.memberships.none { it.groupId == groupId }) return
        replaceUnscoped(
            destination = GroupsDestination.LOADING,
            memberships = current.memberships,
            requestedGroupId = groupId,
        )
    }

    private fun openGroups() {
        val memberships = state.value.memberships
        if (memberships.isEmpty()) return
        showGroups(memberships)
    }

    private fun navigate(destination: GroupsDestination) {
        val current = state.value
        val groupId = current.groupId ?: return
        if (!isAllowed(destination, current.access)) return
        if (current.destination == destination && current.gameId == null) return
        update { current.copy(destination = destination, gameId = null) }
        emit(GroupsNavigationEffect.DestinationChanged(destination, groupId))
    }

    private fun openGameDetail(gameId: String) {
        if (gameId.isBlank()) return
        val current = state.value
        val groupId = current.groupId ?: return
        if (!current.access.showGames) return
        if (current.destination == GroupsDestination.GAME_DETAIL && current.gameId == gameId) return
        update { current.copy(destination = GroupsDestination.GAME_DETAIL, gameId = gameId) }
        emit(GroupsNavigationEffect.DestinationChanged(GroupsDestination.GAME_DETAIL, groupId))
    }

    private fun openFinance() {
        val destination = state.value.access.financeDestination ?: return
        navigate(destination)
    }

    private fun enforceAllowedDestination(group: Group) {
        val current = state.value
        if (isAllowed(current.destination, current.access)) return
        val destination = initialDestination(group)
        update { current.copy(destination = destination, gameId = null) }
        emit(GroupsNavigationEffect.DestinationChanged(destination, group.id.value))
    }

    private fun initialDestination(group: Group): GroupsDestination = when {
        group.profileStatus == GroupProfileStatus.INCOMPLETE && group.role != GroupRole.ATHLETE -> {
            GroupsDestination.PROFILE_COMPLETION
        }
        else -> GroupsDestination.HOME
    }

    private fun accessFor(group: Group): GroupsNavigationAccess {
        val policy = GroupRoutePolicy.evaluate(group.role, group.profileStatus)
        return GroupsNavigationAccess(
            showPeople = policy.peopleVisible,
            showGames = policy.gamesVisible,
            showFinance = true,
            canCompleteProfile = policy.profileCompletionVisible,
            canMutateOperations = policy.operationsMutable,
            financeDestination = if (policy.financeVisibility == GroupFinanceVisibility.ORGANIZER) {
                GroupsDestination.FINANCE
            } else {
                GroupsDestination.OWN_CHARGES
            },
        )
    }

    private fun isAllowed(destination: GroupsDestination, access: GroupsNavigationAccess): Boolean = when (destination) {
        GroupsDestination.HOME -> true
        GroupsDestination.PROFILE_COMPLETION -> access.canCompleteProfile
        GroupsDestination.PEOPLE -> access.showPeople
        GroupsDestination.GAMES,
        GroupsDestination.GAME_DETAIL,
        -> access.showGames
        GroupsDestination.FINANCE -> access.financeDestination == GroupsDestination.FINANCE
        GroupsDestination.OWN_CHARGES -> access.financeDestination == GroupsDestination.OWN_CHARGES
        GroupsDestination.NOTICES,
        GroupsDestination.MORE,
        -> true
        GroupsDestination.SETUP,
        GroupsDestination.SELECTOR,
        GroupsDestination.LOADING,
        GroupsDestination.LOAD_ERROR,
        -> false
    }
}
