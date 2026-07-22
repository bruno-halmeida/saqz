package br.com.saqz.groups.presentation.attendance.share

import br.com.saqz.groups.presentation.DeferredLinkStateMachine
import br.com.saqz.groups.presentation.DeferredResolution
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingError
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
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
    private val gateway: AttendanceSharingGateway,
    private val scope: CoroutineScope,
    private val onResolved: (AttendanceLinkDestination) -> Unit,
) {
    private val delegate = DeferredLinkStateMachine(
        links, { (it as? GroupLinkEvent.Attendance)?.code }, localState::readPendingAttendanceLink,
        localState::writePendingAttendanceLink, { code ->
            when (val result = gateway.resolveLink(AttendanceLinkCode(code))) {
                is SaqzResult.Success -> DeferredResolution.Success(result.value)
                is SaqzResult.Failure -> DeferredResolution.Failure(result.error)
            }
        }, { DeferredAttendanceLinkState() },
        { result -> onResolved(AttendanceLinkDestination(result.groupId.value, result.gameId)) }, ::DeferredAttendanceLinkState,
        { DeferredAttendanceLinkState(hasPending = true) }, { it.copy(hasPending = true, error = null, retryAfterSeconds = null) },
        DeferredAttendanceLinkState::isResolving, DeferredAttendanceLinkState::retryAfterSeconds,
        { state, processing -> state.copy(isResolving = processing, error = if (processing) null else state.error) },
        { it.copy(hasPending = false, isResolving = false, retryAfterSeconds = null) },
        { state, error -> when (error) {
            AttendanceSharingError.InvalidOrExpired -> DeferredAttendanceLinkState(error = AttendanceLinkUiError.INVALID_OR_EXPIRED) to true
            is AttendanceSharingError.AttemptLimit -> state.copy(isResolving = false, error = AttendanceLinkUiError.ATTEMPT_LIMIT, retryAfterSeconds = error.retryAfterSeconds) to false
            is AttendanceSharingError.DataFailure -> state.copy(isResolving = false, error = AttendanceLinkUiError.UNAVAILABLE) to false
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
