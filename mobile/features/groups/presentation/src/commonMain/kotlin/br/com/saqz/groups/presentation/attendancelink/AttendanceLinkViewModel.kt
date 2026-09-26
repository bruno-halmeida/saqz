package br.com.saqz.groups.presentation.attendancelink

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.membership.*
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

class AttendanceLinkViewModel(
    private val code: String,
    private val decline: Boolean = false,
    private val sharing: AttendanceSharingGateway,
    private val attendance: AttendanceGateway,
    private val invites: InviteGateway,
    private val notifications: NativeNotificationPort? = null,
) : MviViewModel<AttendanceLinkState, AttendanceLinkIntent, AttendanceLinkEffect>(AttendanceLinkState()) {
    private var generation = 0
    init { resolve() }

    override fun handleIntent(intent: AttendanceLinkIntent) {
        when (intent) {
            AttendanceLinkIntent.Retry -> retry()
            AttendanceLinkIntent.Decline -> decline()
        }
    }

    fun retry() {
        if (state.value.phase in listOf(AttendanceLinkPhase.Failed, AttendanceLinkPhase.Pending)) resolve()
    }

    private fun resolve() {
        val current = ++generation
        update { AttendanceLinkState() }
        viewModelScope.launch {
            val requestId = requestId(code)
            if (requestId == null) { show(AttendanceLinkPhase.Invalid); return@launch }
            val result = sharing.resolveLink(AttendanceLinkCode(code))
            if (current != generation) return@launch
            when (result) {
                is SaqzResult.Failure -> if (result.error is AttendanceSharingError.InvalidOrExpired) join(current)
                    else show(AttendanceLinkPhase.Failed)
                is SaqzResult.Success -> {
                    val destination = result.value
                    if (destination.registrationRequired) register(destination.groupId.value)
                    else if (decline) ask(current, destination)
                    else respond(destination, requestId, current, AttendanceIntent.Confirm)
                }
            }
        }
    }

    /** O link do "não vou" não responde nada sozinho: abre o aviso e espera o toque. */
    private fun ask(current: Int, destination: AttendanceLinkDestination) {
        if (current != generation) return
        update { AttendanceLinkState(AttendanceLinkPhase.DeclineSheet, destination) }
    }

    private fun decline() {
        val destination = state.value.destination ?: return
        if (state.value.phase != AttendanceLinkPhase.DeclineSheet) return
        val current = generation
        val requestId = requestId(code) ?: return show(AttendanceLinkPhase.Invalid)
        viewModelScope.launch { respond(destination, requestId, current, AttendanceIntent.Decline) }
    }

    private suspend fun join(current: Int) {
        val result = invites.redeem(InviteCode(code))
        if (current != generation) return
        when (result) {
            is SaqzResult.Success -> when (result.value.status) {
                InviteRedeemStatus.JOINED -> {
                    val resolved = sharing.resolveLink(AttendanceLinkCode(code))
                    if (current != generation) return
                    when (resolved) {
                        is SaqzResult.Success -> register(resolved.value.groupId.value)
                        is SaqzResult.Failure -> show(
                            if (resolved.error is AttendanceSharingError.InvalidOrExpired) AttendanceLinkPhase.Invalid
                            else AttendanceLinkPhase.Failed,
                        )
                    }
                }
                InviteRedeemStatus.PENDING -> show(AttendanceLinkPhase.Pending)
            }
            is SaqzResult.Failure -> show(when (result.error) {
                InviteError.InvalidOrExpired, is InviteError.Expired, InviteError.GroupDeleted -> AttendanceLinkPhase.Invalid
                else -> AttendanceLinkPhase.Failed
            })
        }
    }

    private fun register(groupId: String) {
        show(AttendanceLinkPhase.Registration)
        emit(AttendanceLinkEffect.Register(groupId))
    }

    private suspend fun respond(
        destination: AttendanceLinkDestination,
        requestId: String,
        current: Int,
        intent: AttendanceIntent,
    ) {
        val response = attendance.respond(destination.groupId, destination.gameId, SelfAttendanceCommand(requestId, intent))
            .onSuccess {
                SaqzAnalytics.attendanceAnswered("link", intent == AttendanceIntent.Confirm)
                notifications?.dismissAttendance(destination.gameId)
            }
        if (current != generation) return
        when (response) {
            is SaqzResult.Success -> update {
                AttendanceLinkState(
                    when (response.value.value.attendance.status) {
                        AttendanceStatus.Waitlisted -> AttendanceLinkPhase.Waitlisted
                        AttendanceStatus.Confirmed -> AttendanceLinkPhase.Confirmed
                        AttendanceStatus.Declined ->
                            if (intent == AttendanceIntent.Decline) AttendanceLinkPhase.Declined else AttendanceLinkPhase.Failed
                    },
                    destination,
                )
            }
            is SaqzResult.Failure -> show(
                if (response.error in listOf(AttendanceError.HiddenResource, AttendanceError.DeadlinePassed, AttendanceError.Frozen))
                    AttendanceLinkPhase.Invalid else AttendanceLinkPhase.Failed,
            )
        }
    }

    private fun show(phase: AttendanceLinkPhase) { update { AttendanceLinkState(phase) } }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class, kotlin.uuid.ExperimentalUuidApi::class)
    private fun requestId(value: String): String? = runCatching {
        require(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]").matches(value))
        val bytes = Base64.UrlSafe.decode("$value=")
        // Metade distinta por intenção: o mesmo código tem um requestId para confirmar e
        // outro para declinar — o backend deduplica por requestId, e são comandos diferentes.
        val (start, end) = if (decline) 16 to 32 else 0 to 16
        Uuid.fromByteArray(bytes.copyOfRange(start, end)).toString()
    }.getOrNull()
}
