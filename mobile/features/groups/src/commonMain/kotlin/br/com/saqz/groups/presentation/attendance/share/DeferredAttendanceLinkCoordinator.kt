package br.com.saqz.groups.presentation.attendance.share

import br.com.saqz.groups.data.DeferredLinkFailure
import br.com.saqz.groups.data.attendance.share.AttendanceShareGateway
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

enum class AttendanceLinkUiError {
    INVALID_OR_EXPIRED,
    ATTEMPT_LIMIT,
    UNAVAILABLE,
}

data class AttendanceLinkDestination(
    val groupId: String,
    val gameId: String,
)

data class DeferredAttendanceLinkState(
    val hasPending: Boolean = false,
    val isResolving: Boolean = false,
    val error: AttendanceLinkUiError? = null,
    val retryAfterSeconds: Int? = null,
)

sealed interface DeferredAttendanceLinkIntent {
    data object Start : DeferredAttendanceLinkIntent
    data object Stop : DeferredAttendanceLinkIntent
    data object Restore : DeferredAttendanceLinkIntent
    data class SetSessionReady(val ready: Boolean) : DeferredAttendanceLinkIntent
    data object Retry : DeferredAttendanceLinkIntent
    data object Discard : DeferredAttendanceLinkIntent
    data object Logout : DeferredAttendanceLinkIntent
}

class DeferredAttendanceLinkStateMachine(
    private val links: NativeGroupLinkPort,
    private val localState: LocalGroupStatePort,
    private val gateway: AttendanceShareGateway,
    private val scope: CoroutineScope,
    private val onResolved: (AttendanceLinkDestination) -> Unit,
) {
    private val mutableState = MutableStateFlow(DeferredAttendanceLinkState())
    val state: StateFlow<DeferredAttendanceLinkState> = mutableState.asStateFlow()
    private var pendingCode: String? = null
    private var sessionReady = false
    private var subscription: GroupCancelable? = null

    fun onIntent(intent: DeferredAttendanceLinkIntent) {
        when (intent) {
            DeferredAttendanceLinkIntent.Start -> start()
            DeferredAttendanceLinkIntent.Stop -> stop()
            DeferredAttendanceLinkIntent.Restore -> restore()
            is DeferredAttendanceLinkIntent.SetSessionReady -> setSessionReady(intent.ready)
            DeferredAttendanceLinkIntent.Retry -> retry()
            DeferredAttendanceLinkIntent.Discard -> clearPending()
            DeferredAttendanceLinkIntent.Logout -> logout()
        }
    }

    private fun start() {
        if (subscription != null) return
        subscription = links.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                if (event is GroupLinkEvent.Attendance) receive(event.code)
            }
        })
    }

    private fun stop() {
        subscription?.cancel()
        subscription = null
    }

    private fun restore() {
        localState.readPendingAttendanceLink(object : GroupValueCallback {
            override fun complete(result: GroupValueResult) {
                val restored = (result as? GroupValueResult.Success)?.value ?: return
                pendingCode = restored
                mutableState.value = DeferredAttendanceLinkState(hasPending = true)
                attemptResolve()
            }
        })
    }

    private fun setSessionReady(ready: Boolean) {
        sessionReady = ready
        if (ready) attemptResolve()
    }

    private fun retry() {
        if (mutableState.value.retryAfterSeconds != null) return
        attemptResolve()
    }

    private fun logout() {
        sessionReady = false
        clearPending()
        mutableState.value = DeferredAttendanceLinkState()
    }

    private fun receive(code: String) {
        pendingCode = code
        persist(code)
        mutableState.value = mutableState.value.copy(
            hasPending = true,
            error = null,
            retryAfterSeconds = null,
        )
        attemptResolve()
    }

    private fun attemptResolve() {
        val code = pendingCode ?: return
        if (!sessionReady || mutableState.value.isResolving || mutableState.value.retryAfterSeconds != null) return
        mutableState.value = mutableState.value.copy(isResolving = true, error = null)
        scope.launch {
            when (val result = gateway.resolveLink(code)) {
                is NetworkResult.Success -> {
                    if (pendingCode != code) {
                        mutableState.value = mutableState.value.copy(isResolving = false)
                        attemptResolve()
                        return@launch
                    }
                    clearPending()
                    onResolved(AttendanceLinkDestination(result.value.groupId, result.value.gameId))
                }
                is NetworkResult.Failure -> {
                    if (pendingCode != code) {
                        mutableState.value = mutableState.value.copy(isResolving = false)
                        attemptResolve()
                        return@launch
                    }
                    handleFailure(result.error)
                }
            }
        }
    }

    private fun handleFailure(error: NetworkError) {
        when (val failure = error.toDeferredLinkFailure(
            invalidCode = "ATTENDANCE_LINK_INVALID_OR_EXPIRED",
            attemptLimitCode = "ATTENDANCE_LINK_ATTEMPT_LIMIT",
        )) {
            DeferredLinkFailure.InvalidOrExpired -> {
                clearPending()
                mutableState.value = DeferredAttendanceLinkState(error = AttendanceLinkUiError.INVALID_OR_EXPIRED)
            }
            is DeferredLinkFailure.AttemptLimit -> {
                mutableState.value = mutableState.value.copy(
                    isResolving = false,
                    error = AttendanceLinkUiError.ATTEMPT_LIMIT,
                    retryAfterSeconds = failure.retryAfterSeconds,
                )
            }
            DeferredLinkFailure.Unavailable -> mutableState.value = mutableState.value.copy(
                isResolving = false,
                error = AttendanceLinkUiError.UNAVAILABLE,
            )
        }
    }

    private fun clearPending() {
        pendingCode = null
        persist(null)
        mutableState.value = mutableState.value.copy(
            hasPending = false,
            isResolving = false,
            retryAfterSeconds = null,
        )
    }

    private fun persist(value: String?) {
        localState.writePendingAttendanceLink(value, object : GroupResultCallback {
            override fun complete(result: GroupOperationResult) = Unit
        })
    }
}
