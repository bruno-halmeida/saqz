package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GroupCashboxViewModel(
    private val groupId: String,
    private val groupGateway: GroupGateway,
    private val membershipGateway: GroupMembershipGateway,
    private val statementGateway: FinanceStatementGateway,
    private val organizerFinanceGateway: OrganizerFinanceGateway,
    private val now: GroupNowPort,
) : MviViewModel<GroupCashboxState, GroupCashboxIntent, GroupCashboxEffect>(GroupCashboxState()) {

    private var loadGeneration = 0L
    private var receiptGeneration = 0L

    init {
        load()
    }

    override fun onIntent(intent: GroupCashboxIntent) {
        when (intent) {
            GroupCashboxIntent.Retry -> load()
            GroupCashboxIntent.ChargeMissing -> openChargeSheet()
            is GroupCashboxIntent.ChargeIndividual -> openChargeSheet(intent.chargeId)
            GroupCashboxIntent.Register -> emit(GroupCashboxEffect.OpenNewEntry(groupId))
            GroupCashboxIntent.ViewFullStatement -> emit(GroupCashboxEffect.OpenStatement(groupId))
            is GroupCashboxIntent.OpenReceipt -> openReceiptSheet(intent.chargeId)
            GroupCashboxIntent.DismissChargeSheet -> update {
                it.copy(chargeSheetOpen = false, chargeSheetChargeId = null)
            }
            GroupCashboxIntent.DismissReceiptSheet -> update { it.copy(receiptSheetChargeId = null) }
            GroupCashboxIntent.CopyPix -> state.value.pix?.let {
                emit(GroupCashboxEffect.CopyPix(it.key))
            }
            is GroupCashboxIntent.MarkReceived -> markReceived(intent.chargeId, intent.paidMethod)
        }
    }

    private fun openChargeSheet(chargeId: String? = null) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.pix?.key.isNullOrBlank() ||
            current.debtors.isEmpty() ||
            (chargeId != null && current.debtors.none { it.chargeId == chargeId })
        ) return
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
        if (current.isLoading || current.updatingChargeId != null ||
            current.debtors.none { it.chargeId == chargeId }
        ) return
        update { it.copy(receiptSheetChargeId = chargeId, chargeSheetOpen = false, chargeSheetChargeId = null) }
    }

    private fun load() {
        val generation = ++loadGeneration
        receiptGeneration++
        update { it.copy(isLoading = true, loadFailed = false, operationFailed = false) }
        viewModelScope.launch {
            val groupResult = groupGateway.read(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            val membershipResult = membershipGateway.listMemberships(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            val group = when (groupResult) {
                is SaqzResult.Failure -> return@launch failLoad(generation)
                is SaqzResult.Success -> groupResult.value.group
            }
            val memberships = when (membershipResult) {
                is SaqzResult.Failure -> return@launch failLoad(generation)
                is SaqzResult.Success -> membershipResult.value
            }
            val date = currentDate(group.timeZone.id)
            val monthKey = date.monthKey()
            val statementResult = statementGateway.statement(
                GroupId(groupId),
                FinanceStatementQuery(month = monthKey),
            )
            if (generation != loadGeneration) return@launch
            val chargesResult = organizerFinanceGateway.charges(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            val statement = when (statementResult) {
                is SaqzResult.Failure -> return@launch failLoad(generation)
                is SaqzResult.Success -> statementResult.value
            }
            val charges = when (chargesResult) {
                is SaqzResult.Failure -> return@launch failLoad(generation)
                is SaqzResult.Success -> chargesResult.value.charges
            }
            updateIfCurrent(generation) {
                toState(
                    groupName = group.name,
                    monthKey = monthKey,
                    date = date,
                    memberships = memberships,
                    charges = charges,
                    statement = statement,
                    pixKey = group.profile?.pixKey,
                    pixLabel = group.profile?.pixLabel,
                )
            }
        }
    }

    private fun markReceived(chargeId: String, paidMethod: PaidMethod) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null) return
        val debtor = current.debtors.firstOrNull { it.chargeId == chargeId } ?: return
        val generation = ++receiptGeneration
        val loadAtStart = loadGeneration
        val previous = current
        update { current.optimisticallyReceive(debtor) }
        viewModelScope.launch {
            val result = organizerFinanceGateway.updateChargeStatus(
                groupId = GroupId(groupId),
                chargeId = debtor.chargeId,
                version = FinanceVersionToken("\"${debtor.chargeVersion}\""),
                command = ChargeStatusCommand(ChargeStatus.Paid, paidMethod = paidMethod),
            )
            if (generation != receiptGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    update { it.copy(updatingChargeId = null, operationFailed = false) }
                    emit(GroupCashboxEffect.MutationSucceeded)
                }
                is SaqzResult.Failure -> update {
                    previous.copy(operationFailed = true, updatingChargeId = null, receiptSheetChargeId = null)
                }
            }
        }
    }

    private fun failLoad(generation: Long) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true) }
    }

    private fun updateIfCurrent(generation: Long, transform: (GroupCashboxState) -> GroupCashboxState) {
        if (generation != loadGeneration) return
        update { current -> transform(current).copy(isLoading = false, loadFailed = false) }
    }

    @Suppress("LongParameterList")
    private fun toState(
        groupName: String,
        monthKey: String,
        date: LocalDate,
        memberships: List<GroupMembership>,
        charges: List<Charge>,
        statement: FinanceStatementPage,
        pixKey: String?,
        pixLabel: String?,
    ): GroupCashboxState {
        val monthly = charges.filter {
            it.kind == ChargeKind.Monthly && it.month == monthKey && it.status != ChargeStatus.Cancelled
        }
        val paidMonthly = monthly.filter { it.status == ChargeStatus.Paid }
        val openMonthly = monthly.filter { it.status == ChargeStatus.Pending }
        val pending = charges.filter { it.status == ChargeStatus.Pending }
        val memberNames = memberships.associate { it.userId to it.displayName }
        val debtors = pending
            .map { it.toDebtor(memberNames[it.memberId] ?: "Membro", it.dueDate < date.toString()) }
            .sortedBy { it.dueLabel }
        val balanceCents = statement.summary.accumulatedBalanceCents
        val receivedCents = paidMonthly.sumOf { it.amountCents }
        val expensesCents = statement.summary.totalOutCents
        val hasHistory = statement.summary.totalInCents != 0L || expensesCents != 0L ||
            charges.any { it.status != ChargeStatus.Cancelled }
        val cashboxEmpty = !hasHistory && pending.isEmpty() && balanceCents == 0L
        return GroupCashboxState(
            isLoading = false,
            groupName = groupName,
            monthKey = monthKey,
            monthLabel = monthNameFromKey(monthKey),
            monthlyMembersLabel = "${monthly.size} mensalistas",
            balanceLabel = formatBrl(balanceCents),
            receivedLabel = formatBrl(receivedCents),
            openLabel = formatBrl(openMonthly.sumOf { it.amountCents }),
            expensesLabel = formatBrl(expensesCents),
            monthlyProgressLabel = "${paidMonthly.size}/${monthly.size}",
            monthlyProgress = if (monthly.isEmpty()) 0f else paidMonthly.size.toFloat() / monthly.size,
            balanceCents = balanceCents,
            receivedCents = receivedCents,
            openCents = openMonthly.sumOf { it.amountCents },
            expensesCents = expensesCents,
            paidMonthlyCount = paidMonthly.size,
            openMonthlyCount = openMonthly.size,
            monthlyTotalCount = monthly.size,
            cashboxEmpty = cashboxEmpty,
            overdueBanner = buildOverdueBanner(debtors),
            debtors = debtors,
            pix = pixKey?.trim()?.takeIf(String::isNotEmpty)?.let { PixUi(it, pixLabel) },
        )
    }

    private fun Charge.toDebtor(name: String, isOverdue: Boolean) = DebtorUi(
        chargeId = id,
        memberId = memberId,
        name = name,
        dueLabel = "${if (isOverdue) "Venceu em" else "Vence em"} ${formatDate(dueDate)}",
        amountLabel = formatBrl(amountCents),
        amountCents = amountCents,
        chargeVersion = version,
        month = month,
        isOverdue = isOverdue,
        referenceLabel = when (kind) {
            ChargeKind.Game -> "Diarista · jogo de ${formatDate(dueDate)}"
            ChargeKind.Monthly -> "Mensalista · ${month?.let(::monthNameOnlyFromKey) ?: "mês"}"
        },
    )

    private fun GroupCashboxState.optimisticallyReceive(debtor: DebtorUi): GroupCashboxState {
        val isMonthly = debtor.month == monthKey
        val nextPaid = paidMonthlyCount + if (isMonthly) 1 else 0
        val nextOpenCount = openMonthlyCount - if (isMonthly) 1 else 0
        val nextTotal = monthlyTotalCount
        val nextReceived = receivedCents + if (isMonthly) debtor.amountCents else 0L
        val nextOpen = (openCents - if (isMonthly) debtor.amountCents else 0L).coerceAtLeast(0L)
        val remainingDebtors = debtors.filterNot { it.chargeId == debtor.chargeId }
        return copy(
            balanceCents = balanceCents + debtor.amountCents,
            balanceLabel = formatBrl(balanceCents + debtor.amountCents),
            receivedCents = nextReceived,
            receivedLabel = formatBrl(nextReceived),
            openCents = nextOpen,
            openLabel = formatBrl(nextOpen),
            paidMonthlyCount = nextPaid,
            openMonthlyCount = nextOpenCount,
            monthlyProgressLabel = "$nextPaid/$nextTotal",
            monthlyProgress = if (nextTotal == 0) 0f else nextPaid.toFloat() / nextTotal,
            debtors = remainingDebtors,
            overdueBanner = buildOverdueBanner(remainingDebtors),
            updatingChargeId = debtor.chargeId,
            cashboxEmpty = false,
            operationFailed = false,
            receiptSheetChargeId = null,
        )
    }

    private fun currentDate(timeZoneId: String): LocalDate {
        val instant = now.now()
        return runCatching { instant.toLocalDateTime(TimeZone.of(timeZoneId)).date }
            .getOrElse { instant.toLocalDateTime(TimeZone.UTC).date }
    }

    private fun LocalDate.monthKey() = "$year-${month.ordinal.plus(1).toString().padStart(2, '0')}"

    private fun formatDate(value: String): String = runCatching {
        val date = LocalDate.parse(value)
        "${date.day.toString().padStart(2, '0')}/${date.month.ordinal.plus(1).toString().padStart(2, '0')}"
    }.getOrDefault(value)

    private fun monthNameFromKey(key: String): String {
        val month = key.substringAfter('-', "01").toIntOrNull()?.coerceIn(1, 12) ?: 1
        val year = key.substringBefore('-', "").toIntOrNull()
        val name = MONTH_NAMES[month - 1]
        return if (year == null) name else "$name de $year"
    }

    private fun monthNameOnlyFromKey(key: String): String {
        val month = key.substringAfter('-', "01").toIntOrNull()?.coerceIn(1, 12) ?: 1
        return MONTH_NAMES[month - 1]
    }

    private fun buildOverdueBanner(debtors: List<DebtorUi>): OverdueBannerUi? {
        val overdue = debtors.filter { it.isOverdue }
        val names = overdue.map { it.name }.distinct().sorted()
        val overdueMonthKeys = overdue.mapNotNull { it.month }.distinct()
        val bannerMonthKey = overdueMonthKeys.singleOrNull()
            ?.takeUnless { overdue.any { it.month == null } }
        val month = bannerMonthKey?.let(::monthNameOnlyFromKey)
        return names.takeIf { it.isNotEmpty() }?.let {
            val verb = if (it.size == 1) "está" else "estão"
            OverdueBannerUi(
                message = it.joinNames() + " $verb com " +
                    (month?.let { value -> "$value em aberto" } ?: "cobranças em aberto"),
                monthLabel = bannerMonthKey?.let(::monthNameFromKey),
            )
        }
    }

    private fun List<String>.joinNames(): String = when (size) {
        0 -> ""
        1 -> single()
        2 -> "${this[0]} e ${this[1]}"
        else -> dropLast(1).joinToString(", ") + " e " + last()
    }

    private companion object {
        val MONTH_NAMES = listOf(
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
        )
    }
}
