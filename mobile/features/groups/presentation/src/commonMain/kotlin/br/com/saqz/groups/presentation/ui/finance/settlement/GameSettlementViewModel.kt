package br.com.saqz.groups.presentation.ui.finance.settlement

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class GameSettlementViewModel(
    private val groupId: String,
    private val gameId: String,
    private val gameGateway: GameGateway,
    private val groupGateway: GroupGateway,
    private val attendanceGateway: AttendanceGateway,
    private val athleteGateway: AthleteGateway,
    private val organizerFinanceGateway: OrganizerFinanceGateway,
) : MviViewModel<GameSettlementState, GameSettlementIntent, GameSettlementEffect>(GameSettlementState()) {

    private var loadGeneration = 0L
    private var mutationGeneration = 0L

    init {
        load()
    }

    override fun onIntent(intent: GameSettlementIntent) {
        when (intent) {
            GameSettlementIntent.Retry -> load()
            GameSettlementIntent.ChargeMissing -> openChargeSheet()
            is GameSettlementIntent.ChargeIndividual -> openChargeSheet(intent.chargeId)
            is GameSettlementIntent.OpenReceipt -> openReceiptSheet(intent.chargeId)
            is GameSettlementIntent.MarkReceived -> markReceived(intent.chargeId, intent.paidMethod)
            GameSettlementIntent.DismissChargeSheet -> update {
                it.copy(chargeSheetOpen = false, chargeSheetChargeId = null)
            }
            GameSettlementIntent.DismissReceiptSheet -> update { it.copy(receiptSheetChargeId = null) }
            GameSettlementIntent.CopyPix -> state.value.pix?.let { pix ->
                emit(GameSettlementEffect.CopyPix(pix.key))
            }
            GameSettlementIntent.OpenCourtExpense -> if (!state.value.isLoading) {
                emit(GameSettlementEffect.OpenNewEntry(groupId))
            }
            GameSettlementIntent.EndSettlement -> if (state.value.isSummary) load()
            GameSettlementIntent.OpenCashbox -> emit(GameSettlementEffect.OpenCashbox(groupId))
        }
    }

    private fun openChargeSheet(chargeId: String? = null) {
        val current = state.value
        val targetExists = chargeId == null || current.debtors.any { it.chargeId == chargeId }
        val canOpen = !current.isLoading && current.updatingChargeId == null &&
            !current.pix?.key.isNullOrBlank() && current.debtors.isNotEmpty() && targetExists
        if (!canOpen) return
        update {
            it.copy(
                chargeSheetOpen = true,
                chargeSheetChargeId = chargeId,
                receiptSheetChargeId = null,
            )
        }
    }

    private fun openReceiptSheet(chargeId: String) {
        val current = state.value
        if (
            current.isLoading || current.updatingChargeId != null ||
            current.debtors.none { it.chargeId == chargeId }
        ) return
        update {
            it.copy(
                receiptSheetChargeId = chargeId,
                chargeSheetOpen = false,
                chargeSheetChargeId = null,
            )
        }
    }

    private suspend fun <T, E : SaqzError> requestOrFail(
        generation: Long,
        request: suspend () -> SaqzResult<T, E>,
        mapError: (E) -> GroupUiError,
    ): T? {
        val result = request()
        if (generation != loadGeneration) return null
        return when (result) {
            is SaqzResult.Failure -> {
                failLoad(generation, mapError(result.error))
                null
            }
            is SaqzResult.Success -> result.value
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        mutationGeneration++
        update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                error = null,
                operationFailed = false,
            )
        }
        viewModelScope.launch {
            val game = requestOrFail(generation, { gameGateway.read(GroupId(groupId), gameId) }) {
                it.toSettlementUiError()
            }?.game ?: return@launch
            val group = requestOrFail(generation, { groupGateway.read(GroupId(groupId)) }) {
                it.toSettlementUiError()
            }?.group ?: return@launch
            if (group.role == GroupRole.ATHLETE) {
                return@launch failLoad(generation, GroupUiError.AccessDenied)
            }
            val roster = requestOrFail(generation, { attendanceGateway.roster(GroupId(groupId), gameId) }) {
                it.toSettlementUiError()
            } ?: return@launch
            val athletes = requestOrFail(generation, { athleteGateway.roster(GroupId(groupId), AthleteRosterFilter()) }) {
                it.toSettlementUiError()
            } ?: return@launch
            val charges = requestOrFail(generation, { organizerFinanceGateway.charges(GroupId(groupId)) }) {
                it.toSettlementUiError()
            }?.charges ?: return@launch
            val expenses = requestOrFail(generation, { organizerFinanceGateway.expenses(GroupId(groupId)) }) {
                it.toSettlementUiError()
            }?.expenses ?: return@launch
            updateIfCurrent(generation) {
                toState(
                    game = game,
                    groupName = group.name,
                    groupTimeZone = group.timeZone.id,
                    roster = roster,
                    athletes = athletes,
                    charges = charges,
                    expenses = expenses,
                    pix = group.profile?.let { profile ->
                        profile.pixKey?.trim()?.takeIf(String::isNotEmpty)?.let { key ->
                            PixUi(key, profile.pixLabel)
                        }
                    },
                )
            }
        }
    }

    private fun markReceived(chargeId: String, paidMethod: PaidMethod) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null) return
        val diarist = current.diarists.firstOrNull {
            it.chargeId == chargeId && it.status == ChargeStatus.Pending
        } ?: return
        val debtor = current.debtors.firstOrNull { it.chargeId == chargeId } ?: return
        val generation = ++mutationGeneration
        val loadAtStart = loadGeneration
        val previous = current
        update { it.optimisticallyReceive(diarist) }
        viewModelScope.launch {
            val result = organizerFinanceGateway.updateChargeStatus(
                groupId = GroupId(groupId),
                chargeId = debtor.chargeId,
                version = FinanceVersionToken("\"${debtor.chargeVersion}\""),
                command = ChargeStatusCommand(ChargeStatus.Paid, paidMethod = paidMethod),
            )
            if (generation != mutationGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> load()
                is SaqzResult.Failure -> update {
                    previous.copy(operationFailed = true, updatingChargeId = null)
                }
            }
        }
    }

    private fun GameSettlementState.optimisticallyReceive(
        diarist: GameSettlementDiaristUi,
    ): GameSettlementState {
        val received = receivedDiaristCents + diarist.amountCents
        val pending = (pendingDiaristCents - diarist.amountCents).coerceAtLeast(0L)
        val paidCount = paidDiaristCount + 1
        val pendingCount = (pendingDiaristCount - 1).coerceAtLeast(0)
        return copy(
            diarists = diarists.map {
                if (it.chargeId == diarist.chargeId) {
                    it.copy(status = ChargeStatus.Paid, isUpdating = true)
                } else {
                    it
                }
            },
            debtors = debtors.filterNot { it.chargeId == diarist.chargeId },
            paidDiaristCount = paidCount,
            pendingDiaristCount = pendingCount,
            progress = progressFor(paidCount, totalDiaristCount),
            receivedDiaristCents = received,
            pendingDiaristCents = pending,
            resultCents = received - costCents,
            updatingChargeId = diarist.chargeId,
            receiptSheetChargeId = null,
            operationFailed = false,
        )
    }

    @Suppress("LongParameterList")
    private fun toState(
        game: br.com.saqz.groups.domain.game.Game,
        groupName: String,
        groupTimeZone: String,
        roster: AttendanceRoster,
        athletes: List<AthleteRosterEntry>,
        charges: List<Charge>,
        expenses: List<br.com.saqz.groups.domain.finance.Expense>,
        pix: PixUi?,
    ): GameSettlementState {
        val athleteById = athletes.associateBy(AthleteRosterEntry::userId)
        val names = roster.confirmed.associate { it.memberId to it.displayName }
        val gameCharges = charges
            .filter { it.kind == ChargeKind.Game && it.gameId == game.id && it.status != ChargeStatus.Cancelled }
            .map { it.toDiarist(names[it.memberId] ?: athleteById[it.memberId]?.displayName ?: "Membro") }
        val paid = gameCharges.filter { it.status == ChargeStatus.Paid }
        val pending = gameCharges.filter { it.status == ChargeStatus.Pending }
        val costCents = expenses
            .filter {
                it.status == ExpenseStatus.Active &&
                    it.direction == br.com.saqz.groups.domain.finance.FinanceDirection.Out &&
                    it.category == ExpenseCategory.Venue && it.expenseDate == game.localDate
            }
            .sumOf { it.amountCents }
        val localDateTime = runCatching {
            Instant.parse(game.startsAt).toLocalDateTime(TimeZone.of(game.zoneId))
        }.getOrNull()
        val fallbackDateTime = runCatching {
            Instant.parse(game.startsAt).toLocalDateTime(TimeZone.of(groupTimeZone))
        }.getOrNull()
        val dateTime = (localDateTime ?: fallbackDateTime)?.let {
            "${it.day.toString().padStart(2, '0')}/${it.month.ordinal.plus(1).toString().padStart(2, '0')}/${it.year} · " +
                "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
        } ?: game.localDate
        val monthlyMemberCount = roster.confirmed.count {
            athleteById[it.memberId]?.membershipType == AthleteMembershipType.MENSALISTA
        }
        val totalCents = gameCharges.sumOf { it.amountCents }
        val receivedCents = paid.sumOf { it.amountCents }
        val pendingCents = pending.sumOf { it.amountCents }
        return GameSettlementState(
            isLoading = false,
            groupId = groupId,
            groupName = groupName,
            header = GameSettlementHeaderUi(
                dateTime = dateTime,
                venue = listOfNotNull(game.venue.name, game.venue.court).joinToString(" — "),
                playersCount = roster.confirmed.size,
            ),
            monthlyMemberCount = monthlyMemberCount,
            paidDiaristCount = paid.size,
            totalDiaristCount = gameCharges.size,
            pendingDiaristCount = pending.size,
            progress = progressFor(paid.size, gameCharges.size),
            totalDiaristCents = totalCents,
            unitDiaristCents = gameCharges.firstOrNull()?.amountCents ?: 0L,
            receivedDiaristCents = receivedCents,
            pendingDiaristCents = pendingCents,
            costCents = costCents,
            resultCents = receivedCents - costCents,
            diarists = gameCharges,
            debtors = pending.map { it.toDebtor() },
            pix = pix,
        )
    }

    private fun Charge.toDiarist(name: String) = GameSettlementDiaristUi(
        chargeId = id,
        memberId = memberId,
        name = name,
        meta = "Diarista",
        amountLabel = formatBrl(amountCents),
        amountCents = amountCents,
        dueDate = dueDate,
        chargeVersion = version,
        status = status,
        referenceLabel = "Diarista · jogo de ${formatDate(dueDate)}",
    )

    private fun GameSettlementDiaristUi.toDebtor() = DebtorUi(
        chargeId = chargeId,
        memberId = memberId,
        name = name,
        dueLabel = "Vence em ${formatDate(dueDate)}",
        amountLabel = amountLabel,
        amountCents = amountCents,
        chargeVersion = chargeVersion,
        month = null,
        referenceLabel = referenceLabel,
    )

    private fun failLoad(generation: Long, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun updateIfCurrent(
        generation: Long,
        transform: (GameSettlementState) -> GameSettlementState,
    ) {
        if (generation != loadGeneration) return
        update { current -> transform(current).copy(isLoading = false, loadFailed = false) }
    }

    private fun formatDate(value: String): String = runCatching {
        val date = kotlinx.datetime.LocalDate.parse(value)
        "${date.day.toString().padStart(2, '0')}/${date.month.ordinal.plus(1).toString().padStart(2, '0')}"
    }.getOrDefault(value)

    private companion object {
        fun progressFor(paid: Int, total: Int): Float =
            if (total == 0) 0f else (paid.toFloat() / total).coerceIn(0f, 1f)
    }
}

