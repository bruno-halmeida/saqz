package br.com.saqz.groups.presentation.gamedetail
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
class GameDetailViewModel(
    val groupId: String,
    val gameId: String,
    private val gameGateway: GameGateway,
    private val groupGateway: GroupGateway,
) : MviViewModel<GameDetailState, GameDetailIntent, GameDetailEffect>(GameDetailState()) {
    private var loadGeneration = 0
    private var versionToken: GameVersionToken? = null
    init {
        load()
    }
    override fun onIntent(intent: GameDetailIntent) {
        when (intent) {
            GameDetailIntent.Retry -> load()
            GameDetailIntent.Edit -> emit(GameDetailEffect.OpenEditor)
            GameDetailIntent.RequestCancel -> update { it.copy(cancelDialogOpen = true, cancelFailed = false) }
            GameDetailIntent.DismissCancel -> update { it.copy(cancelDialogOpen = false, cancelFailed = false) }
            GameDetailIntent.ConfirmCancel -> cancel()
        }
    }
    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            val gameResult = gameGateway.read(GroupId(groupId), gameId)
            if (generation != loadGeneration) return@launch
            when (gameResult) {
                is SaqzResult.Failure -> showFailure(generation, gameResult.error.toUiError())
                is SaqzResult.Success -> {
                    versionToken = gameResult.value.version
                    val role = groupGateway.read(GroupId(groupId))
                    if (generation != loadGeneration) return@launch
                    update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            error = null,
                            header = gameResult.value.game.toHeader(),
                            attendance = gameResult.value.game.toAttendance(),
                            isAdmin = role is SaqzResult.Success && role.value.group.role != GroupRole.ATHLETE,
                        )
                    }
                }
            }
        }
    }
    private fun cancel() {
        val token = versionToken ?: return
        update { it.copy(cancelling = true, cancelFailed = false) }
        viewModelScope.launch {
            val result = gameGateway.lifecycle(GroupId(groupId), gameId, token, GameLifecycleAction.Cancel)
            if (result is SaqzResult.Success) {
                versionToken = result.value.version
                update {
                    it.copy(cancelling = false, cancelDialogOpen = false, header = result.value.game.toHeader())
                }
                emit(GameDetailEffect.Cancelled)
            } else {
                update { it.copy(cancelling = false, cancelFailed = true) }
            }
        }
    }
    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }
    private fun Game.toHeader(): GameDetailHeaderUi {
        val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.UTC }
        val local = runCatching { Instant.parse(startsAt) }.getOrNull()?.toLocalDateTime(zone)
        val dateTime = if (local != null) {
            "${formatDatePtBr(local.date)} · ${local.hour.pad()}:${local.minute.pad()}"
        } else {
            title
        }
        val deadline = runCatching { Instant.parse(confirmationDeadline) }
            .getOrNull()?.toLocalDateTime(zone)
            ?.let { "${it.hour.pad()}:${it.minute.pad()}" } ?: confirmationDeadline
        val venueLine = listOfNotNull(venue.name, venue.court).joinToString(" — ")
        return GameDetailHeaderUi(
            statusTone = status.toTone(),
            confirmationDeadline = deadline,
            weekday = local?.let { GroupWeekday.entries[it.date.dayOfWeek.ordinal] } ?: GroupWeekday.MONDAY,
            dateTime = dateTime,
            venue = venueLine.ifBlank { venue.name },
            durationMinutes = durationMinutes,
            availableSpots = availableSpots,
        )
    }
    // ponytail: o domínio não expõe declinedCount nem pendingCount, então "Não vou" e
    // "Pendentes" chegam nulos e a UI omite. Quando o AttendanceGateway entregar as
    // contagens por status, estes campos passam a ser derivados aqui.
    private fun Game.toAttendance() = GameDetailAttendanceUi(
        confirmed = confirmedCount,
        capacity = capacity,
        availableSpots = availableSpots,
        out = null,
        pending = null,
    )
    private fun Int.pad(): String = if (this < 10) "0$this" else toString()
}
private fun GameStatus.toTone(): GameDetailStatusTone = when (this) {
    GameStatus.Draft -> GameDetailStatusTone.Draft
    GameStatus.Published -> GameDetailStatusTone.Published
    GameStatus.Cancelled -> GameDetailStatusTone.Cancelled
    GameStatus.Completed -> GameDetailStatusTone.Completed
}
private fun formatDatePtBr(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"
