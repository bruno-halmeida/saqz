package br.com.saqz.groups.presentation.details

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AutoConfirmationCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("LargeClass")
class GroupDetailsViewModel(
    private val groupId: String,
    private val groupGateway: GroupGateway,
    private val gameGateway: GameGateway,
    private val attendanceGateway: AttendanceGateway,
    private val athleteGateway: AthleteGateway,
    private val now: GroupNowPort,
) : MviViewModel<GroupDetailsState, GroupDetailsIntent, GroupDetailsEffect>(GroupDetailsState()) {

    private var loadGeneration = 0
    private var responseGeneration = 0L
    private var autoConfirmationGeneration = 0L
    private var rosterGeneration = 0L

    init {
        load()
    }

    override fun onIntent(intent: GroupDetailsIntent) {
        when (intent) {
            GroupDetailsIntent.Retry -> load()
            GroupDetailsIntent.CreateNextGame -> emit(GroupDetailsEffect.OpenCreateGame(groupId))
            GroupDetailsIntent.EditGroup -> emit(GroupDetailsEffect.OpenEdit(groupId))
            GroupDetailsIntent.EditVenue -> emit(GroupDetailsEffect.OpenEdit(groupId))
            GroupDetailsIntent.ManageMembers,
            GroupDetailsIntent.ViewAllMembers,
            -> emit(GroupDetailsEffect.OpenMembers(groupId))
            GroupDetailsIntent.ManageSchedule,
            GroupDetailsIntent.OpenSchedule,
            -> emit(GroupDetailsEffect.OpenSchedule(groupId))
            GroupDetailsIntent.InviteByLink,
            GroupDetailsIntent.Invite,
            -> emit(GroupDetailsEffect.OpenInviteLink(groupId))
            GroupDetailsIntent.OpenCashbox -> emit(GroupDetailsEffect.OpenCashbox(groupId))
            GroupDetailsIntent.OpenVenueMap -> emit(GroupDetailsEffect.OpenMap)
            GroupDetailsIntent.Leave -> emit(GroupDetailsEffect.Left)
            GroupDetailsIntent.RetryRoster -> retryRoster()
            GroupDetailsIntent.ConfirmAttendance,
            GroupDetailsIntent.ViewGame,
            GroupDetailsIntent.NotifyPending,
            GroupDetailsIntent.OpenNotices,
            GroupDetailsIntent.OpenChat,
            -> Unit
            is GroupDetailsIntent.Respond -> respond(intent.intent)
            is GroupDetailsIntent.ToggleAutoConfirmation -> toggleAutoConfirmation(intent.enabled)
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        responseGeneration++
        autoConfirmationGeneration++
        rosterGeneration++
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            when (val groupResult = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Failure -> showFailure(generation, groupResult.error.toUiError())
                is SaqzResult.Success -> when (val gamesResult = gameGateway.list(GroupId(groupId))) {
                    is SaqzResult.Failure -> showFailure(generation, gamesResult.error.toUiError())
                    is SaqzResult.Success -> loadNextGame(generation, groupResult.value.group, gamesResult.value)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun loadNextGame(generation: Int, group: Group, games: List<Game>) {
        if (generation != loadGeneration) return
        val game = games.nextPublishedGame(now.now())
        if (game == null) {
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = null,
                    nextGame = null,
                    attendance = null,
                    memberResponse = null,
                    membershipType = null,
                    autoConfirmationVisible = false,
                ).from(group)
            }
            return
        }
        val detailResult = attendanceGateway.read(GroupId(groupId), game.id)
        if (generation != loadGeneration) return
        val rosterResult = attendanceGateway.roster(GroupId(groupId), game.id)
        if (generation != loadGeneration) return
        val profileResult = athleteGateway.ownProfile()
        if (generation != loadGeneration) return

        when {
            detailResult is SaqzResult.Failure -> showFailure(generation, detailResult.error.toUiError())
            rosterResult is SaqzResult.Failure -> showFailure(generation, rosterResult.error.toUiError())
            profileResult is SaqzResult.Failure -> showFailure(generation, profileResult.error.toUiError())
            else -> {
                val detail = (detailResult as SaqzResult.Success).value
                val roster = (rosterResult as SaqzResult.Success).value
                val membershipType = (profileResult as SaqzResult.Success).value.memberships
                    .firstOrNull { it.groupId == GroupId(groupId) }
                    ?.membershipType
                update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        error = null,
                        nextGame = game.toNextGame(detail, roster),
                        attendance = detail.toAttendance(),
                        memberResponse = detail.ownAttendance?.toResponse(roster),
                        responding = false,
                        responseFailed = false,
                        membershipType = membershipType,
                        autoConfirmationVisible = group.role == GroupRole.ATHLETE &&
                            membershipType == AthleteMembershipType.MENSALISTA &&
                            group.gameConfig.autoConfirmEnabled,
                        autoConfirmationEnabled = detail.autoConfirmEnabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                        rosterStale = false,
                        rosterRefreshing = false,
                    ).from(group)
                }
            }
        }
    }

    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        val game = current.nextGame ?: return
        if (current.isAdmin || !game.confirmationOpen || current.responding) return
        if (!game.confirmationIsOpen()) {
            update { it.copy(nextGame = game.copy(confirmationOpen = false)) }
            return
        }
        val generation = ++responseGeneration
        rosterGeneration++
        val loadAtStart = loadGeneration
        val previousResponse = current.memberResponse
        update {
            it.copy(
                memberResponse = GroupDetailsResponseUi(intent.toResponseStatus()),
                responding = true,
                responseFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.respond(
                GroupId(groupId),
                game.gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            )
            if (generation != responseGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val rosterResult = attendanceGateway.roster(GroupId(groupId), game.gameId)
                    if (generation != responseGeneration || loadAtStart != loadGeneration) return@launch
                    val roster = (rosterResult as? SaqzResult.Success)?.value
                    val detail = result.value.value.detail
                    val response = result.value.value.attendance.toResponse(roster)
                    update {
                        it.copy(
                            nextGame = it.nextGame?.reconcile(detail, roster),
                            attendance = detail.toAttendance(),
                            memberResponse = if (roster != null) response.reconcileRoster(roster) else response,
                            responding = false,
                            responseFailed = false,
                            rosterStale = rosterResult is SaqzResult.Failure,
                            rosterRefreshing = false,
                        )
                    }
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        memberResponse = previousResponse,
                        responding = false,
                        responseFailed = result.error != AttendanceError.Frozen,
                        nextGame = if (result.error == AttendanceError.DeadlinePassed || result.error == AttendanceError.Frozen) {
                            it.nextGame?.copy(confirmationOpen = false)
                        } else it.nextGame,
                    )
                }
            }
        }
    }

    private fun retryRoster() {
        val game = state.value.nextGame ?: return
        if (!state.value.rosterStale || state.value.rosterRefreshing || state.value.responding) return
        val generation = ++rosterGeneration
        val loadAtStart = loadGeneration
        update { it.copy(rosterRefreshing = true) }
        viewModelScope.launch {
            val roster = attendanceGateway.roster(GroupId(groupId), game.gameId)
            val detail = attendanceGateway.read(GroupId(groupId), game.gameId)
            when {
                roster is SaqzResult.Success && detail is SaqzResult.Success ->
                    if (generation == rosterGeneration && loadAtStart == loadGeneration) {
                    update {
                        it.copy(
                            nextGame = it.nextGame?.reconcile(detail.value, roster.value),
                            attendance = detail.value.toAttendance(),
                            memberResponse = it.memberResponse?.reconcileRoster(roster.value),
                            rosterStale = false,
                            rosterRefreshing = false,
                        )
                    }
                }
                else -> if (generation == rosterGeneration && loadAtStart == loadGeneration) {
                    update { it.copy(rosterStale = true, rosterRefreshing = false) }
                }
            }
        }
    }

    private fun toggleAutoConfirmation(enabled: Boolean) {
        val current = state.value
        if (!current.autoConfirmationVisible || current.autoConfirmationUpdating) return
        val gameId = current.nextGame?.gameId
        val generation = ++autoConfirmationGeneration
        val loadAtStart = loadGeneration
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
            if (generation != autoConfirmationGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> update {
                    it.copy(
                        autoConfirmationEnabled = result.value.enabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                    )
                }
                is SaqzResult.Failure -> reconcileAutoConfirmationFailure(
                    error = result.error,
                    gameId = gameId,
                    generation = generation,
                    loadAtStart = loadAtStart,
                    previous = previous,
                )
            }
        }
    }

    private suspend fun reconcileAutoConfirmationFailure(
        error: AttendanceError,
        gameId: String?,
        generation: Long,
        loadAtStart: Int,
        previous: Boolean,
    ) {
        if (error !is AttendanceError.Data || gameId == null) {
            rollbackAutoConfirmation(previous, generation, loadAtStart)
            return
        }
        when (val detail = attendanceGateway.read(GroupId(groupId), gameId)) {
            is SaqzResult.Success -> if (generation == autoConfirmationGeneration && loadAtStart == loadGeneration) {
                update {
                    it.copy(
                        autoConfirmationEnabled = detail.value.autoConfirmEnabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                    )
                }
            }
            is SaqzResult.Failure -> rollbackAutoConfirmation(previous, generation, loadAtStart)
        }
    }

    private fun rollbackAutoConfirmation(previous: Boolean, generation: Long, loadAtStart: Int) {
        if (generation != autoConfirmationGeneration || loadAtStart != loadGeneration) return
        update {
            it.copy(
                autoConfirmationEnabled = previous,
                autoConfirmationUpdating = false,
                autoConfirmationFailed = true,
            )
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun AttendanceDetail.toAttendance() = AttendanceSummaryUi(
        confirmedCount = confirmedCount,
        capacity = capacity,
        going = confirmedCount,
        notGoing = declinedCount,
        pending = pendingCount,
        availableSpots = availableSpots,
    )

    private fun Game.toNextGame(detail: AttendanceDetail, roster: AttendanceRoster) = NextGameUi(
        gameId = id,
        date = displayDate(),
        venue = listOfNotNull(venue.name, venue.court).joinToString(" — "),
        deadline = displayDeadline(),
        confirmationDeadline = confirmationDeadline,
        confirmedCount = detail.confirmedCount,
        capacity = detail.capacity,
        confirmedNames = roster.confirmed.map { it.displayName },
        availableSpots = detail.availableSpots,
        confirmationOpen = status == GameStatus.Published && deadlineIsOpen(),
        hasGameFee = gameFeeCents != null,
    )

    private fun NextGameUi.reconcile(detail: AttendanceDetail, roster: AttendanceRoster?): NextGameUi = copy(
        confirmedCount = detail.confirmedCount,
        capacity = detail.capacity,
        confirmedNames = roster?.confirmed?.map { it.displayName } ?: confirmedNames,
        availableSpots = detail.availableSpots,
    )

    private fun NextGameUi.reconcileRoster(roster: AttendanceRoster) = copy(
        confirmedNames = roster.confirmed.map { it.displayName },
    )

    private fun AttendanceEntry.toResponse(roster: AttendanceRoster?) = GroupDetailsResponseUi(
        status = status.toResponseStatus(),
        memberId = memberId,
        waitlistPosition = if (status == AttendanceStatus.Waitlisted) {
            roster?.waitlisted
                ?.indexOfFirst { it.memberId == memberId }
                ?.takeIf { it >= 0 }
                ?.let { it + 1L }
                ?: waitlistPosition
        } else null,
    )

    private fun NextGameUi.confirmationIsOpen() = runCatching {
        Instant.parse(confirmationDeadline) > now.now()
    }.getOrDefault(false)

    private fun GroupDetailsResponseUi.reconcileRoster(roster: AttendanceRoster): GroupDetailsResponseUi {
        val id = memberId ?: return this
        return when {
            roster.confirmed.any { it.memberId == id } -> copy(
                status = GroupDetailsResponseStatus.Confirmed,
                waitlistPosition = null,
            )
            roster.waitlisted.any { it.memberId == id } -> copy(
                status = GroupDetailsResponseStatus.Waitlisted,
                waitlistPosition = roster.waitlisted.indexOfFirst { it.memberId == id } + 1L,
            )
            status != GroupDetailsResponseStatus.Declined -> copy(
                status = GroupDetailsResponseStatus.Declined,
                waitlistPosition = null,
            )
            else -> this
        }
    }

    private fun AttendanceIntent.toResponseStatus() = when (this) {
        AttendanceIntent.Confirm -> GroupDetailsResponseStatus.Confirmed
        AttendanceIntent.Decline -> GroupDetailsResponseStatus.Declined
    }

    private fun AttendanceStatus.toResponseStatus() = when (this) {
        AttendanceStatus.Confirmed -> GroupDetailsResponseStatus.Confirmed
        AttendanceStatus.Declined -> GroupDetailsResponseStatus.Declined
        AttendanceStatus.Waitlisted -> GroupDetailsResponseStatus.Waitlisted
    }

    private fun Game.displayDate(): String {
        val date = runCatching { LocalDate.parse(localDate) }.getOrNull()
        return date?.let {
            "${it.day.toString().padStart(2, '0')}/${it.month.ordinal.plus(1).toString().padStart(2, '0')} · " +
                localTime.take(5)
        }
            ?: "$localDate · ${localTime.take(5)}"
    }

    private fun Game.displayDeadline(): String {
        val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.UTC }
        val local = runCatching { Instant.parse(confirmationDeadline).toLocalDateTime(zone) }.getOrNull()
        return local?.let { "${it.hour.toString().padStart(2, '0')}h${it.minute.toString().padStart(2, '0')}" }
            ?: confirmationDeadline
    }

    private fun Game.deadlineIsOpen(): Boolean {
        return runCatching { Instant.parse(confirmationDeadline) > now.now() }
            .getOrDefault(true)
    }
}

