package br.com.saqz.groups.presentation.gamedetail
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AutoConfirmationCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
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
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Suppress("LongParameterList")
class GameDetailViewModel(
    val groupId: String,
    val gameId: String,
    private val gameGateway: GameGateway,
    private val groupGateway: GroupGateway,
    private val attendanceGateway: AttendanceGateway,
    private val athleteGateway: AthleteGateway,
) : MviViewModel<GameDetailState, GameDetailIntent, GameDetailEffect>(GameDetailState()) {
    private var loadGeneration = 0
    private var responseGeneration = 0L
    private var autoConfirmationGeneration = 0L
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
            is GameDetailIntent.Respond -> respond(intent.intent)
            is GameDetailIntent.ToggleAutoConfirmation -> toggleAutoConfirmation(intent.enabled)
        }
    }
    private fun load() {
        val generation = ++loadGeneration
        responseGeneration++
        autoConfirmationGeneration++
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
                        is SaqzResult.Success -> loadAttendance(
                            generation,
                            gameResult.value.game,
                            groupResult.value.group,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadAttendance(
        generation: Int,
        game: Game,
        group: br.com.saqz.groups.domain.group.Group,
    ) {
        val detail = when (val result = attendanceGateway.read(GroupId(groupId), gameId)) {
            is SaqzResult.Failure -> return showFailure(generation, result.error.toUiError())
            is SaqzResult.Success -> result.value
        }
        if (generation != loadGeneration) return
        val roster = when (val result = attendanceGateway.roster(GroupId(groupId), gameId)) {
            is SaqzResult.Failure -> return showFailure(generation, result.error.toUiError())
            is SaqzResult.Success -> result.value
        }
        if (generation != loadGeneration) return
        val membershipType = when (val result = athleteGateway.ownProfile()) {
            is SaqzResult.Success -> result.value.memberships
                .firstOrNull { it.groupId == GroupId(groupId) }
                ?.membershipType
            is SaqzResult.Failure -> null
        }
        if (generation != loadGeneration) return
        update {
            it.copy(
                isLoading = false,
                loadFailed = false,
                error = null,
                header = game.toHeader(),
                attendance = detail.toAttendance(),
                memberResponse = detail.ownAttendance?.toResponse(roster),
                responding = false,
                responseFailed = false,
                membershipType = membershipType,
                autoConfirmationVisible = membershipType == AthleteMembershipType.MENSALISTA &&
                    group.gameConfig.autoConfirmEnabled,
                autoConfirmationEnabled = false,
                autoConfirmationUpdating = false,
                autoConfirmationFailed = false,
                isAdmin = group.role != GroupRole.ATHLETE,
            )
        }
    }

    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        if (current.header?.confirmationOpen != true || current.responding) return
        val generation = ++responseGeneration
        val previous = current.memberResponse
        update {
            it.copy(
                memberResponse = GameDetailResponseUi(intent.toResponseStatus()),
                responding = true,
                responseFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.respond(
                GroupId(groupId),
                gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            )
            if (generation != responseGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val roster = when (val rosterResult = attendanceGateway.roster(GroupId(groupId), gameId)) {
                        is SaqzResult.Success -> rosterResult.value
                        is SaqzResult.Failure -> null
                    }
                    if (generation != responseGeneration) return@launch
                    update {
                        it.copy(
                            memberResponse = result.value.value.attendance.toResponse(roster),
                            attendance = result.value.value.detail.toAttendance(),
                            responding = false,
                            responseFailed = false,
                        )
                    }
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        memberResponse = previous,
                        responding = false,
                        responseFailed = true,
                        header = if (result.error == AttendanceError.DeadlinePassed) {
                            it.header?.copy(confirmationOpen = false)
                        } else it.header,
                    )
                }
            }
        }
    }

    private fun toggleAutoConfirmation(enabled: Boolean) {
        val current = state.value
        if (!current.autoConfirmationVisible || current.autoConfirmationUpdating) return
        val generation = ++autoConfirmationGeneration
        val previous = current.autoConfirmationEnabled
        update {
            it.copy(
                autoConfirmationEnabled = enabled,
                autoConfirmationUpdating = true,
                autoConfirmationFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.updateAutoConfirmation(
                GroupId(groupId),
                AutoConfirmationCommand(enabled),
            )
            if (generation != autoConfirmationGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> update {
                    it.copy(
                        autoConfirmationEnabled = result.value.enabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                    )
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        autoConfirmationEnabled = previous,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = true,
                    )
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
            confirmationOpen = status == GameStatus.Published &&
                (deadlineLocal?.let { it.toInstant(zone) > Clock.System.now() } ?: true),
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

    private fun AttendanceDetail.toAttendance() = GameDetailAttendanceUi(
        confirmed = confirmedCount,
        capacity = capacity,
        availableSpots = availableSpots,
        pending = waitlistCount,
    )

    private fun AttendanceEntry.toResponse(roster: AttendanceRoster?) = GameDetailResponseUi(
        status = status.toResponseStatus(),
        waitlistPosition = if (status == br.com.saqz.groups.domain.attendance.AttendanceStatus.Waitlisted) {
            roster?.waitlisted
                ?.indexOfFirst { it.memberId == memberId }
                ?.takeIf { it >= 0 }
                ?.let { it + 1L }
                ?: waitlistPosition
        } else null,
    )

    private fun AttendanceIntent.toResponseStatus() = when (this) {
        AttendanceIntent.Confirm -> GameDetailResponseStatus.Confirmed
        AttendanceIntent.Decline -> GameDetailResponseStatus.Declined
    }

    private fun br.com.saqz.groups.domain.attendance.AttendanceStatus.toResponseStatus() = when (this) {
        br.com.saqz.groups.domain.attendance.AttendanceStatus.Confirmed -> GameDetailResponseStatus.Confirmed
        br.com.saqz.groups.domain.attendance.AttendanceStatus.Declined -> GameDetailResponseStatus.Declined
        br.com.saqz.groups.domain.attendance.AttendanceStatus.Waitlisted -> GameDetailResponseStatus.Waitlisted
    }
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

private fun AttendanceError.toUiError(): GroupUiError = when (this) {
    is AttendanceError.Validation -> GroupUiError.Validation
    AttendanceError.HiddenResource -> GroupUiError.NotFound
    AttendanceError.DeadlinePassed,
    AttendanceError.Frozen,
    AttendanceError.Conflict,
    -> GroupUiError.Conflict
    AttendanceError.Authentication -> GroupUiError.AccessDenied
    is AttendanceError.Data -> GroupUiError.Network
}
