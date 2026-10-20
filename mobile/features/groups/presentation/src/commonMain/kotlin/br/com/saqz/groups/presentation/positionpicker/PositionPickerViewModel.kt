package br.com.saqz.groups.presentation.positionpicker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.PositionSummary
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.attendance.PositionSuggestion
import br.com.saqz.groups.presentation.game.gameHeroDisplay
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A tela de escolher posição em todo "Vou" de jogo com posições (DEB-107, PRES-014). Nada é confirmado antes do toque
 * no botão; antes de enviar a presença é relida, e posição que lotou nesse meio-tempo não é enviada.
 */
@OptIn(ExperimentalUuidApi::class)
class PositionPickerViewModel(
    private val groupId: String,
    private val gameId: String,
    private val origin: PositionPickerOrigin,
    private val savedState: SavedStateHandle,
    private val attendanceGateway: AttendanceGateway,
    private val gameGateway: GameGateway,
    private val athleteGateway: AthleteGateway,
    /** Resposta pelo app limpa o aviso do jogo no aparelho; sem a porta (web, teste) não há o que limpar. */
    private val notifications: NativeNotificationPort? = null,
) : MviViewModel<PositionPickerState, PositionPickerIntent, PositionPickerEffect>(PositionPickerState()) {
    private var loadGeneration = 0
    private var submitGeneration = 0

    init {
        load()
    }

    override fun handleIntent(intent: PositionPickerIntent) {
        when (intent) {
            is PositionPickerIntent.Select -> select(intent.position)
            PositionPickerIntent.Submit -> submit()
            PositionPickerIntent.Retry -> if (state.value.loadError != null) load()
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val attendance = attendanceGateway.read(GroupId(groupId), gameId)
            if (generation != loadGeneration) return@launch
            val game = gameGateway.read(GroupId(groupId), gameId)
            if (generation != loadGeneration) return@launch
            // O perfil só traz a sugestão e o nome do grupo: sem ele a tela abre sem nada marcado.
            val membership = (athleteGateway.ownProfile() as? SaqzResult.Success)?.value?.memberships
                ?.firstOrNull { it.groupId == GroupId(groupId) }
            if (generation != loadGeneration) return@launch
            when {
                attendance is SaqzResult.Failure -> fail(attendance.error.toPickerError())
                game is SaqzResult.Failure -> fail(game.error.toUiError())
                else -> {
                    val positions = (attendance as SaqzResult.Success).value.positions
                    val profile = membership?.position
                    val options = positions.toOptions(profile)
                    // Recriação (processo morto, rotação): a escolha feita volta; senão, a sugestão com vaga.
                    val restored = savedState.get<String>(KEY_SELECTED)
                        ?.let { name -> AthletePosition.entries.firstOrNull { it.name == name } }
                        ?.takeIf { position -> options.any { it.position == position } }
                    val header = (game as SaqzResult.Success).value.game.toUi(membership?.groupName.orEmpty())
                    update {
                        it.copy(
                            loading = false,
                            loadError = null,
                            game = header,
                            options = options,
                            selected = restored ?: PositionSuggestion.initial(profile, positions),
                        )
                    }
                }
            }
        }
    }

    private fun fail(error: GroupUiError) {
        update { it.copy(loading = false, loadError = error) }
    }

    private fun select(position: AthletePosition) {
        val current = state.value
        if (current.loading || current.sending || current.options.none { it.position == position }) return
        if (current.selected != position) savedState.remove<String>(KEY_REQUEST_ID)
        savedState[KEY_SELECTED] = position.name
        update { it.copy(selected = position, raced = null, submitFailed = false) }
    }

    private fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        if (current.withoutPositions) return send(null)
        val chosen = current.selectedOption ?: return
        val generation = ++submitGeneration
        update { it.copy(sending = true, raced = null, submitFailed = false) }
        viewModelScope.launch {
            val fresh = attendanceGateway.read(GroupId(groupId), gameId)
            if (generation != submitGeneration) return@launch
            if (fresh !is SaqzResult.Success) return@launch update { it.copy(sending = false, submitFailed = true) }
            val profile = state.value.options.firstOrNull { it.suggested }?.position
            val options = fresh.value.positions.toOptions(profile)
            val now = options.firstOrNull { it.position == chosen.position }
            // Escolheu com vaga e ela lotou: não envia; a pessoa decide entre a espera dela e outra posição.
            if (now == null || (now.full && !chosen.full)) {
                update { it.copy(options = options, sending = false, raced = chosen.position.takeIf { now != null }) }
                return@launch
            }
            update { it.copy(options = options) }
            respond(generation, chosen.position)
        }
    }

    /** Jogo sem posições na leitura: o mesmo "Vou" de sempre, sem posição (o servidor decide). */
    private fun send(position: AthletePosition?) {
        val generation = ++submitGeneration
        update { it.copy(sending = true, raced = null, submitFailed = false) }
        viewModelScope.launch { respond(generation, position) }
    }

    private suspend fun respond(generation: Int, position: AthletePosition?) {
        val result = attendanceGateway.respond(
            GroupId(groupId),
            gameId,
            SelfAttendanceCommand(requestId(), AttendanceIntent.Confirm, position),
        )
        if (generation != submitGeneration) return
        when (result) {
            is SaqzResult.Success -> {
                SaqzAnalytics.attendanceAnswered(origin.routeValue, true)
                notifications?.dismissAttendance(gameId)
                // Fica "enviando" até a tela sair: um segundo toque não manda outra resposta.
                emit(
                    when (origin) {
                        PositionPickerOrigin.Link -> PositionPickerEffect.LinkResult(result.value.value.attendance.status)
                        PositionPickerOrigin.Push -> PositionPickerEffect.OpenGame(groupId, gameId)
                        PositionPickerOrigin.Home, PositionPickerOrigin.Group, PositionPickerOrigin.Game -> PositionPickerEffect.Close
                    },
                )
            }
            is SaqzResult.Failure -> update { it.copy(sending = false, submitFailed = true) }
        }
    }

    /** Um requestId por posição escolhida: tentar de novo a mesma escolha não vira uma segunda resposta. */
    private fun requestId(): String =
        savedState.get<String>(KEY_REQUEST_ID) ?: Uuid.random().toString().also { savedState[KEY_REQUEST_ID] = it }

    private suspend fun Game.toUi(groupName: String): PositionPickerGameUi {
        val local = runCatching { Instant.parse(startsAt).toLocalDateTime(gameTimeZone(zoneId)) }.getOrNull()
        return PositionPickerGameUi(
            day = local?.day?.toString().orEmpty(),
            month = local?.let { gameShortMonthLabel(it.month.ordinal + 1) }.orEmpty(),
            title = local?.gameHeroDisplay() ?: title,
            durationMinutes = durationMinutes,
            venue = listOfNotNull(venue.name, venue.court).joinToString(" — ").ifBlank { venue.name },
            groupName = groupName,
        )
    }

    private companion object {
        const val KEY_SELECTED = "position-picker.selected"
        const val KEY_REQUEST_ID = "position-picker.request-id"
    }
}

/** Só as posições com vaga configurada; a do perfil leva o selo "Do seu perfil". */
private fun List<PositionSummary>.toOptions(profile: AthletePosition?): List<PositionOptionUi> {
    val suggested = PositionSuggestion.suggested(profile, this)
    return filter { it.slots > 0 }.map {
        PositionOptionUi(
            position = it.position,
            slots = it.slots,
            confirmed = it.confirmed,
            waitlisted = it.waitlisted,
            full = it.isFull,
            suggested = it.position == suggested,
        )
    }
}

private fun AttendanceError.toPickerError(): GroupUiError = when (this) {
    AttendanceError.HiddenResource -> GroupUiError.NotFound
    AttendanceError.Authentication -> GroupUiError.AccessDenied
    else -> GroupUiError.Network
}
