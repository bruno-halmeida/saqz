package br.com.saqz.access.presentation

import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.RolesInvitesGateway
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.InviteCodeListener
import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.ValueCallback
import br.com.saqz.access.port.ValueResult
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
    private val links: NativeLinkPort,
    private val localState: LocalAccessStatePort,
    private val invites: RolesInvitesGateway,
    private val scope: CoroutineScope,
    private val selectGroup: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(InviteState())
    val state: StateFlow<InviteState> = mutableState.asStateFlow()
    private var pendingCode: String? = null
    private var sessionReady = false
    private var subscription: Cancelable? = null

    fun onIntent(intent: DeferredInviteIntent) {
        when (intent) {
            DeferredInviteIntent.Start -> start()
            DeferredInviteIntent.Stop -> stop()
            DeferredInviteIntent.Restore -> restore()
            is DeferredInviteIntent.SetSessionReady -> setSessionReady(intent.ready)
            DeferredInviteIntent.Retry -> retry()
            DeferredInviteIntent.Discard -> clearPending()
            DeferredInviteIntent.Logout -> logout()
        }
    }

    private fun start() {
        if (subscription != null) return
        subscription = links.start(object : InviteCodeListener {
            override fun onInviteCode(code: String) = receive(code)
        })
    }

    private fun stop() {
        subscription?.cancel()
        subscription = null
    }

    private fun restore() {
        localState.readPendingInvite(object : ValueCallback {
            override fun complete(result: ValueResult) {
                val restored = (result as? ValueResult.Success)?.value ?: return
                pendingCode = restored
                mutableState.value = InviteState(hasPending = true)
                attemptRedeem()
            }
        })
    }

    private fun setSessionReady(ready: Boolean) {
        sessionReady = ready
        if (ready) attemptRedeem()
    }

    private fun retry() {
        if (mutableState.value.retryAfterSeconds != null) return
        attemptRedeem()
    }

    private fun logout() {
        sessionReady = false
        clearPending()
        mutableState.value = InviteState()
    }

    private fun receive(code: String) {
        pendingCode = code
        persist(code)
        mutableState.value = mutableState.value.copy(
            hasPending = true,
            error = null,
            retryAfterSeconds = null,
            redeemedRole = null,
        )
        attemptRedeem()
    }

    private fun attemptRedeem() {
        val code = pendingCode ?: return
        if (!sessionReady || mutableState.value.isRedeeming || mutableState.value.retryAfterSeconds != null) return
        mutableState.value = mutableState.value.copy(isRedeeming = true, error = null)
        scope.launch {
            val result = invites.redeem(code)
            if (pendingCode != code) {
                mutableState.value = mutableState.value.copy(isRedeeming = false)
                attemptRedeem()
                return@launch
            }
            when (result) {
                is NetworkResult.Success -> {
                    clearPending()
                    mutableState.value = InviteState(redeemedRole = result.value.role)
                    selectGroup(result.value.groupId)
                }
                is NetworkResult.Failure -> handleFailure(result.error)
            }
        }
    }

    private fun handleFailure(error: NetworkError) {
        val problem = (error as? NetworkError.ApiProblemError)?.problem
        when {
            problem?.status == 404 && problem.code == "INVITE_INVALID_OR_EXPIRED" -> {
                clearPending()
                mutableState.value = InviteState(error = InviteUiError.INVALID_OR_EXPIRED)
            }
            problem?.status == 429 && problem.code == "INVITE_ATTEMPT_LIMIT" -> {
                mutableState.value = mutableState.value.copy(
                    isRedeeming = false,
                    error = InviteUiError.ATTEMPT_LIMIT,
                    retryAfterSeconds = problem.retryAfterSeconds,
                )
            }
            else -> mutableState.value = mutableState.value.copy(
                isRedeeming = false,
                error = InviteUiError.UNAVAILABLE,
            )
        }
    }

    private fun clearPending() {
        pendingCode = null
        persist(null)
        mutableState.value = mutableState.value.copy(
            hasPending = false,
            isRedeeming = false,
            retryAfterSeconds = null,
        )
    }

    private fun persist(value: String?) {
        localState.writePendingInvite(value, object : ResultCallback {
            override fun complete(result: OperationResult) = Unit
        })
    }
}
