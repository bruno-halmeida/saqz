package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupFinanceVisibility
import br.com.saqz.groups.presentation.GroupRoutePolicy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

internal enum class GroupsDestination {
    SETUP,
    SELECTOR,
    LOADING,
    LOAD_ERROR,
    HOME,
    PROFILE_COMPLETION,
    PEOPLE,
    GAMES,
    GAME_DETAIL,
    FINANCE,
    OWN_CHARGES,
}

@Immutable
internal data class GroupsNavigationAccess(
    val showPeople: Boolean = false,
    val showGames: Boolean = false,
    val showFinance: Boolean = false,
    val canCompleteProfile: Boolean = false,
    val canMutateOperations: Boolean = false,
    val financeDestination: GroupsDestination? = null,
)

@Immutable
internal data class GroupsNavigationState(
    val destination: GroupsDestination = GroupsDestination.SETUP,
    val groupId: String? = null,
    val access: GroupsNavigationAccess = GroupsNavigationAccess(),
    val gameId: String? = null,
)

internal sealed interface GroupsNavigationIntent {
    data class Reconcile(val selection: GroupSelectionState) : GroupsNavigationIntent
    data object OpenHome : GroupsNavigationIntent
    data object OpenProfileCompletion : GroupsNavigationIntent
    data object OpenPeople : GroupsNavigationIntent
    data object OpenGames : GroupsNavigationIntent
    data class OpenGameDetail(val gameId: String) : GroupsNavigationIntent
    data object OpenFinance : GroupsNavigationIntent
}

internal sealed interface GroupsNavigationEffect {
    data class DestinationChanged(val destination: GroupsDestination, val groupId: String) : GroupsNavigationEffect
}

internal class GroupsNavigationViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(GroupsNavigationState())
    val state: StateFlow<GroupsNavigationState> = mutableState.asStateFlow()
    private val effectChannel = Channel<GroupsNavigationEffect>(Channel.BUFFERED)
    val effects: Flow<GroupsNavigationEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: GroupsNavigationIntent) {
        when (intent) {
            is GroupsNavigationIntent.Reconcile -> reconcile(intent.selection)
            GroupsNavigationIntent.OpenHome -> navigate(GroupsDestination.HOME)
            GroupsNavigationIntent.OpenProfileCompletion -> navigate(GroupsDestination.PROFILE_COMPLETION)
            GroupsNavigationIntent.OpenPeople -> navigate(GroupsDestination.PEOPLE)
            GroupsNavigationIntent.OpenGames -> navigate(GroupsDestination.GAMES)
            is GroupsNavigationIntent.OpenGameDetail -> openGameDetail(intent.gameId)
            GroupsNavigationIntent.OpenFinance -> openFinance()
        }
    }

    private fun reconcile(selection: GroupSelectionState) {
        when (selection) {
            GroupSelectionState.NoGroup -> replaceUnscoped(GroupsDestination.SETUP)
            is GroupSelectionState.Selector -> replaceUnscoped(GroupsDestination.SELECTOR)
            is GroupSelectionState.Loading -> replaceUnscoped(GroupsDestination.LOADING)
            is GroupSelectionState.LoadError -> replaceUnscoped(GroupsDestination.LOAD_ERROR)
            is GroupSelectionState.Selected -> select(selection.group.group)
        }
    }

    private fun select(group: GroupDto) {
        val current = mutableState.value
        if (current.groupId == group.id) {
            mutableState.value = current.copy(access = accessFor(group))
            enforceAllowedDestination(group)
            return
        }
        val destination = initialDestination(group)
        mutableState.value = GroupsNavigationState(
            destination = destination,
            groupId = group.id,
            access = accessFor(group),
        )
        effectChannel.trySend(GroupsNavigationEffect.DestinationChanged(destination, group.id))
    }

    private fun replaceUnscoped(destination: GroupsDestination) {
        if (mutableState.value == GroupsNavigationState(destination = destination)) return
        mutableState.value = GroupsNavigationState(destination = destination)
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
        GroupsDestination.SETUP,
        GroupsDestination.SELECTOR,
        GroupsDestination.LOADING,
        GroupsDestination.LOAD_ERROR,
        -> false
    }
}