private fun GameError.toSettlementUiError(): GroupUiError = when (this) {
    is GameError.Validation -> GroupUiError.Validation
    GameError.HiddenResource -> GroupUiError.NotFound
    is GameError.Conflict -> GroupUiError.Conflict
    GameError.VersionConflict -> GroupUiError.Conflict
    GameError.InvalidLifecycle -> GroupUiError.Validation
    GameError.Authentication -> GroupUiError.AccessDenied
    is GameError.Data -> GroupUiError.Network
}

private fun GroupProfileError.toSettlementUiError(): GroupUiError = when (this) {
    is GroupProfileError.Validation -> GroupUiError.Validation
    is GroupProfileError.Conflict -> GroupUiError.Conflict
    is GroupProfileError.DataFailure -> GroupUiError.Network
}

private fun AttendanceError.toSettlementUiError(): GroupUiError = when (this) {
    is AttendanceError.Validation -> GroupUiError.Validation
    AttendanceError.HiddenResource -> GroupUiError.NotFound
    AttendanceError.DeadlinePassed,
    AttendanceError.Frozen,
    AttendanceError.Conflict,
    AttendanceError.Authentication,
    is AttendanceError.Data,
    -> GroupUiError.Network
}

private fun AthleteError.toSettlementUiError(): GroupUiError = when (this) {
    is AthleteError.Validation -> GroupUiError.Validation
    is AthleteError.DataFailure -> GroupUiError.Network
}

private fun FinanceError.toSettlementUiError(): GroupUiError = when (this) {
    is FinanceError.Validation -> GroupUiError.Validation
    FinanceError.HiddenResource -> GroupUiError.NotFound
    FinanceError.Forbidden,
    FinanceError.Authentication,
    -> GroupUiError.AccessDenied
    FinanceError.Conflict -> GroupUiError.Conflict
    FinanceError.PreconditionRequired,
    FinanceError.InvalidLifecycle,
    is FinanceError.Data,
    -> GroupUiError.Network
}
