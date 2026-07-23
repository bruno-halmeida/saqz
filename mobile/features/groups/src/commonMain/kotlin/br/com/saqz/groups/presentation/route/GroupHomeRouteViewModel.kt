package br.com.saqz.groups.presentation.route

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.photo.GroupPhotoCoordinator
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Immutable
data class GroupHomeRouteState(
    val administration: GroupAdministrationState = GroupAdministrationState(),
    val photo: GroupPhotoState = GroupPhotoState(),
    val showLogoutConfirmation: Boolean = false,
)

sealed interface GroupHomeRouteIntent {
    data class Administration(val intent: GroupAdministrationIntent) : GroupHomeRouteIntent
    data class Photo(val intent: GroupPhotoIntent) : GroupHomeRouteIntent
    data object OpenSettings : GroupHomeRouteIntent
    data object OpenMemberships : GroupHomeRouteIntent
    data object OpenInvite : GroupHomeRouteIntent
    data object SwitchGroup : GroupHomeRouteIntent
    data object RequestLogout : GroupHomeRouteIntent
    data object ConfirmLogout : GroupHomeRouteIntent
    data object CancelLogout : GroupHomeRouteIntent
}

/** GroupHomeRouteViewModel requests navigation via a typed effect only; it never imports Nav3 UI. */
sealed interface GroupHomeRouteEffect {
    data object OpenSettings : GroupHomeRouteEffect
    data object OpenMemberships : GroupHomeRouteEffect
    data object OpenInvite : GroupHomeRouteEffect
    data object SwitchGroup : GroupHomeRouteEffect
    data object ConfirmLogout : GroupHomeRouteEffect
}

/**
 * GroupHome route adapter (T13): the only content route combining two existing
 * sources -- [GroupAdministrationStateMachine] and [GroupPhotoCoordinator] --
 * because GroupHome is the sole screen that renders both (GROUPNAV-01, LIFE-01,
 * LIFE-03, LIFE-05). Neither coordinator's business logic is reimplemented here;
 * every mutating intent delegates 1:1 to the existing machine.
 */
class GroupHomeRouteViewModel(
    private val administration: GroupAdministrationStateMachine,
    private val photo: GroupPhotoCoordinator,
) : MviViewModel<GroupHomeRouteState, GroupHomeRouteIntent, GroupHomeRouteEffect>(
    GroupHomeRouteState(administration.state.value, photo.state.value),
) {

    init {
        combine(administration.state, photo.state) { admin, photoState -> admin to photoState }
            .onEach { (admin, photoState) -> update { it.copy(administration = admin, photo = photoState) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: GroupHomeRouteIntent) {
        when (intent) {
            is GroupHomeRouteIntent.Administration -> administration.onIntent(intent.intent)
            is GroupHomeRouteIntent.Photo -> photo.onIntent(intent.intent)
            GroupHomeRouteIntent.OpenSettings -> emit(GroupHomeRouteEffect.OpenSettings)
            GroupHomeRouteIntent.OpenMemberships -> emit(GroupHomeRouteEffect.OpenMemberships)
            GroupHomeRouteIntent.OpenInvite -> emit(GroupHomeRouteEffect.OpenInvite)
            GroupHomeRouteIntent.SwitchGroup -> emit(GroupHomeRouteEffect.SwitchGroup)
            GroupHomeRouteIntent.RequestLogout -> update { it.copy(showLogoutConfirmation = true) }
            GroupHomeRouteIntent.CancelLogout -> update { it.copy(showLogoutConfirmation = false) }
            GroupHomeRouteIntent.ConfirmLogout -> {
                update { it.copy(showLogoutConfirmation = false) }
                emit(GroupHomeRouteEffect.ConfirmLogout)
            }
        }
    }
}
