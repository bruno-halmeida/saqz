package br.com.saqz.groups.presentation.ui.finance.groupcash

import br.com.saqz.groups.presentation.photo.memberPhoto
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.formatting.sanitizeBrlInput
import br.com.saqz.groups.domain.finance.CashboxGateway
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceStatementItem
import br.com.saqz.groups.domain.finance.OpeningBalance
import br.com.saqz.groups.domain.finance.UnchargedSummary
import br.com.saqz.groups.presentation.ui.finance.dayMonth
import br.com.saqz.groups.presentation.ui.finance.formatBrlIn
import br.com.saqz.groups.presentation.ui.finance.formatBrlOut
import br.com.saqz.groups.presentation.ui.finance.formatBrlShort
import br.com.saqz.groups.presentation.ui.finance.monthNameOf
import br.com.saqz.groups.presentation.ui.finance.weekdayDayMonth
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
    private val cashboxGateway: CashboxGateway,
) : MviViewModel<GroupCashboxState, GroupCashboxIntent, GroupCashboxEffect>(GroupCashboxState()) {

    private var loadGeneration = 0L
    private var receiptGeneration = 0L
    private var loadInFlight = false

    /** Geração da carga que veio de um puxão — só ela avisa quando falha. */
    private var pulledGeneration = -1L

    init {
        load()
    }

    @Suppress("CyclomaticComplexMethod")
    override fun handleIntent(intent: GroupCashboxIntent) {
        when (intent) {
            GroupCashboxIntent.Retry -> load()
            GroupCashboxIntent.Refresh -> refresh()
            GroupCashboxIntent.PullRefresh -> pullRefresh()
            GroupCashboxIntent.DismissRefreshFailed -> update { it.copy(refreshFailed = false) }
            GroupCashboxIntent.ChargeMissing -> openChargeSheet()
            is GroupCashboxIntent.ChargeIndividual -> openChargeSheet(intent.chargeId)
            GroupCashboxIntent.Register, GroupCashboxIntent.NewIncome -> emit(GroupCashboxEffect.OpenNewEntry(groupId))
            GroupCashboxIntent.NewExpense -> emit(GroupCashboxEffect.OpenNewEntry(groupId, out = true))
            is GroupCashboxIntent.SelectTab -> update { it.copy(tab = intent.tab) }
            GroupCashboxIntent.OpenMenu -> if (!state.value.isLoading && !state.value.loadFailed) {
                update { it.copy(menuOpen = true) }
            }
            GroupCashboxIntent.DismissMenu -> update { it.copy(menuOpen = false) }
            is GroupCashboxIntent.OpenDebtorMenu -> openDebtorMenu(intent.chargeId)
            GroupCashboxIntent.DismissDebtorMenu -> update { it.copy(debtorMenuChargeId = null) }
            GroupCashboxIntent.OpenOpeningBalance -> openOpeningSheet()
            GroupCashboxIntent.DismissOpeningBalance -> if (!state.value.openingSaving) {
                update { it.copy(openingSheetOpen = false, openingInvalid = false, openingFailed = false) }
            }
            is GroupCashboxIntent.OpeningInputChanged -> update {
                it.copy(openingInput = sanitizeBrlInput(intent.value), openingInvalid = false, openingFailed = false)
            }
            GroupCashboxIntent.SaveOpeningBalance -> saveOpening(fromZero = false)
            GroupCashboxIntent.StartFromZero -> saveOpening(fromZero = true)
            GroupCashboxIntent.ReviewUncharged -> state.value.uncharged?.let { pending ->
                emit(
                    pending.singleGameId?.let { GroupCashboxEffect.OpenUnchargedReview(groupId, it) }
                        ?: GroupCashboxEffect.OpenUnchargedGames(groupId),
                )
            }
            GroupCashboxIntent.GenerateMonthly -> if (!state.value.isLoading && !state.value.loadFailed) {
                update { it.copy(menuOpen = false) }
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

    /** O ⋮ do devedor: Cobrar, Dispensar (quem desistiu ou faltou) e Cancelar. */
    private fun openDebtorMenu(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.debtors.none { it.chargeId == chargeId }
        ) return
        update { it.copy(debtorMenuChargeId = chargeId) }
    }

    private fun openOpeningSheet() {
        val current = state.value
        if (current.isLoading || current.loadFailed || current.openingVersion == null) return
        update {
            it.copy(
                menuOpen = false,
                openingSheetOpen = true,
                openingInput = it.openingBalanceCents?.let(::formatBrlPlain).orEmpty(),
                openingInvalid = false,
                openingFailed = false,
                openingConflict = false,
            )
        }
    }

    /**
     * Grava o saldo inicial com a versão lida (`If-Match`). Trava de toque duplo em [GroupCashboxState.openingSaving].
     * 409: relê o saldo, põe o valor de quem salvou antes no campo e avisa.
     */
    private fun saveOpening(fromZero: Boolean) {
        val current = state.value
        if (current.openingSaving || current.isLoading) return
        val version = current.openingVersion ?: return
        val cents = if (fromZero) 0L else parseBrlToCents(current.openingInput)
        if (cents == null || cents < 0L || cents > MAX_OPENING_CENTS) {
            update { it.copy(openingInvalid = true) }
            return
        }
        update { it.copy(openingSaving = true, openingInvalid = false, openingFailed = false, openingConflict = false) }
        viewModelScope.launch {
            when (val result = cashboxGateway.saveOpeningBalance(GroupId(groupId), FinanceVersionToken(version), cents)) {
                is SaqzResult.Success -> {
                    update {
                        it.copy(
                            openingSaving = false,
                            openingSheetOpen = false,
                            openingBalanceCents = result.value.amountCents,
                            openingVersion = result.value.version.value,
                        )
                    }
                    emit(GroupCashboxEffect.MutationSucceeded)
                    load(softRefresh = true)
                }
                is SaqzResult.Failure -> if (result.error == FinanceError.Conflict) {
                    val fresh = (cashboxGateway.openingBalance(GroupId(groupId)) as? SaqzResult.Success)?.value
                    update {
                        it.copy(
                            openingSaving = false,
                            openingConflict = true,
                            openingBalanceCents = fresh?.amountCents ?: it.openingBalanceCents,
                            openingVersion = fresh?.version?.value ?: it.openingVersion,
                            openingInput = fresh?.amountCents?.let(::formatBrlPlain) ?: it.openingInput,
                        )
                    }
                } else {
                    update { it.copy(openingSaving = false, openingFailed = true) }
                }
            }
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
                debtorMenuChargeId = null,
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

    /** Só quem desistiu ou faltou tem "Dispensar": cobrança comum segue o caminho do "Recebi". */
    private fun openWaiveSheet(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null ||
            current.debtors.none { it.chargeId == chargeId && it.waivable }
        ) return
        update {
            it.copy(
                waiveSheetChargeId = chargeId,
                debtorMenuChargeId = null,
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
                debtorMenuChargeId = null,
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
            dueLabel = formatBrlShort(remainingDebtors.sumOf { it.amountCents }),
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
            reviewSheetChargeId != null || undoSheetChargeId != null || updatingChargeId != null ||
            menuOpen || debtorMenuChargeId != null || openingSheetOpen || openingSaving

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

    /**
     * GER-002: folha aberta ou baixa em andamento ignoram o puxão, como o [refresh]; carga no ar leva o
     * puxão de carona — a bola mostra a espera e a falha dela avisa —, sem outra busca.
     */
    private fun pullRefresh() {
        val current = state.value
        val showingContent = !current.isLoading && !current.loadFailed
        if (!showingContent || current.isRefreshing || current.interacting()) return
        update { it.copy(isRefreshing = true, refreshFailed = false) }
        if (loadInFlight) {
            pulledGeneration = loadGeneration
            return
        }
        // Antes do `load`: a falha pode chegar antes de ele voltar.
        pulledGeneration = loadGeneration + 1
        load(softRefresh = true)
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
            // O servidor aceita no máximo 100 por página: as listas do mês seguem o `hasMore` até MONTH_PAGES páginas.
            // ponytail: mês com mais de 500 lançamentos mostra só os 500 primeiros; o placar vem do `summary` e fecha.
            val statementResult = monthStatement(monthKey)
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
            // Saldo inicial e pendência da retroativa não derrubam o caixa: sem eles, some a pergunta e a faixa.
            val opening = (cashboxGateway.openingBalance(GroupId(groupId)) as? SaqzResult.Success)?.value
            if (generation != loadGeneration) return@launch
            val uncharged = (cashboxGateway.unchargedSummary(GroupId(groupId)) as? SaqzResult.Success)?.value
            if (generation != loadGeneration) return@launch
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
                    opening = opening,
                    uncharged = uncharged,
                ).copy(tab = it.tab).keepingNoticesFrom(it, softRefresh)
            }
        }
        job.invokeOnCompletion {
            if (generation == loadGeneration) {
                loadInFlight = false
                update { it.copy(isRefreshing = false) }
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

    private fun waive(chargeId: String) {
        val current = state.value
        if (current.isLoading || current.updatingChargeId != null) return
        val debtor = current.debtors.firstOrNull { it.chargeId == chargeId && it.waivable } ?: return
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
            dueLabel = formatBrlShort(remainingDebtors.sumOf { it.amountCents }),
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
        if (generation != loadGeneration) return
        if (softRefresh) {
            if (generation == pulledGeneration) update { it.copy(refreshFailed = true) }
            return
        }
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
        opening: OpeningBalance? = null,
        uncharged: UnchargedSummary? = null,
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
                .filter { (it.status == ChargeStatus.Paid || it.status == ChargeStatus.Waived) && !it.settledElectronically }
                .map { it.toSettled(memberNames[it.memberId] ?: "Membro").copy(photo = it.photo()) },
            pix = pixKey?.trim()?.takeIf(String::isNotEmpty)?.let { PixUi(it, pixLabel) },
            monthIn = statement.items.filter { it.direction == FinanceDirection.In }.map { it.toLedger() },
            monthOut = statement.items.filter { it.direction == FinanceDirection.Out }.map { it.toLedger() },
            monthInLabel = formatBrlShort(statement.summary.totalInCents),
            monthInCents = statement.summary.totalInCents,
            monthOutLabel = formatBrlShort(kotlin.math.abs(statement.summary.totalOutCents)),
            dueLabel = formatBrlShort(debtors.sumOf { it.amountCents }),
            openingBalanceCents = opening?.amountCents ?: statement.summary.openingBalanceCents,
            openingVersion = opening?.version?.value,
            openingLoaded = opening != null,
            uncharged = uncharged?.toPending(),
        )
    }

    private suspend fun monthStatement(monthKey: String): SaqzResult<FinanceStatementPage, FinanceError> {
        var page = when (
            val first = statementGateway.statement(GroupId(groupId), FinanceStatementQuery(month = monthKey, limit = MONTH_PAGE))
        ) {
            is SaqzResult.Failure -> return first
            is SaqzResult.Success -> first.value
        }
        var items = page.items
        var pages = 1
        while (page.hasMore && pages < MONTH_PAGES) {
            val next = statementGateway.statement(
                GroupId(groupId),
                FinanceStatementQuery(month = monthKey, limit = MONTH_PAGE, offset = items.size),
            )
            page = (next as? SaqzResult.Success)?.value ?: break
            items = items + page.items
            pages++
        }
        return SaqzResult.Success(page.copy(items = items))
    }

    private fun FinanceStatementItem.toLedger(): LedgerUi {
        val out = direction == FinanceDirection.Out
        return LedgerUi(
            id = id,
            title = title,
            subtitle = weekdayDayMonth(occurredAt),
            amountLabel = if (out) formatBrlOut(amountCents) else formatBrlIn(amountCents),
            out = out,
        )
    }

    private fun UnchargedSummary.toPending(): UnchargedPendingUi? {
        if (peopleCount <= 0 || games.isEmpty()) return null
        val months = games.map { it.localDate.take(7) }.distinct()
        val single = games.singleOrNull()
        return UnchargedPendingUi(
            peopleCount = peopleCount,
            gamesCount = games.size,
            monthName = months.singleOrNull()?.let(::monthNameOf),
            singleGameDate = single?.let { dayMonth(it.localDate) },
            singleGameId = single?.gameId,
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
        noShow = noShow && !withdrew,
        dueDate = dueDate,
        cashMeta = when {
            kind == ChargeKind.Monthly ->
                "${if (isOverdue) "Venceu" else "Vence"} ${formatDate(dueDate)} · ${formatBrlShort(amountCents)}"
            guestDisplayName != null -> "Convidou $guestDisplayName · ${formatBrlShort(amountCents)}"
            else -> "${weekdayDayMonth(dueDate)} · ${formatBrlShort(amountCents)}"
        },
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
            dueLabel = formatBrlShort(remainingDebtors.sumOf { it.amountCents }),
            // O recebido entra no Entrou do mês: saldo, placar e extrato fecham juntos.
            monthInCents = monthInCents + debtor.amountCents,
            monthInLabel = formatBrlShort(monthInCents + debtor.amountCents),
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
        const val MONTH_PAGE = 100
        const val MONTH_PAGES = 5
        const val MAX_OPENING_CENTS = 99_999_999L
        val MONTH_NAMES = listOf(
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
        )
    }
}
