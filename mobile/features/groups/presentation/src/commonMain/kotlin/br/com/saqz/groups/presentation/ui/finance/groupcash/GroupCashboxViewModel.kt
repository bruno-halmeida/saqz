package br.com.saqz.groups.presentation.ui.finance.groupcash

import br.com.saqz.groups.presentation.photo.memberPhoto
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.FinanceError
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

@Suppress("LargeClass")
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
    private var loadInFlight = false

    init {
        load()
    }

    @Suppress("CyclomaticComplexMethod")
    override fun handleIntent(intent: GroupCashboxIntent) {
        when (intent) {
            GroupCashboxIntent.Retry -> load()
            GroupCashboxIntent.Refresh -> refresh()
            GroupCashboxIntent.ChargeMissing -> openChargeSheet()
            is GroupCashboxIntent.ChargeIndividual -> openChargeSheet(intent.chargeId)
            GroupCashboxIntent.Register -> emit(GroupCashboxEffect.OpenNewEntry(groupId))
            GroupCashboxIntent.GenerateMonthly -> if (!state.value.isLoading && !state.value.loadFailed) {
                emit(GroupCashboxEffect.OpenMonthlyGeneration(groupId))
            }
            GroupCashboxIntent.ViewFullStatement -> emit(GroupCashboxEffect.OpenStatement(groupId))
            is GroupCashboxIntent.OpenReceipt -> openReceiptSheet(intent.chargeId)
            is GroupCashboxIntent.OpenWaive -> openWaiveSheet(intent.chargeId)
            is GroupCashboxIntent.Waive -> waive(intent.chargeId)
            GroupCashboxIntent.DismissWaiveSheet -> update { it.copy(waiveSheetChargeId = null) }
            is GroupCashboxIntent.OpenCancel -> openCancelSheet(intent.chargeId)
            is GroupCashboxIntent.Cancel -> cancel(intent.chargeId)
            GroupCashboxIntent.DismissCancelSheet -> update { it.copy(cancelSheetChargeId = null) }
            is GroupCashboxIntent.OpenReview -> openReviewSheet(intent.chargeId)
            is GroupCashboxIntent.ResolveReview -> resolveReview(intent.chargeId)
            GroupCashboxIntent.DismissReviewSheet -> update { it.copy(reviewSheetChargeId = null) }
            is GroupCashboxIntent.OpenUndo -> openUndoSheet(intent.chargeId)
            is GroupCashboxIntent.Undo -> undo(intent.chargeId, intent.note)
            GroupCashboxIntent.DismissUndoSheet -> update { it.copy(undoSheetChargeId = null) }
            GroupCashboxIntent.DismissChargeSheet -> update {
                it.copy(chargeSheetOpen = false, chargeSheetChargeId = null)
            }
            GroupCashboxIntent.DismissReceiptSheet -> update { it.copy(receiptSheetChargeId = null) }
            GroupCashboxIntent.OpenPixSetup -> emit(GroupCashboxEffect.OpenGroupSettings(groupId))
            GroupCashboxIntent.CopyPix -> state.value.pix?.let {
                emit(GroupCashboxEffect.CopyPix(it.key))
            }
            is GroupCashboxIntent.MarkReceived -> markReceived(intent.chargeId, intent.paidMethod)
        }
    }

    private fun openChargeSheet(chargeId: String? = null) {
        val current = state.value
        val busy = current.isLoading || current.updatingChargeId != null
        val unknownCharge = chargeId != null && current.debtors.none { it.chargeId == chargeId }
        if (busy ||
            current.debtors.isEmpty() ||
            unknownCharge
        ) return
        update {
            it.copy(
                chargeSheetOpen = true,
                chargeSheetChargeId = chargeId,
                receiptSheetChargeId = null,
                waiveSheetChargeId = null,
                cancelSheetChargeId = null,
            )
        }
    }

    private fun openReceiptSheet(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.debtors.none { it.chargeId == chargeId }
        ) return
        update {
            it.copy(
                receiptSheetChargeId = chargeId,
                chargeSheetOpen = false,
                chargeSheetChargeId = null,
                waiveSheetChargeId = null,
                cancelSheetChargeId = null,
            )
        }
    }

    /** Só quem desistiu tem "Dispensar": cobrança comum segue o caminho do "Recebi". */
    private fun openWaiveSheet(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.debtors.none { it.chargeId == chargeId && it.withdrew }
        ) return
        update {
            it.copy(
                waiveSheetChargeId = chargeId,
                receiptSheetChargeId = null,
                chargeSheetOpen = false,
                chargeSheetChargeId = null,
            )
        }
    }

    private fun openCancelSheet(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.debtors.none { it.chargeId == chargeId }
        ) return
        update {
            it.copy(
                cancelSheetChargeId = chargeId,
                receiptSheetChargeId = null,
                waiveSheetChargeId = null,
                reviewSheetChargeId = null,
                undoSheetChargeId = null,
                chargeSheetOpen = false,
                chargeSheetChargeId = null,
            )
        }
    }

    /** Trava de toque duplo: com uma operação em curso (`updatingChargeId`) o segundo toque é ignorado. */
    private fun cancel(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null) return
        val debtor = current.debtors.firstOrNull { it.chargeId == chargeId } ?: return
        val generation = ++receiptGeneration
        val loadAtStart = loadGeneration
        val previous = current
        update { current.optimisticallyCancel(debtor) }
        viewModelScope.launch {
            val result = organizerFinanceGateway.updateChargeStatus(
                groupId = GroupId(groupId),
                chargeId = debtor.chargeId,
                version = FinanceVersionToken("\"${debtor.chargeVersion}\""),
                command = ChargeStatusCommand(ChargeStatus.Cancelled),
            )
            if (generation != receiptGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    update { it.copy(updatingChargeId = null, operationFailed = false, cancelFailed = false) }
                    emit(GroupCashboxEffect.MutationSucceeded)
                }
                is SaqzResult.Failure -> {
                    update { previous.copy(cancelFailed = true, updatingChargeId = null, cancelSheetChargeId = null) }
                    // Versão velha: recarrega por baixo para a próxima tentativa sair com a versão certa.
                    if (result.error == FinanceError.Conflict) load(softRefresh = true)
                }
            }
        }
    }

    /** Cancelar não entra no saldo nem no recebido: só tira a cobrança da lista de quem deve. */
    private fun GroupCashboxState.optimisticallyCancel(debtor: DebtorUi): GroupCashboxState {
        val remainingDebtors = debtors.filterNot { it.chargeId == debtor.chargeId }
        return copy(
            debtors = remainingDebtors,
            overdueBanner = buildOverdueBanner(remainingDebtors),
            updatingChargeId = debtor.chargeId,
            operationFailed = false,
            cancelFailed = false,
            cancelSheetChargeId = null,
        )
    }

    private fun openReviewSheet(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.reviews.none { it.chargeId == chargeId }
        ) return
        update {
            it.copy(
                reviewSheetChargeId = chargeId,
                receiptSheetChargeId = null,
                waiveSheetChargeId = null,
                cancelSheetChargeId = null,
                chargeSheetOpen = false,
                chargeSheetChargeId = null,
            )
        }
    }

    /** Trava de toque duplo: com uma operação em curso (`updatingChargeId`) o segundo toque é ignorado. */
    private fun resolveReview(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null) return
        val review = current.reviews.firstOrNull { it.chargeId == chargeId } ?: return
        val generation = ++receiptGeneration
        val loadAtStart = loadGeneration
        val previous = current
        update {
            it.copy(
                reviews = it.reviews.filterNot { row -> row.chargeId == chargeId },
                updatingChargeId = chargeId,
                reviewSheetChargeId = null,
                reviewFailed = false,
            )
        }
        viewModelScope.launch {
            val result = organizerFinanceGateway.resolveChargeReview(
                groupId = GroupId(groupId),
                chargeId = review.chargeId,
                version = FinanceVersionToken("\"${review.chargeVersion}\""),
            )
            if (generation != receiptGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    update { it.copy(updatingChargeId = null, reviewFailed = false) }
                    emit(GroupCashboxEffect.MutationSucceeded)
                }
                is SaqzResult.Failure -> {
                    update { previous.copy(reviewFailed = true, updatingChargeId = null, reviewSheetChargeId = null) }
                    // Versão velha: recarrega por baixo para a próxima tentativa sair com a versão certa.
                    if (result.error == FinanceError.Conflict) load(softRefresh = true)
                }
            }
        }
    }

    private fun openUndoSheet(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.settled.none { it.chargeId == chargeId }
        ) return
        update {
            it.copy(
                undoSheetChargeId = chargeId,
                receiptSheetChargeId = null,
                waiveSheetChargeId = null,
                cancelSheetChargeId = null,
                reviewSheetChargeId = null,
                chargeSheetOpen = false,
                chargeSheetChargeId = null,
            )
        }
    }

    /** Trava de toque duplo: com uma operação em curso (`updatingChargeId`) o segundo toque é ignorado. */
    private fun undo(chargeId: String, note: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null) return
        val settled = current.settled.firstOrNull { it.chargeId == chargeId } ?: return
        val generation = ++receiptGeneration
        val loadAtStart = loadGeneration
        val previous = current
        update {
            it.copy(
                settled = it.settled.map { row -> if (row.chargeId == chargeId) row.copy(isUpdating = true) else row },
                updatingChargeId = chargeId,
                undoSheetChargeId = null,
                undoFailed = false,
            )
        }
        viewModelScope.launch {
            val result = organizerFinanceGateway.updateChargeStatus(
                groupId = GroupId(groupId),
                chargeId = settled.chargeId,
                version = FinanceVersionToken("\"${settled.chargeVersion}\""),
                command = ChargeStatusCommand(ChargeStatus.Pending, note = note.trim().ifEmpty { null }),
            )
            if (generation != receiptGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    // O valor sai do caixa e a cobrança volta à lista de quem deve: recarrega por baixo.
                    update { it.copy(updatingChargeId = null, undoFailed = false) }
                    emit(GroupCashboxEffect.MutationSucceeded)
                    load(softRefresh = true)
                }
                is SaqzResult.Failure -> {
                    update { previous.copy(undoFailed = true, updatingChargeId = null, undoSheetChargeId = null) }
                    // Versão velha: recarrega por baixo para a próxima tentativa sair com a versão certa.
                    if (result.error == FinanceError.Conflict) load(softRefresh = true)
                }
            }
        }
    }

    /** A recarga por baixo não apaga os avisos da revisão, do desfazer e do cancelamento. */
    private fun GroupCashboxState.keepingNoticesFrom(previous: GroupCashboxState, softRefresh: Boolean) =
        if (softRefresh) {
            copy(
                reviewFailed = previous.reviewFailed,
                undoFailed = previous.undoFailed,
                cancelFailed = previous.cancelFailed,
            )
        } else {
            this
        }

    private fun GroupCashboxState.interacting() =
        chargeSheetOpen || receiptSheetChargeId != null || waiveSheetChargeId != null || cancelSheetChargeId != null ||
            reviewSheetChargeId != null || undoSheetChargeId != null || updatingChargeId != null

    /**
     * Volta à tela: recarga por baixo, sem esqueleto e sem tela de erro. Com carga em voo, folha
     * aberta ou baixa em andamento é ignorado — a troca do estado inteiro fecharia a folha e
     * descartaria o resultado da baixa. Sem conteúdo (erro da primeira carga) tenta de novo por inteiro.
     */
    private fun refresh() {
        if (isFirstAppearance()) return
        val current = state.value
        if (loadInFlight || current.isLoading || current.interacting()) return
        load(softRefresh = !current.loadFailed)
    }

    private fun load(softRefresh: Boolean = false) {
        val generation = ++loadGeneration
        loadInFlight = true
        if (!softRefresh) {
            receiptGeneration++
            update { it.copy(isLoading = true, loadFailed = false, operationFailed = false) }
        }
        val job = viewModelScope.launch {
            val groupResult = groupGateway.read(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            val membershipResult = membershipGateway.listMemberships(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            val group = when (groupResult) {
                is SaqzResult.Failure -> return@launch failLoad(generation, softRefresh)
                is SaqzResult.Success -> groupResult.value.group
            }
            val memberships = when (membershipResult) {
                is SaqzResult.Failure -> return@launch failLoad(generation, softRefresh)
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
                is SaqzResult.Failure -> return@launch failLoad(generation, softRefresh)
                is SaqzResult.Success -> statementResult.value
            }
            val charges = when (chargesResult) {
                is SaqzResult.Failure -> return@launch failLoad(generation, softRefresh)
                is SaqzResult.Success -> chargesResult.value.charges
            }
            // Por baixo, uma folha aberta depois do pedido não pode ser fechada pela troca do estado.
            if (softRefresh && state.value.interacting()) return@launch
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
                ).keepingNoticesFrom(it, softRefresh)
            }
        }
        job.invokeOnCompletion { if (generation == loadGeneration) loadInFlight = false }
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

    private fun waive(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null) return
        val debtor = current.debtors.firstOrNull { it.chargeId == chargeId && it.withdrew } ?: return
        val generation = ++receiptGeneration
        val loadAtStart = loadGeneration
        val previous = current
        update { current.optimisticallyWaive(debtor) }
        viewModelScope.launch {
            val result = organizerFinanceGateway.updateChargeStatus(
                groupId = GroupId(groupId),
                chargeId = debtor.chargeId,
                version = FinanceVersionToken("\"${debtor.chargeVersion}\""),
                command = ChargeStatusCommand(ChargeStatus.Waived),
            )
            if (generation != receiptGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    update { it.copy(updatingChargeId = null, operationFailed = false, waiveFailed = false) }
                    emit(GroupCashboxEffect.MutationSucceeded)
                }
                is SaqzResult.Failure -> update {
                    previous.copy(waiveFailed = true, updatingChargeId = null, waiveSheetChargeId = null)
                }
            }
        }
    }

    /** Dispensar não entra no saldo nem no recebido: só tira a cobrança da lista de quem deve. */
    private fun GroupCashboxState.optimisticallyWaive(debtor: DebtorUi): GroupCashboxState {
        val remainingDebtors = debtors.filterNot { it.chargeId == debtor.chargeId }
        return copy(
            debtors = remainingDebtors,
            overdueBanner = buildOverdueBanner(remainingDebtors),
            updatingChargeId = debtor.chargeId,
            operationFailed = false,
            waiveFailed = false,
            waiveSheetChargeId = null,
            cancelSheetChargeId = null,
        )
    }

    /** Por baixo ([softRefresh]) a falha não troca a tela por erro: o caixa que está na tela fica. */
    private fun failLoad(generation: Long, softRefresh: Boolean) {
        if (generation != loadGeneration || softRefresh) return
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
        val memberPhotos = memberships.associate { it.userId to memberPhoto(groupId, it.userId, it.photoTag) }
        fun Charge.photo() = if (guestDisplayName != null) null else memberPhotos[memberId]
        val debtors = pending
            .map { it.toDebtor(memberNames[it.memberId] ?: "Membro", it.dueDate < date.toString()).copy(photo = it.photo()) }
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
            reviews = charges.filter { it.reviewRequired }
                .map { it.toReview(memberNames[it.memberId] ?: "Membro").copy(photo = it.photo()) },
            settled = monthly
                .filter { (it.status == ChargeStatus.Paid || it.status == ChargeStatus.Waived) && !it.electronicOrder }
                .map { it.toSettled(memberNames[it.memberId] ?: "Membro").copy(photo = it.photo()) },
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
        withdrew = withdrew,
        referenceLabel = when (kind) {
            ChargeKind.Game -> "Diarista · jogo de ${formatDate(dueDate)}"
            ChargeKind.Monthly -> "Mensalista · ${month?.let(::monthNameOnlyFromKey) ?: "mês"}"
        },
    )

    private fun Charge.toSettled(name: String) = SettledUi(
        chargeId = id,
        name = name,
        referenceLabel = "Mensalista · ${month?.let(::monthNameOnlyFromKey) ?: "mês"}",
        amountLabel = formatBrl(amountCents),
        chargeVersion = version,
        received = status == ChargeStatus.Paid,
    )

    private fun Charge.toReview(name: String) = ReviewUi(
        chargeId = id,
        name = guestDisplayName ?: name,
        referenceLabel = (when (kind) {
            ChargeKind.Game -> "Diarista · jogo de ${formatDate(dueDate)}"
            ChargeKind.Monthly -> "Mensalista · ${month?.let(::monthNameOnlyFromKey) ?: "mês"}"
        }) + " · " + when (status) {
            ChargeStatus.Paid -> "paga"
            ChargeStatus.Waived -> "dispensada"
            ChargeStatus.Cancelled -> "cancelada"
            ChargeStatus.Pending -> "em aberto"
        },
        amountLabel = formatBrl(amountCents),
        chargeVersion = version,
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
            waiveFailed = false,
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
