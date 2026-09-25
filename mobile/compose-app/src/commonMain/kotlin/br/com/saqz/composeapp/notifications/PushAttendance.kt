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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** O que a notificação mostra depois de responder presença sem abrir o app. */
enum class PushAttendanceOutcome { Confirmed, Waitlisted, Declined, Closed, Failed }

/** Botões "Confirmar" / "Não vou" do push: mesmo gateway (auth, retry, idempotência) da tela do jogo. */
object PushAttendance {
    @OptIn(ExperimentalUuidApi::class)
    fun respond(groupId: String, gameId: String, confirm: Boolean, done: (PushAttendanceOutcome) -> Unit) {
        val gateway = KoinPlatformTools.defaultContext().get().get<AttendanceGateway>()
        val intent = if (confirm) AttendanceIntent.Confirm else AttendanceIntent.Decline
        CoroutineScope(Dispatchers.Main).launch {
            done(gateway.respond(GroupId(groupId), gameId, SelfAttendanceCommand(Uuid.random().toString(), intent)).toOutcome())
        }
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
