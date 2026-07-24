package br.com.saqz.groups.presentation.route

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.presentation.InviteToolState
import br.com.saqz.groups.presentation.InviteToolStateMachine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Immutable
data class GroupInviteRouteState(
    val invite: InviteToolState = InviteToolState(),
    val showExpireConfirmation: Boolean = false,
)

sealed interface GroupInviteRouteIntent {
    data object Rotate : GroupInviteRouteIntent
    data object Retry : GroupInviteRouteIntent
    data class ShareInvite(val url: String) : GroupInviteRouteIntent
    data class ShareFinished(val successful: Boolean) : GroupInviteRouteIntent
    data object RequestExpire : GroupInviteRouteIntent
    data object ConfirmExpire : GroupInviteRouteIntent
    data object CancelExpire : GroupInviteRouteIntent
}

sealed interface GroupInviteRouteEffect {
    data class RequestShare(val url: String) : GroupInviteRouteEffect
}

/**
 * Invite route adapter (T13): the sole content route backed by
 * [InviteToolStateMachine]. Every entry-owned instance projects its state and
 * delegates rotate/expire directly; the expire-confirmation dialog is local UI
 * state only, preserving current behavior (GROUPNAV-01, LIFE-01, LIFE-03,
 * LIFE-05, REG-01).
 */
class GroupInviteRouteViewModel(
    inviteFactory: (kotlinx.coroutines.CoroutineScope) -> InviteToolStateMachine,
) : MviViewModel<GroupInviteRouteState, GroupInviteRouteIntent, GroupInviteRouteEffect>(
    GroupInviteRouteState(),
) {
    // The invite machine is scope-bound; each entry-owned instance builds its own
    // machine on viewModelScope (same lifetime), mirroring the orchestrator pattern.
    private val invite: InviteToolStateMachine = inviteFactory(viewModelScope)

    init {
        update { it.copy(invite = invite.state.value) }
        invite.state.onEach { current -> update { it.copy(invite = current) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: GroupInviteRouteIntent) {
        when (intent) {
            GroupInviteRouteIntent.Rotate, GroupInviteRouteIntent.Retry -> invite.rotate()
            is GroupInviteRouteIntent.ShareInvite -> emit(GroupInviteRouteEffect.RequestShare(intent.url))
            is GroupInviteRouteIntent.ShareFinished -> invite.shareFinished(intent.successful)
            GroupInviteRouteIntent.RequestExpire -> update { it.copy(showExpireConfirmation = true) }
            GroupInviteRouteIntent.CancelExpire -> update { it.copy(showExpireConfirmation = false) }
            GroupInviteRouteIntent.ConfirmExpire -> {
                update { it.copy(showExpireConfirmation = false) }
                invite.expire()
            }
        }
    }
}