private fun List<Game>.nextPublishedGame(now: Instant): Game? {
    val published = filter { it.status == GameStatus.Published }
    return published
        .mapNotNull { game -> runCatching { Instant.parse(game.startsAt) to game }.getOrNull() }
        .filter { it.first >= now }
        .minByOrNull { it.first }
        ?.second
}

private fun GroupDetailsState.from(group: Group): GroupDetailsState {
    val profile = group.profile
    return copy(
        isAdmin = group.role != GroupRole.ATHLETE,
        isOwner = group.role == GroupRole.OWNER,
        header = GroupHeaderUi(
            name = group.name,
            subtitle = listOfNotNull(
                profile?.composition?.label(),
                profile?.level?.label(),
            ).joinToString(" · ").ifBlank { group.timeZone.id },
            summaryChips = profile.toSummaryChips(),
        ),
        venue = profile?.defaultVenue?.let { VenueUi(it.name, it.address) },
    )
}

private fun GroupProfile?.toSummaryChips(): List<GroupSummaryChipUi> {
    if (this == null) return emptyList()
    return listOfNotNull(
        city?.takeIf(String::isNotBlank)?.let(::GroupSummaryChipUi),
        modality?.label()?.let(::GroupSummaryChipUi),
        regularSlots.map { it.weekday.label() }.distinct().takeIf { it.isNotEmpty() }
            ?.joinToString(" e ")
            ?.let { GroupSummaryChipUi(it, highlighted = true) },
    )
}

