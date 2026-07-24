package br.com.saqz.groups.presentation.route

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.domain.group.GroupProfileStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.GroupRouteAccess
import br.com.saqz.groups.presentation.GroupRoutePolicy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class GroupContentPlaceholderMode { PROFILE_COMPLETION, PEOPLE, GAMES, NOTICES, MORE }

@Immutable
data class GroupContentPlaceholderState(
    val access: GroupRouteAccess = GroupRoutePolicy.evaluate(GroupRole.ATHLETE, GroupProfileStatus.COMPLETE),
    val groupName: String? = null,
)

/** Only the MORE mode dispatches a command; the other placeholder routes are inert (REG-01). */
sealed interface GroupContentPlaceholderIntent {
    data object OpenPeople : GroupContentPlaceholderIntent
    data object OpenFinance : GroupContentPlaceholderIntent
}

sealed interface GroupContentPlaceholderEffect {
    data object OpenPeople : GroupContentPlaceholderEffect
    data object OpenFinance : GroupContentPlaceholderEffect
}

/**
 * Reusable placeholder route adapter (T13) for ProfileCompletion, People, Games,
 * Notices, and More: none of these routes activate a disconnected feature
 * implementation. Every instance derives an immutable [GroupRouteAccess]
 * projection from the shared [GroupAdministrationStateMachine] via the existing
 * [GroupRoutePolicy] -- no new coordinator, no mutation (GROUPNAV-01, LIFE-01,
 * LIFE-03, LIFE-05, REG-01).
 */
class GroupContentPlaceholderRouteViewModel(
    private val mode: GroupContentPlaceholderMode,
    administration: GroupAdministrationStateMachine,
) : MviViewModel<GroupContentPlaceholderState, GroupContentPlaceholderIntent, GroupContentPlaceholderEffect>(
    administration.state.value.toPlaceholderState(),
) {

    init {
        administration.state.onEach { current -> update { current.toPlaceholderState() } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: GroupContentPlaceholderIntent) {
        if (mode != GroupContentPlaceholderMode.MORE) return
        when (intent) {
            GroupContentPlaceholderIntent.OpenPeople -> emit(GroupContentPlaceholderEffect.OpenPeople)
            GroupContentPlaceholderIntent.OpenFinance -> emit(GroupContentPlaceholderEffect.OpenFinance)
        }
    }
}

private fun GroupAdministrationState.toPlaceholderState(): GroupContentPlaceholderState {
    val versionedGroup = group?.group
    val access = GroupRoutePolicy.evaluate(
        role = versionedGroup?.role ?: GroupRole.ATHLETE,
        profileStatus = versionedGroup?.profileStatus ?: GroupProfileStatus.COMPLETE,
    )
    return GroupContentPlaceholderState(access = access, groupName = versionedGroup?.name)
}
