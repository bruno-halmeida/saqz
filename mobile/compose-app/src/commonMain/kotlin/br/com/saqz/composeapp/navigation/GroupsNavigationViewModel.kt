package br.com.saqz.composeapp.navigation

import androidx.lifecycle.ViewModel
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRoleDto
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

internal class GroupsNavigationViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(GroupsNavigationState())
    val state: StateFlow<GroupsNavigationState> = mutableState.asStateFlow()
    private val effectChannel = Channel<GroupsNavigationEffect>(Channel.BUFFERED)
    val effects: Flow<GroupsNavigationEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: GroupsNavigationIntent) {
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
            is GroupSelectionState.Selected -> if (selection.group.group.id == membership.groupId) {
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
        val requestedGroupId = mutableState.value.requestedGroupId
        if (memberships.size > 1 && requestedGroupId != groupId) {
            showGroups(memberships)
            return
        }
        replaceUnscoped(destination, memberships, requestedGroupId)
    }

    private fun reconcileSelected(group: GroupDto, memberships: List<GroupSelectionMembership>) {
        if (memberships.none { it.groupId == group.id }) {
            showGroups(memberships)
            return
        }
        val current = mutableState.value
        val detailAlreadyOpen = current.groupId == group.id && current.destination.isGroupScoped()
        val requestedGroupLoaded = current.requestedGroupId == group.id
        if (memberships.size > 1 && !detailAlreadyOpen && !requestedGroupLoaded) {
            showGroups(memberships)
            return
        }
        select(group, memberships)
    }

    private fun select(group: GroupDto, memberships: List<GroupSelectionMembership>) {
        val current = mutableState.value
        if (current.groupId == group.id) {
            mutableState.value = current.copy(
                access = accessFor(group),
                memberships = memberships,
                requestedGroupId = null,
            )
            enforceAllowedDestination(group)
            return
        }
        val destination = initialDestination(group)
        mutableState.value = GroupsNavigationState(
            destination = destination,
            groupId = group.id,
            access = accessFor(group),
            memberships = memberships,
        )
        effectChannel.trySend(GroupsNavigationEffect.DestinationChanged(destination, group.id))
    }

    private fun replaceUnscoped(
        destination: GroupsDestination,
        memberships: List<GroupSelectionMembership> = emptyList(),
        requestedGroupId: String? = null,
    ) {
        val state = GroupsNavigationState(
            destination = destination,
            memberships = memberships,
            requestedGroupId = requestedGroupId,
        )
        if (mutableState.value == state) return
        mutableState.value = state
    }

    private fun showGroups(memberships: List<GroupSelectionMembership>) {
        replaceUnscoped(GroupsDestination.SELECTOR, memberships)
    }

    private fun openGroup(groupId: String) {
        val current = mutableState.value
        if (current.destination != GroupsDestination.SELECTOR) return
        if (current.memberships.none { it.groupId == groupId }) return
        replaceUnscoped(
            destination = GroupsDestination.LOADING,
            memberships = current.memberships,
            requestedGroupId = groupId,
        )
    }

    private fun openGroups() {
        val memberships = mutableState.value.memberships
        if (memberships.isEmpty()) return
        showGroups(memberships)
    }

    private fun navigate(destination: GroupsDestination) {
        val current = mutableState.value
        val groupId = current.groupId ?: return
        if (!isAllowed(destination, current.access)) return
        if (current.destination == destination && current.gameId == null) return
        mutableState.value = current.copy(destination = destination, gameId = null)
        effectChannel.trySend(GroupsNavigationEffect.DestinationChanged(destination, groupId))
    }

    private fun openGameDetail(gameId: String) {
        if (gameId.isBlank()) return
        val current = mutableState.value
        val groupId = current.groupId ?: return
        if (!current.access.showGames) return
        if (current.destination == GroupsDestination.GAME_DETAIL && current.gameId == gameId) return
        mutableState.value = current.copy(destination = GroupsDestination.GAME_DETAIL, gameId = gameId)
        effectChannel.trySend(GroupsNavigationEffect.DestinationChanged(GroupsDestination.GAME_DETAIL, groupId))
    }

    private fun openFinance() {
        val destination = mutableState.value.access.financeDestination ?: return
        navigate(destination)
    }

    private fun enforceAllowedDestination(group: GroupDto) {
        val state = mutableState.value
        if (isAllowed(state.destination, state.access)) return
        val destination = initialDestination(group)
        mutableState.value = state.copy(destination = destination, gameId = null)
        effectChannel.trySend(GroupsNavigationEffect.DestinationChanged(destination, group.id))
    }

    private fun initialDestination(group: GroupDto): GroupsDestination = when {
        group.profileStatus == GroupProfileStatusDto.INCOMPLETE && group.role != GroupRoleDto.ATHLETE -> {
            GroupsDestination.PROFILE_COMPLETION
        }
        else -> GroupsDestination.HOME
    }

    private fun accessFor(group: GroupDto): GroupsNavigationAccess {
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
