package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.DeferredLinkFailure
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.groups.data.toDeferredLinkFailure
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class InviteUiError {
    INVALID_OR_EXPIRED,
    ATTEMPT_LIMIT,
    UNAVAILABLE,
}

data class InviteState(
    val hasPending: Boolean = false,
    val isRedeeming: Boolean = false,
    val error: InviteUiError? = null,
    val retryAfterSeconds: Int? = null,
    val redeemedRole: GroupRoleDto? = null,
)

sealed interface DeferredInviteIntent {
    data object Start : DeferredInviteIntent

    data object Stop : DeferredInviteIntent

    data object Restore : DeferredInviteIntent

    data class SetSessionReady(val ready: Boolean) : DeferredInviteIntent

    data object Retry : DeferredInviteIntent

    data object Discard : DeferredInviteIntent

    data object Logout : DeferredInviteIntent
}

class DeferredInviteStateMachine(
    private val links: NativeGroupLinkPort,
    private val localState: LocalGroupStatePort,
    private val invites: RolesInvitesGateway,
    private val scope: CoroutineScope,
    private val selectGroup: (String) -> Unit,
) {
    private val delegate = DeferredLinkStateMachine(
        links = links,
        eventFilter = { (it as? GroupLinkEvent.Invite)?.code },
        readPending = localState::readPendingInvite,
        writePending = localState::writePendingInvite,
        resolve = invites::redeem,
        resolvedState = { InviteState(redeemedRole = it.role) },
        onResolved = { result -> selectGroup(result.groupId) },
        initialState = ::InviteState,
        pendingState = { InviteState(hasPending = true) },
        receivingState = { it.copy(hasPending = true, error = null, retryAfterSeconds = null, redeemedRole = null) },
        processing = InviteState::isRedeeming,
        retryAfter = InviteState::retryAfterSeconds,
        processingState = { state, processing -> state.copy(isRedeeming = processing, error = if (processing) null else state.error) },
        clearedState = { it.copy(hasPending = false, isRedeeming = false, retryAfterSeconds = null) },
        failureState = { state, error ->
            when (val failure = error.toDeferredLinkFailure("INVITE_INVALID_OR_EXPIRED", "INVITE_ATTEMPT_LIMIT")) {
                DeferredLinkFailure.InvalidOrExpired -> InviteState(error = InviteUiError.INVALID_OR_EXPIRED) to true
                is DeferredLinkFailure.AttemptLimit -> state.copy(isRedeeming = false, error = InviteUiError.ATTEMPT_LIMIT, retryAfterSeconds = failure.retryAfterSeconds) to false
                DeferredLinkFailure.Unavailable -> state.copy(isRedeeming = false, error = InviteUiError.UNAVAILABLE) to false
            }
        },
        scope = scope,
    )
    val state: StateFlow<InviteState> = delegate.state

    fun onIntent(intent: DeferredInviteIntent) {
        when (intent) {
            DeferredInviteIntent.Start -> delegate.start()
            DeferredInviteIntent.Stop -> delegate.stop()
            DeferredInviteIntent.Restore -> delegate.restore()
            is DeferredInviteIntent.SetSessionReady -> delegate.setSessionReady(intent.ready)
            DeferredInviteIntent.Retry -> delegate.retry()
            DeferredInviteIntent.Discard -> delegate.discard()
            DeferredInviteIntent.Logout -> delegate.logout()
        }
    }
}
