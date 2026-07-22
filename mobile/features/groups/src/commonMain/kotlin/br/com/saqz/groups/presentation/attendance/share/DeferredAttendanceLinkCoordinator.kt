package br.com.saqz.groups.presentation.attendance.share

import br.com.saqz.groups.presentation.DeferredLinkStateMachine
import br.com.saqz.groups.presentation.DeferredResolution
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
    private val delegate = DeferredLinkStateMachine(
        links, { (it as? GroupLinkEvent.Attendance)?.code }, localState::readPendingAttendanceLink,
        localState::writePendingAttendanceLink, { code ->
            when (val result = gateway.resolveLink(code)) {
                is NetworkResult.Success -> DeferredResolution.Success(result.value)
                is NetworkResult.Failure -> DeferredResolution.Failure(result.error)
            }
        }, { DeferredAttendanceLinkState() },
        { result -> onResolved(AttendanceLinkDestination(result.groupId, result.gameId)) }, ::DeferredAttendanceLinkState,
        { DeferredAttendanceLinkState(hasPending = true) }, { it.copy(hasPending = true, error = null, retryAfterSeconds = null) },
        DeferredAttendanceLinkState::isResolving, DeferredAttendanceLinkState::retryAfterSeconds,
        { state, processing -> state.copy(isResolving = processing, error = if (processing) null else state.error) },
        { it.copy(hasPending = false, isResolving = false, retryAfterSeconds = null) },
        { state, error -> when (val failure = error.toDeferredLinkFailure("ATTENDANCE_LINK_INVALID_OR_EXPIRED", "ATTENDANCE_LINK_ATTEMPT_LIMIT")) {
            DeferredLinkFailure.InvalidOrExpired -> DeferredAttendanceLinkState(error = AttendanceLinkUiError.INVALID_OR_EXPIRED) to true
            is DeferredLinkFailure.AttemptLimit -> state.copy(isResolving = false, error = AttendanceLinkUiError.ATTEMPT_LIMIT, retryAfterSeconds = failure.retryAfterSeconds) to false
            DeferredLinkFailure.Unavailable -> state.copy(isResolving = false, error = AttendanceLinkUiError.UNAVAILABLE) to false
        } }, scope,
    )
    val state: StateFlow<DeferredAttendanceLinkState> = delegate.state

    fun onIntent(intent: DeferredAttendanceLinkIntent) {
        when (intent) {
            DeferredAttendanceLinkIntent.Start -> delegate.start()
            DeferredAttendanceLinkIntent.Stop -> delegate.stop()
            DeferredAttendanceLinkIntent.Restore -> delegate.restore()
            is DeferredAttendanceLinkIntent.SetSessionReady -> delegate.setSessionReady(intent.ready)
            DeferredAttendanceLinkIntent.Retry -> delegate.retry()
            DeferredAttendanceLinkIntent.Discard -> delegate.discard()
            DeferredAttendanceLinkIntent.Logout -> delegate.logout()
        }
    }

}