private fun br.com.saqz.groups.domain.group.GroupComposition.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupComposition.WOMEN -> "Feminino"
    br.com.saqz.groups.domain.group.GroupComposition.MEN -> "Masculino"
    br.com.saqz.groups.domain.group.GroupComposition.MIXED -> "Misto"
}

private fun br.com.saqz.groups.domain.group.GroupLevel.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupLevel.BEGINNER -> "Iniciante"
    br.com.saqz.groups.domain.group.GroupLevel.INTERMEDIATE -> "Intermediário"
    br.com.saqz.groups.domain.group.GroupLevel.ADVANCED -> "Avançado"
    br.com.saqz.groups.domain.group.GroupLevel.MIXED_LEVELS -> "Níveis mistos"
    br.com.saqz.groups.domain.group.GroupLevel.CUSTOM -> "Personalizado"
}

private fun br.com.saqz.groups.domain.group.GroupModality.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupModality.COURT_VOLLEYBALL -> "Vôlei de quadra"
    br.com.saqz.groups.domain.group.GroupModality.BEACH_VOLLEYBALL -> "Vôlei de areia"
    br.com.saqz.groups.domain.group.GroupModality.FOOTVOLLEY -> "Futevôlei"
}

private fun br.com.saqz.groups.domain.group.GroupWeekday.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupWeekday.MONDAY -> "Segunda"
    br.com.saqz.groups.domain.group.GroupWeekday.TUESDAY -> "Terça"
    br.com.saqz.groups.domain.group.GroupWeekday.WEDNESDAY -> "Quarta"
    br.com.saqz.groups.domain.group.GroupWeekday.THURSDAY -> "Quinta"
    br.com.saqz.groups.domain.group.GroupWeekday.FRIDAY -> "Sexta"
    br.com.saqz.groups.domain.group.GroupWeekday.SATURDAY -> "Sábado"
    br.com.saqz.groups.domain.group.GroupWeekday.SUNDAY -> "Domingo"
}

private fun AttendanceError.toUiError(): GroupUiError = when (this) {
    AttendanceError.HiddenResource -> GroupUiError.NotFound
    AttendanceError.Conflict -> GroupUiError.Conflict
    AttendanceError.Authentication -> GroupUiError.AccessDenied
    AttendanceError.DeadlinePassed,
    AttendanceError.Frozen,
    is AttendanceError.Validation,
    is AttendanceError.Data,
    -> GroupUiError.Network
}

private fun AthleteError.toUiError(): GroupUiError = when (this) {
    is AthleteError.Validation -> GroupUiError.Validation
    is AthleteError.DataFailure -> GroupUiError.Network
}
