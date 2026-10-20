package br.com.saqz.groups.presentation.attendancelink

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.groups.domain.communication.AppDownloadPort
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.membership.*
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AttendanceLinkViewModel(
    private val code: String,
    private val decline: Boolean = false,
    private val sharing: AttendanceSharingGateway,
    private val attendance: AttendanceGateway,
    private val invites: InviteGateway,
    private val notifications: NativeNotificationPort? = null,
    /** Só no web app: a loja do app oferecida depois da resposta. */
    private val appDownload: AppDownloadPort? = null,
    /** O cartão do jogo de quem já é membro; sem o port, a tela segue sem cartão. */
    private val games: GameGateway? = null,
    /** Horário do cartão de quem aguarda aprovação (a prévia traz o instante); sem o port, UTC. */
    private val timeZonePort: GroupSystemTimeZonePort? = null,
) : MviViewModel<AttendanceLinkState, AttendanceLinkIntent, AttendanceLinkEffect>(AttendanceLinkState()) {
    /** Há loja do app para oferecer depois da resposta (confirmada ou lista de espera). */
    val appDownloadAvailable: Boolean = appDownload?.appDownloadUrl() != null
    private var generation = 0
    private var timeZone: TimeZone = TimeZone.UTC
    init {
        timeZonePort?.detect { result ->
            if (result is GroupSystemTimeZoneResult.Available) runCatching { TimeZone.of(result.value.id) }.onSuccess { timeZone = it }
        }
        resolve()
    }

    override fun handleIntent(intent: AttendanceLinkIntent) {
        when (intent) {
            AttendanceLinkIntent.Retry -> retry()
            AttendanceLinkIntent.Decline -> decline()
            AttendanceLinkIntent.OpenAppDownload -> appDownload?.appDownloadUrl()?.let(appDownload::openAppDownload)
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
                is SaqzResult.Failure -> when (val error = result.error) {
                    // Código de jogo conhecido: não é convite, então não tenta entrar no grupo.
                    is AttendanceSharingError.GameClosed -> show(error.reason.phase())
                    AttendanceSharingError.InvalidOrExpired -> join(current)
                    else -> show(AttendanceLinkPhase.Failed)
                }
                is SaqzResult.Success -> {
                    val destination = result.value
                    if (!destination.registrationRequired) loadGame(current, destination)
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
        update { AttendanceLinkState(AttendanceLinkPhase.DeclineSheet, destination, it.game) }
    }

    /** O cartão chega quando chegar: uma falha na leitura do jogo não atrapalha a resposta. */
    private fun loadGame(current: Int, destination: AttendanceLinkDestination) {
        val gateway = games ?: return
        viewModelScope.launch {
            val result = gateway.read(destination.groupId, destination.gameId)
            if (current != generation || result !is SaqzResult.Success) return@launch
            val card = result.value.game.toCard()
            update { if (it.destination == destination) it.copy(game = card) else it }
        }
    }

    /** Quem aguarda aprovação ainda não lê o jogo: o cartão vem da prévia do convite, com o local já escondido. */
    private fun loadPreviewGame(current: Int) {
        viewModelScope.launch {
            val preview = invites.preview(InviteCode(code))
            if (current != generation || preview !is SaqzResult.Success) return@launch
            val card = preview.value.nextGame?.toCard(timeZone) ?: return@launch
            update { if (it.phase == AttendanceLinkPhase.Pending) it.copy(game = card) else it }
        }
    }

    private fun decline() {
        val destination = state.value.destination ?: return
        if (state.value.phase != AttendanceLinkPhase.DeclineSheet || state.value.declining) return
        val current = generation
        val requestId = requestId(code) ?: return show(AttendanceLinkPhase.Invalid)
        // Toda resposta troca o estado inteiro, então a marca some sozinha quando ela volta.
        update { it.copy(declining = true) }
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
                            when (val error = resolved.error) {
                                AttendanceSharingError.InvalidOrExpired -> AttendanceLinkPhase.Invalid
                                is AttendanceSharingError.GameClosed -> error.reason.phase()
                                else -> AttendanceLinkPhase.Failed
                            },
                        )
                    }
                }
                InviteRedeemStatus.PENDING -> {
                    show(AttendanceLinkPhase.Pending)
                    loadPreviewGame(current)
                }
            }
            is SaqzResult.Failure -> show(when (result.error) {
                is InviteError.InvalidOrExpired, is InviteError.Expired, InviteError.GroupDeleted -> AttendanceLinkPhase.Invalid
                InviteError.GroupNotReceiving -> AttendanceLinkPhase.NotReceiving
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
                    phase = when (response.value.value.attendance.status) {
                        AttendanceStatus.Waitlisted -> AttendanceLinkPhase.Waitlisted
                        AttendanceStatus.Confirmed -> AttendanceLinkPhase.Confirmed
                        AttendanceStatus.Declined ->
                            if (intent == AttendanceIntent.Decline) AttendanceLinkPhase.Declined else AttendanceLinkPhase.Failed
                    },
                    destination = destination,
                    game = it.game,
                )
            }
            is SaqzResult.Failure -> show(
                if (response.error in listOf(AttendanceError.HiddenResource, AttendanceError.DeadlinePassed, AttendanceError.Frozen))
                    AttendanceLinkPhase.Invalid else AttendanceLinkPhase.Failed,
            )
        }
    }

    private fun AttendanceLinkClosedReason.phase() = when (this) {
        AttendanceLinkClosedReason.GameStarted -> AttendanceLinkPhase.GameStarted
        AttendanceLinkClosedReason.GameCancelled -> AttendanceLinkPhase.GameCancelled
        AttendanceLinkClosedReason.GameUnpublished -> AttendanceLinkPhase.GameUnpublished
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

private fun Game.toCard(): AttendanceLinkGame {
    val day = runCatching { LocalDate.parse(localDate) }.getOrNull()
    return AttendanceLinkGame(
        weekdayCode = day?.dayOfWeek?.name.orEmpty(),
        date = day?.toDayMonth() ?: localDate,
        time = localTime.toHourMinute(),
        venueName = venue.name,
        court = venue.court,
    )
}

private fun InviteNextGame.toCard(timeZone: TimeZone): AttendanceLinkGame {
    val local = runCatching { Instant.parse(startsAt).toLocalDateTime(timeZone) }.getOrNull()
        ?: return AttendanceLinkGame("", startsAt, "", venueName, court, groupNext = true)
    return AttendanceLinkGame(
        weekdayCode = local.date.dayOfWeek.name,
        date = local.date.toDayMonth(),
        time = "${local.hour.toString().padStart(2, '0')}h${local.minute.toString().padStart(2, '0')}",
        venueName = venueName,
        court = court,
        groupNext = true,
    )
}

private fun LocalDate.toDayMonth(): String =
    "${day.toString().padStart(2, '0')}/${(month.ordinal + 1).toString().padStart(2, '0')}"

/** "19:30" ou "19:30:00" viram "19h30". */
private fun String.toHourMinute(): String {
    val parts = split(":")
    return if (parts.size >= 2) "${parts[0]}h${parts[1]}" else this
}
