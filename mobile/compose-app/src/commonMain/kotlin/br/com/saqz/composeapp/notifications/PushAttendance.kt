package br.com.saqz.composeapp.notifications

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** O que a notificação mostra depois de responder presença sem abrir o app. */
enum class PushAttendanceOutcome { Confirmed, Waitlisted, Declined, Closed, Failed, NoResponse }

/** Botões "Confirmar" / "Não vou" do push: mesmo gateway (auth, retry, idempotência) da tela do jogo. */
class PushAttendance(private val gateway: AttendanceGateway, private val scope: CoroutineScope) {
    @OptIn(ExperimentalUuidApi::class)
    fun respond(groupId: String, gameId: String, confirm: Boolean, done: (PushAttendanceOutcome) -> Unit) {
        val intent = if (confirm) AttendanceIntent.Confirm else AttendanceIntent.Decline
        scope.launch {
            // ponytail: o teto cabe na janela do goAsync do Android (~10 s); WorkManager se a rede pedir mais.
            val result = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
                gateway.respond(GroupId(groupId), gameId, SelfAttendanceCommand(Uuid.random().toString(), intent))
            }
            // Estourou o teto: o servidor pode ter gravado; não afirmar falha nem convidar a repetir.
            done(result?.toOutcome() ?: PushAttendanceOutcome.NoResponse)
        }
    }

    private companion object {
        const val RESPONSE_TIMEOUT_MS = 8_000L
    }
}

internal fun SaqzResult<VersionedAttendanceMutation, AttendanceError>.toOutcome(): PushAttendanceOutcome = when (this) {
    is SaqzResult.Success -> when (value.value.attendance.status) {
        AttendanceStatus.Confirmed -> PushAttendanceOutcome.Confirmed
        AttendanceStatus.Waitlisted -> PushAttendanceOutcome.Waitlisted
        AttendanceStatus.Declined -> PushAttendanceOutcome.Declined
    }
    is SaqzResult.Failure -> when (error) {
        AttendanceError.DeadlinePassed, AttendanceError.Frozen -> PushAttendanceOutcome.Closed
        else -> PushAttendanceOutcome.Failed
    }
}
