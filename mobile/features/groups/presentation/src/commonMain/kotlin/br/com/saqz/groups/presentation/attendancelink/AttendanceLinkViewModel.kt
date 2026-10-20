package br.com.saqz.groups.presentation.attendancelink

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
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
    /** Lê o cadastro guardado no pedido pendente; sem o port, a fase `Pending` segue sem esse passo. */
    private val athletes: AthleteGateway? = null,
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
            AttendanceLinkIntent.Refresh -> refresh()
            AttendanceLinkIntent.Decline -> decline()
            AttendanceLinkIntent.Undo -> undo()
            AttendanceLinkIntent.OpenAppDownload -> appDownload?.appDownloadUrl()?.let(appDownload::openAppDownload)
            AttendanceLinkIntent.PickPosition -> state.value.destination
                ?.takeIf { state.value.phase == AttendanceLinkPhase.ChoosingPosition }
                ?.let { emit(AttendanceLinkEffect.PickPosition(it)) }
        }
    }

    fun retry() {
        val phase = state.value.phase
        if (phase in listOf(AttendanceLinkPhase.Failed, AttendanceLinkPhase.Pending, AttendanceLinkPhase.RequestClosed)) resolve()
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
                is SaqzResult.Success -> proceed(current, result.value, requestId)
            }
        }
    }

    /** Quem já é membro: cadastro pendente leva ao formulário; senão o link responde (ou abre o aviso do "não vou"). */
    private suspend fun proceed(current: Int, destination: AttendanceLinkDestination, requestId: String) {
        if (destination.registrationRequired) return register(destination.groupId.value)
        loadGame(current, destination)
        if (decline) ask(current, destination)
        else if (!choosePosition(current, destination)) respond(destination, requestId, current, AttendanceIntent.Confirm)
    }

    /**
     * Jogo com posições (DEB-107, LINK-002): o "Vou" do link não registra sozinho; abre a tela de escolher posição. Quem
     * já está na lista ou na espera vê o resultado. Devolve `false` em jogo sem posições, que segue respondendo direto.
     */
    private suspend fun choosePosition(current: Int, destination: AttendanceLinkDestination): Boolean {
        val read = attendance.read(destination.groupId, destination.gameId)
        if (current != generation) return true
        // Sem a leitura não dá para saber se o jogo pede posição: nada é registrado, e "Tentar de novo" refaz o link.
        val detail = (read as? SaqzResult.Success)?.value ?: return true.also { show(AttendanceLinkPhase.Failed) }
        if (!detail.hasPositions) return false
        if (!showPositionResult(destination, detail)) {
            update { AttendanceLinkState(AttendanceLinkPhase.ChoosingPosition, destination, it.game) }
            emit(AttendanceLinkEffect.PickPosition(destination))
        }
        return true
    }

    /** Já respondeu "Vou" neste jogo com posições: o cartão do resultado com a posição. */
    private fun showPositionResult(destination: AttendanceLinkDestination, detail: AttendanceDetail): Boolean {
        val own = detail.ownAttendance ?: return false
        val phase = when (own.status) {
            AttendanceStatus.Confirmed -> AttendanceLinkPhase.Confirmed
            AttendanceStatus.Waitlisted -> AttendanceLinkPhase.Waitlisted
            AttendanceStatus.Declined -> return false
        }
        val summary = detail.positions.firstOrNull { it.position == own.position }
        update {
            AttendanceLinkState(
                phase = phase,
                destination = destination,
                game = it.game,
                waitlistPosition = own.waitlistPosition,
                position = summary?.let { position -> AttendanceLinkPosition(position.position, position.slots, position.confirmed) },
            )
        }
        return true
    }

    /** Volta da tela de posição: confirmou (mostra o resultado) ou saiu sem confirmar (fica, com o "Vou" para reabrir). */
    private fun refreshChoice() {
        val destination = state.value.destination ?: return
        val current = generation
        viewModelScope.launch {
            val read = attendance.read(destination.groupId, destination.gameId)
            if (current != generation || state.value.phase != AttendanceLinkPhase.ChoosingPosition) return@launch
            if (read is SaqzResult.Success && showPositionResult(destination, read.value)) loadGame(current, destination)
        }
    }

    /** Atualização silenciosa da fase `Pending`: nada volta para `Loading`, o cartão e os textos ficam. */
    private fun refresh() {
        val before = state.value
        if (before.phase == AttendanceLinkPhase.ChoosingPosition) return refreshChoice()
        if (before.phase != AttendanceLinkPhase.Pending || before.refreshing) return
        val current = generation
        update { it.copy(refreshing = true) }
        viewModelScope.launch {
            // Só leituras: refazer o `redeem` a cada volta à tela criaria um pedido novo depois de recusado ou cancelado.
            val requestId = requestId(code) ?: return@launch show(AttendanceLinkPhase.Invalid)
            val resolved = sharing.resolveLink(AttendanceLinkCode(code))
            if (current != generation) return@launch
            when (resolved) {
                is SaqzResult.Success -> proceed(current, resolved.value, requestId)
                is SaqzResult.Failure -> when (val error = resolved.error) {
                    is AttendanceSharingError.GameClosed -> show(error.reason.phase())
                    AttendanceSharingError.InvalidOrExpired -> refreshRequest(current, before.pendingGroupId)
                    else -> update { it.copy(refreshing = false) }
                }
            }
        }
    }

    /** Ainda não é membro: o pedido segue pendente, ou sumiu (recusado/cancelado). */
    private suspend fun refreshRequest(current: Int, groupId: String?) {
        val request = if (groupId == null) null else athletes?.ownEntryRequest(GroupId(groupId))
        if (current != generation) return
        when {
            request is SaqzResult.Success -> {
                readPendingPreview(current)
                val saved = request.value.athleteProfile != null
                if (current == generation) update { it.copy(pendingRegistrationSaved = saved, refreshing = false) }
            }
            request is SaqzResult.Failure && request.error.let { it is AthleteError.DataFailure && it.error == DataError.NotFound } ->
                show(AttendanceLinkPhase.RequestClosed)
            else -> update { it.copy(refreshing = false) }
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

    /**
     * Quem aguarda aprovação ainda não lê o jogo: o cartão e os nomes vêm da prévia do convite (o local já
     * escondido) e o cadastro guardado vem do próprio pedido. Falha numa leitura não apaga o que já está na tela.
     */
    private suspend fun readPending(current: Int, groupId: String) {
        readPendingPreview(current)
        val request = athletes?.ownEntryRequest(GroupId(groupId))
        if (current != generation || request !is SaqzResult.Success) return
        update {
            if (it.phase == AttendanceLinkPhase.Pending) it.copy(pendingRegistrationSaved = request.value.athleteProfile != null) else it
        }
    }

    private suspend fun readPendingPreview(current: Int) {
        val preview = invites.preview(InviteCode(code))
        if (current != generation || preview !is SaqzResult.Success) return
        val card = preview.value.nextGame?.toCard(timeZone)
        update {
            if (it.phase != AttendanceLinkPhase.Pending) it else it.copy(
                game = card ?: it.game,
                pendingGroupName = preview.value.groupName,
                pendingInviterName = preview.value.inviterName,
            )
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

    /** Desfazer a resposta recém-gravada: o "não vou" com o requestId próprio; se falhar, a confirmação continua valendo. */
    private fun undo() {
        val before = state.value
        val destination = before.destination ?: return
        if (before.phase !in listOf(AttendanceLinkPhase.Confirmed, AttendanceLinkPhase.Waitlisted) || before.declining) return
        val current = generation
        val requestId = requestId(code, forDecline = true) ?: return
        update { it.copy(declining = true) }
        viewModelScope.launch {
            val command = SelfAttendanceCommand(requestId, AttendanceIntent.Decline)
            val response = attendance.respond(destination.groupId, destination.gameId, command)
            if (current != generation) return@launch
            if (response is SaqzResult.Success && response.value.value.attendance.status == AttendanceStatus.Declined) {
                SaqzAnalytics.attendanceAnswered("link", false)
                update { AttendanceLinkState(AttendanceLinkPhase.Declined, destination, it.game) }
            } else {
                update { it.copy(declining = false) }
            }
        }
    }

    private suspend fun join(current: Int) {
        val result = invites.redeem(InviteCode(code))
        if (current != generation) return
        when (result) {
            is SaqzResult.Success -> when (result.value.status) {
                InviteRedeemStatus.JOINED -> afterJoined(current)
                InviteRedeemStatus.PENDING -> {
                    val groupId = result.value.groupId.value
                    update { AttendanceLinkState(AttendanceLinkPhase.Pending, pendingGroupId = groupId) }
                    viewModelScope.launch { readPending(current, groupId) }
                }
            }
            is SaqzResult.Failure -> show(when (result.error) {
                is InviteError.InvalidOrExpired, is InviteError.Expired, InviteError.GroupDeleted -> AttendanceLinkPhase.Invalid
                InviteError.GroupNotReceiving -> AttendanceLinkPhase.NotReceiving
                else -> AttendanceLinkPhase.Failed
            })
        }
    }

    /** A pessoa entrou no grupo: resolve o link de novo, já como membro, e segue o fluxo normal. */
    private suspend fun afterJoined(current: Int) {
        val requestId = requestId(code) ?: return show(AttendanceLinkPhase.Invalid)
        val resolved = sharing.resolveLink(AttendanceLinkCode(code))
        if (current != generation) return
        when (resolved) {
            is SaqzResult.Success -> proceed(current, resolved.value, requestId)
            is SaqzResult.Failure -> show(
                when (val error = resolved.error) {
                    AttendanceSharingError.InvalidOrExpired -> AttendanceLinkPhase.Invalid
                    is AttendanceSharingError.GameClosed -> error.reason.phase()
                    else -> AttendanceLinkPhase.Failed
                },
            )
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
            is SaqzResult.Success -> {
                update {
                    AttendanceLinkState(
                        phase = when (response.value.value.attendance.status) {
                            AttendanceStatus.Waitlisted -> AttendanceLinkPhase.Waitlisted
                            AttendanceStatus.Confirmed -> AttendanceLinkPhase.Confirmed
                            AttendanceStatus.Declined ->
                                if (intent == AttendanceIntent.Decline) AttendanceLinkPhase.Declined else AttendanceLinkPhase.Failed
                        },
                        destination = destination,
                        game = it.game,
                        waitlistPosition = response.value.value.attendance.waitlistPosition,
                    )
                }
                // A contagem do cartão já tem de incluir a resposta que acabou de ser gravada.
                loadGame(current, destination)
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
    private fun requestId(value: String, forDecline: Boolean = decline): String? = runCatching {
        require(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]").matches(value))
        val bytes = Base64.UrlSafe.decode("$value=")
        // Metade distinta por intenção: o mesmo código tem um requestId para confirmar e
        // outro para declinar — o backend deduplica por requestId, e são comandos diferentes.
        val (start, end) = if (forDecline) 16 to 32 else 0 to 16
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
        capacity = capacity,
        confirmedCount = confirmedCount,
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
