package br.com.saqz.groups.presentation.route

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class GroupAdministrationRouteMode { SETTINGS, MEMBERSHIPS }

/** Neither route emits a one-off effect: mutations flow through the shared administration machine. */
sealed interface GroupAdministrationRouteEffect

/**
 * Reusable Settings/Memberships route adapter (T13): every entry-owned instance
 * projects the shared [GroupAdministrationStateMachine] directly -- reusing its
 * existing immutable state and typed intent, with no wrapper contract and no new
 * business logic (GROUPNAV-01, LIFE-01, LIFE-03, LIFE-05).
 */
class GroupAdministrationRouteViewModel(
    mode: GroupAdministrationRouteMode,
    private val administration: GroupAdministrationStateMachine,
) : MviViewModel<GroupAdministrationState, GroupAdministrationIntent, GroupAdministrationRouteEffect>(
    administration.state.value,
) {

    init {
        administration.state.onEach { current -> update { current } }.launchIn(viewModelScope)
        if (mode == GroupAdministrationRouteMode.MEMBERSHIPS) {
            administration.onIntent(GroupAdministrationIntent.LoadMemberships)
        }
    }

    override fun onIntent(intent: GroupAdministrationIntent) = administration.onIntent(intent)
}
