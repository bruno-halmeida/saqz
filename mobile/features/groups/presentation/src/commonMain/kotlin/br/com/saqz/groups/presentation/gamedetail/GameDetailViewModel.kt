package br.com.saqz.groups.presentation.gamedetail
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.attendance.AddGuestCommand
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendancePromotionCommand
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.PromotionMode
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("LargeClass")
class GameDetailViewModel(
    val groupId: String,
    val gameId: String,
    private val gameGateway: GameGateway,
    private val groupGateway: GroupGateway,
    private val attendanceGateway: AttendanceGateway,
    private val athleteGateway: AthleteGateway,
) : MviViewModel<GameDetailState, GameDetailIntent, GameDetailEffect>(GameDetailState()) {
    private var loadGeneration = 0
    private var promotionGeneration = 0
    private var capacityGeneration = 0
    private var versionToken: GameVersionToken? = null
    private var selfId: String? = null
    init {
        load()
    }
    override fun onIntent(intent: GameDetailIntent) {
        when (intent) {
            GameDetailIntent.Retry -> load()
            GameDetailIntent.Edit -> emit(GameDetailEffect.OpenEditor)
            GameDetailIntent.OpenSettlement -> if (
                state.value.isAdmin && state.value.header?.statusTone == GameDetailStatusTone.Completed
            ) {
                emit(GameDetailEffect.OpenSettlement)
            }
            GameDetailIntent.RequestCancel -> if (state.value.header?.statusTone == GameDetailStatusTone.Published) {
                update { it.copy(cancelDialogOpen = true, cancelFailed = false) }
            }
            GameDetailIntent.DismissCancel -> if (!state.value.cancelling) {
                update { it.copy(cancelDialogOpen = false, cancelFailed = false) }
            }
            GameDetailIntent.ConfirmCancel -> cancel()
            is GameDetailIntent.Promote -> promote(intent.memberId, intent.reason, intent.guestSeq)
            GameDetailIntent.OpenCapacitySheet -> openCapacitySheet()
            is GameDetailIntent.UpdateCapacity -> update {
                it.copy(capacityDraft = intent.value.coerceIn(MIN_CAPACITY, MAX_CAPACITY), capacityFailed = false)
            }
            GameDetailIntent.SaveCapacity -> saveCapacity()
            GameDetailIntent.DismissCapacitySheet -> if (!state.value.savingCapacity) {
                update { it.copy(capacitySheetOpen = false, capacityFailed = false) }
            }
            GameDetailIntent.OpenGuestSheet -> if (state.value.guest.enabled) {
                updateGuest { it.copy(sheetOpen = true, name = "", addFailed = false) }
            }
            is GameDetailIntent.UpdateGuestName -> updateGuest {
                it.copy(name = intent.value.take(MAX_GUEST_NAME), addFailed = false)
            }
            GameDetailIntent.SubmitGuest -> submitGuest()
            GameDetailIntent.DismissGuestSheet -> if (!state.value.guest.adding) {
                updateGuest { it.copy(sheetOpen = false, addFailed = false) }
            }
            is GameDetailIntent.RequestRemoveGuest -> requestRemoveGuest(intent.rowId)
            GameDetailIntent.ConfirmRemoveGuest -> confirmRemoveGuest()
            GameDetailIntent.DismissRemoveGuest -> if (!state.value.guest.removing) {
                updateGuest { it.copy(removal = null, removeFailed = false) }
            }
            GameDetailIntent.DismissGuestNotice -> updateGuest { it.copy(noticeName = null) }
        }
    }
    private fun load() {
        val generation = ++loadGeneration
        promotionGeneration++
        capacityGeneration++
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
                            groupResult.value.group.role != GroupRole.ATHLETE,
                            groupResult.value.group.gameConfig.mensalistaPriority,
                            groupResult.value.group.gameConfig.promotionMode,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadAttendance(
        generation: Int,
        game: Game,
        isAdmin: Boolean,
        mensalistaPriority: Boolean,
        promotionMode: PromotionMode,
    ) {
        val detailResult = attendanceGateway.read(GroupId(groupId), gameId)
        if (generation != loadGeneration) return
        val rosterResult = attendanceGateway.roster(GroupId(groupId), gameId)
        if (generation != loadGeneration) return
        val athletesResult = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
        if (generation != loadGeneration) return
        when {
            detailResult is SaqzResult.Failure -> showFailure(generation, detailResult.error.toUiError())
            rosterResult is SaqzResult.Failure -> showFailure(generation, rosterResult.error.toUiError())
            athletesResult is SaqzResult.Failure -> showFailure(generation, athletesResult.error.toUiError())
            else -> {
                val detail = (detailResult as SaqzResult.Success).value
                val roster = (rosterResult as SaqzResult.Success).value
                val athletes = (athletesResult as SaqzResult.Success).value.associateBy(AthleteRosterEntry::userId)
                selfId = detail.ownAttendance?.memberId
                val open = game.toHeader().confirmationOpen
                val tone = game.toHeader().statusTone
                update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        error = null,
                        header = game.toHeader().copy(availableSpots = detail.availableSpots),
                        attendance = detail.toAttendance(),
                        confirmedRoster = roster.confirmed.map { member ->
                            member.toConfirmed(athletes[member.memberId], isAdmin, open, tone)
                        },
                        waitlist = roster.waitlisted.map { member ->
                            member.toWaitlist(athletes[member.memberId], isAdmin, open, tone)
                        },
                        guest = guestUi(game, detail, open, it.guest),
                        mensalistaPriority = mensalistaPriority,
                        promotionMode = promotionMode,
                        isAdmin = isAdmin,
                        promotingMemberId = null,
                        promotionFailed = false,
                        capacitySheetOpen = false,
                        savingCapacity = false,
                        capacityFailed = false,
                    )
                }
            }
        }
    }

    private fun promote(memberId: String, reason: String, guestSeq: Int) {
        val current = state.value
        if (
            !current.isAdmin ||
            current.header?.statusTone != GameDetailStatusTone.Published ||
            current.promotionMode != PromotionMode.MANUAL ||
            current.promotingMemberId != null
        ) return
        val rowId = if (guestSeq > 0) "$memberId#$guestSeq" else memberId
        val previousWaitlist = current.waitlist
        val previousAttendance = current.attendance
        if (previousWaitlist.none { it.id == rowId }) return
        val loadAtStart = loadGeneration
        val generation = ++promotionGeneration
        update {
            it.copy(
                waitlist = it.waitlist.filterNot { member -> member.id == rowId },
                attendance = it.attendance?.let { attendance ->
                    attendance.copy(
                        confirmed = attendance.confirmed + 1,
                        availableSpots = (attendance.availableSpots - 1).coerceAtLeast(0),
                    )
                },
                promotingMemberId = rowId,
                promotionFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.promote(
                GroupId(groupId),
                gameId,
                AttendancePromotionCommand(Uuid.random().toString(), memberId, reason, guestSeq),
            )
            if (!isCurrent(Operation.Promotion, generation, loadAtStart)) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val detail = result.value.value.detail.toAttendance()
                    update {
                        it.copy(
                            attendance = detail,
                            header = it.header?.copy(availableSpots = detail.availableSpots),
                            promotingMemberId = null,
                            promotionFailed = false,
                        )
                    }
                    refreshRoster(Operation.Promotion, generation, loadAtStart)
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        waitlist = previousWaitlist,
                        attendance = previousAttendance,
                        promotingMemberId = null,
                        promotionFailed = true,
                    )
                }
            }
        }
    }

    private fun openCapacitySheet() {
        if (!state.value.isAdmin || state.value.header?.statusTone != GameDetailStatusTone.Published) return
        val capacity = state.value.attendance?.capacity ?: return
        update { it.copy(capacitySheetOpen = true, capacityDraft = capacity, capacityFailed = false) }
    }

    private fun saveCapacity() {
        val current = state.value
        if (current.savingCapacity) return
        val version = versionToken ?: return
        val previousAttendance = current.attendance
        val previousHeader = current.header
        val loadAtStart = loadGeneration
        val generation = ++capacityGeneration
        val capacity = current.capacityDraft
        update {
            it.copy(
                savingCapacity = true,
                capacityFailed = false,
                attendance = it.attendance?.let { attendance ->
                    attendance.copy(
                        capacity = capacity,
                        availableSpots = (capacity - attendance.confirmed).coerceAtLeast(0),
                    )
                },
                header = it.header?.copy(
                    availableSpots = (capacity - (it.attendance?.confirmed ?: 0)).coerceAtLeast(0),
                ),
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.capacity(
                GroupId(groupId),
                gameId,
                AttendanceVersionToken(version.value),
                AttendanceCapacityCommand(Uuid.random().toString(), capacity),
            )
            if (!isCurrent(Operation.Capacity, generation, loadAtStart)) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    versionToken = GameVersionToken(result.value.version.value)
                    update {
                        it.copy(
                            savingCapacity = false,
                            capacitySheetOpen = false,
                            capacityFailed = false,
                            attendance = result.value.value.detail.toAttendance(),
                            header = it.header?.copy(availableSpots = result.value.value.detail.availableSpots),
                        )
                    }
                    if (result.value.value.promotedCount > 0) {
                        refreshRoster(Operation.Capacity, generation, loadAtStart)
                    }
                }
                is SaqzResult.Failure -> if (result.error == AttendanceError.Conflict) {
                    update { it.copy(savingCapacity = false, capacitySheetOpen = false, capacityFailed = false) }
                    load()
                } else {
                    update {
                        it.copy(
                            savingCapacity = false,
                            attendance = previousAttendance,
                            header = previousHeader,
                            capacityFailed = true,
                        )
                    }
                }
            }
        }
    }

    private fun refreshRoster(operation: Operation, generation: Int, loadAtStart: Int) {
        viewModelScope.launch {
            val rosterResult = attendanceGateway.roster(GroupId(groupId), gameId)
            val athletesResult = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
            if (!isCurrent(operation, generation, loadAtStart)) return@launch
            when {
                rosterResult is SaqzResult.Failure -> showFailure(loadAtStart, rosterResult.error.toUiError())
                athletesResult is SaqzResult.Failure -> showFailure(loadAtStart, athletesResult.error.toUiError())
                else -> {
                    val roster = (rosterResult as SaqzResult.Success).value
                    val athletes = (athletesResult as SaqzResult.Success).value.associateBy(AthleteRosterEntry::userId)
                    val isAdmin = state.value.isAdmin
                    val open = state.value.header?.confirmationOpen == true
                    val tone = state.value.header?.statusTone
                    update {
                        it.copy(
                            confirmedRoster = roster.confirmed.map { member ->
                                member.toConfirmed(athletes[member.memberId], isAdmin, open, tone)
                            },
                            waitlist = roster.waitlisted.map { member ->
                                member.toWaitlist(athletes[member.memberId], isAdmin, open, tone)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun isCurrent(operation: Operation, generation: Int, loadAtStart: Int): Boolean =
        generation == when (operation) {
            Operation.Promotion -> promotionGeneration
            Operation.Capacity -> capacityGeneration
        } && loadAtStart == loadGeneration

    private enum class Operation { Promotion, Capacity }
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
    private fun AttendanceDetail.toAttendance() = GameDetailAttendanceUi(
        confirmed = confirmedCount,
        capacity = capacity,
        availableSpots = availableSpots,
        declined = declinedCount,
        pending = pendingCount,
    )

    private fun AttendanceRosterMember.toConfirmed(
        athlete: AthleteRosterEntry?,
        isAdmin: Boolean,
        open: Boolean,
        tone: GameDetailStatusTone?,
    ) = GameDetailConfirmedUi(
        id = rowKey,
        name = displayName,
        isYou = !isGuest && memberId == selfId,
        position = if (isGuest) "" else athlete?.position?.name.orEmpty(),
        guest = guestRow(isAdmin, open, tone),
    )

    private fun AttendanceRosterMember.toWaitlist(
        athlete: AthleteRosterEntry?,
        isAdmin: Boolean,
        open: Boolean,
        tone: GameDetailStatusTone?,
    ) = GameDetailWaitlistUi(
        id = rowKey,
        name = displayName,
        queuePosition = waitlistPosition,
        athletePosition = if (isGuest) null else athlete?.position,
        isMensalista = !isGuest && athlete?.membershipType == AthleteMembershipType.MENSALISTA,
        guest = guestRow(isAdmin, open, tone),
    )

    private fun AttendanceRosterMember.guestRow(
        isAdmin: Boolean,
        open: Boolean,
        tone: GameDetailStatusTone?,
    ): GameGuestRowUi? {
        if (!isGuest) return null
        val yours = memberId == selfId
        val published = tone != GameDetailStatusTone.Cancelled && tone != GameDetailStatusTone.Completed
        return GameGuestRowUi(
            hostId = memberId,
            guestSeq = guestSeq,
            hostName = hostDisplayName.orEmpty(),
            isYours = yours,
            canRemove = (yours && open) || (isAdmin && published),
        )
    }

    /** Regras A e C: só quem já disse "Vou" (confirmado ou na fila), e só com confirmações abertas. */
    private fun guestUi(game: Game, detail: AttendanceDetail, open: Boolean, previous: GameGuestUi): GameGuestUi {
        val going = detail.ownAttendance?.status == AttendanceStatus.Confirmed ||
            detail.ownAttendance?.status == AttendanceStatus.Waitlisted
        return previous.copy(
            visible = game.status == GameStatus.Published,
            enabled = open && going,
            hint = when {
                !open -> GameGuestHint.Closed
                !going -> GameGuestHint.NeedAnswer
                else -> GameGuestHint.Default
            },
            feeLabel = game.gameFeeCents?.let(::formatBrl),
            sheetOpen = false,
            adding = false,
            removal = null,
            removing = false,
        )
    }

    private fun updateGuest(block: (GameGuestUi) -> GameGuestUi) = update { it.copy(guest = block(it.guest)) }

    private fun submitGuest() {
        val guest = state.value.guest
        if (!guest.enabled || !guest.canSubmit) return
        val name = guest.name.trim()
        val loadAtStart = loadGeneration
        updateGuest { it.copy(adding = true, addFailed = false) }
        viewModelScope.launch {
            val result = attendanceGateway.addGuest(
                GroupId(groupId),
                gameId,
                AddGuestCommand(Uuid.random().toString(), name),
            )
            if (loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    updateGuest {
                        it.copy(sheetOpen = false, adding = false, name = "", noticeName = name, noticeJoined = true)
                    }
                    load()
                }
                is SaqzResult.Failure -> updateGuest { it.copy(adding = false, addFailed = true) }
            }
        }
    }

    private fun requestRemoveGuest(rowId: String) {
        val confirmed = state.value.confirmedRoster.firstOrNull { it.id == rowId }
        val waiting = state.value.waitlist.firstOrNull { it.id == rowId }
        val row = confirmed?.guest ?: waiting?.guest ?: return
        if (!row.canRemove) return
        updateGuest {
            it.copy(
                removal = GameGuestRemovalUi(
                    rowId,
                    row.hostId,
                    row.guestSeq,
                    confirmed?.name ?: waiting?.name.orEmpty(),
                    confirmed != null,
                ),
                removeFailed = false,
            )
        }
    }

    private fun confirmRemoveGuest() {
        val removal = state.value.guest.removal ?: return
        if (state.value.guest.removing) return
        val loadAtStart = loadGeneration
        updateGuest { it.copy(removing = true, removeFailed = false) }
        viewModelScope.launch {
            val result = attendanceGateway.removeGuest(GroupId(groupId), gameId, removal.hostId, removal.guestSeq)
            if (loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    updateGuest {
                        it.copy(removal = null, removing = false, noticeName = removal.name, noticeJoined = false)
                    }
                    load()
                }
                is SaqzResult.Failure -> updateGuest { it.copy(removing = false, removeFailed = true) }
            }
        }
    }

    private fun Int.pad(): String = if (this < 10) "0$this" else toString()
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

private const val MIN_CAPACITY = 2
private const val MAX_CAPACITY = 100
private const val MAX_GUEST_NAME = 80
private fun GameStatus.toTone(): GameDetailStatusTone = when (this) {
    GameStatus.Draft -> GameDetailStatusTone.Draft
    GameStatus.Published -> GameDetailStatusTone.Published
    GameStatus.Cancelled -> GameDetailStatusTone.Cancelled
    GameStatus.Completed -> GameDetailStatusTone.Completed
}
private fun formatDatePtBr(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"
