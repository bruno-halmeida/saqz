package br.com.saqz.groups.presentation.gamedetail
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
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
            GameDetailIntent.RequestCancel -> if (state.value.header?.statusTone == GameDetailStatusTone.Published) {
                update { it.copy(cancelDialogOpen = true, cancelFailed = false) }
            }
            GameDetailIntent.DismissCancel -> if (!state.value.cancelling) {
                update { it.copy(cancelDialogOpen = false, cancelFailed = false) }
            }
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
                    val groupResult = groupGateway.read(GroupId(groupId))
                    if (generation != loadGeneration) return@launch
                    when (groupResult) {
                        is SaqzResult.Failure -> showFailure(generation, groupResult.error.toUiError())
                        is SaqzResult.Success -> update {
                            it.copy(
                                isLoading = false,
                                loadFailed = false,
                                error = null,
                                header = gameResult.value.game.toHeader(),
                                attendance = gameResult.value.game.toAttendance(),
                                isAdmin = groupResult.value.group.role != GroupRole.ATHLETE,
                            )
                        }
                    }
                }
            }
        }
    }
    private fun cancel() {
        if (state.value.cancelling) return
        val token = versionToken ?: return
        update { it.copy(cancelling = true, cancelFailed = false) }
        viewModelScope.launch {
            val result = gameGateway.lifecycle(GroupId(groupId), gameId, token, GameLifecycleAction.Cancel)
            when (result) {
                is SaqzResult.Success -> {
                    versionToken = result.value.version
                    update {
                        it.copy(cancelling = false, cancelDialogOpen = false, header = result.value.game.toHeader())
                    }
                    emit(GameDetailEffect.Cancelled)
                }
                is SaqzResult.Failure -> if (result.error is GameError.Conflict) {
                    update { it.copy(cancelling = false, cancelDialogOpen = false, cancelFailed = false) }
                    load()
                } else {
                    update { it.copy(cancelling = false, cancelFailed = true) }
                }
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
        val deadlineLocal = runCatching { Instant.parse(confirmationDeadline) }
            .getOrNull()?.toLocalDateTime(zone)
        val deadlineDateDiffers = deadlineLocal?.date != local?.date
        val deadline = deadlineLocal?.let {
            val date = if (deadlineDateDiffers) "${formatDatePtBr(it.date)} · " else ""
            "$date${it.hour.pad()}:${it.minute.pad()}"
        } ?: confirmationDeadline
        val venueLine = listOfNotNull(venue.name, venue.court).joinToString(" — ")
        return GameDetailHeaderUi(
            statusTone = status.toTone(),
            confirmationDeadline = deadline,
            confirmationDeadlineWeekday = deadlineLocal?.date
                ?.takeIf { deadlineDateDiffers }
                ?.let { GroupWeekday.entries[it.dayOfWeek.ordinal] },
            weekday = local?.let { GroupWeekday.entries[it.date.dayOfWeek.ordinal] },
            dateTime = dateTime,
            venue = venueLine.ifBlank { venue.name },
            durationMinutes = durationMinutes,
            availableSpots = availableSpots,
        )
    }
    // ponytail: o domínio de Game ainda não expõe declinedCount nem pendingCount. As três
    // células continuam visíveis com zero até a leitura de attendance fornecer esses valores.
    private fun Game.toAttendance() = GameDetailAttendanceUi(
        confirmed = confirmedCount,
        capacity = capacity,
        availableSpots = availableSpots,
        declined = 0,
        pending = 0,
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
