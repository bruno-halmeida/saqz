package br.com.saqz.groups.presentation.statement

import br.com.saqz.groups.domain.finance.AppPaymentMethod

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.formatting.sanitizeBrlInput
import br.com.saqz.groups.domain.finance.CashboxGateway
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.presentation.ui.finance.formatBrlIn
import br.com.saqz.groups.presentation.ui.finance.formatBrlOut
import br.com.saqz.groups.presentation.ui.finance.monthNameOf
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementItem
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Instant

class StatementViewModel(
    private val groupId: String,
    private val gateway: FinanceStatementGateway,
    private val finance: OrganizerFinanceGateway,
    private val cashbox: CashboxGateway,
) : MviViewModel<StatementState, StatementIntent, StatementEffect>(StatementState()) {
    private var loadGeneration = 0L
    private var loadInFlight = false
    private var loadedOnce = false

    override fun handleIntent(intent: StatementIntent) {
        when (intent) {
            StatementIntent.Retry -> load(reset = true)
            StatementIntent.Refresh -> refresh()
            is StatementIntent.SelectFilter -> if (intent.filter != state.value.filter) {
                update { it.copy(filter = intent.filter, actionError = null) }
                load(reset = true)
            }
            StatementIntent.LoadMore -> if (!state.value.isLoadingMore && state.value.hasMore) {
                load(reset = false)
            }
            StatementIntent.NewEntry -> emit(StatementEffect.OpenNewEntry(groupId))
            StatementIntent.NewExpense -> emit(StatementEffect.OpenNewEntry(groupId, out = true))
            StatementIntent.EditOpening -> if (state.value.openingVersion != null && !state.value.isLoading) {
                update {
                    it.copy(
                        openingSheetOpen = true,
                        openingInput = it.openingBalanceCents?.let(::formatBrlPlain).orEmpty(),
                        openingInvalid = false,
                        openingFailed = false,
                        openingConflict = false,
                    )
                }
            }
            StatementIntent.DismissOpening -> if (!state.value.openingSaving) {
                update { it.copy(openingSheetOpen = false) }
            }
            is StatementIntent.OpeningInputChanged -> update {
                it.copy(openingInput = sanitizeBrlInput(intent.value), openingInvalid = false, openingFailed = false)
            }
            StatementIntent.SaveOpening -> saveOpening()
            is StatementIntent.Edit -> if (state.value.canAct(intent.itemId)) {
                update { it.copy(actionError = null) }
                emit(StatementEffect.OpenEditEntry(groupId, intent.itemId))
            }
            is StatementIntent.AskVoid -> if (state.value.canAct(intent.itemId)) {
                update { it.copy(voidTargetId = intent.itemId, actionError = null) }
            }
            StatementIntent.DismissVoid -> if (!state.value.isVoiding) {
                update { it.copy(voidTargetId = null) }
            }
            StatementIntent.ConfirmVoid -> confirmVoid()
        }
    }

    /** Mesmo contrato do caixa: `If-Match` com a versão lida; 409 relê e avisa; toque duplo trava em `openingSaving`. */
    private fun saveOpening() {
        val current = state.value
        if (current.openingSaving) return
        val version = current.openingVersion ?: return
        val cents = parseBrlToCents(current.openingInput)
        if (cents == null || cents > MAX_OPENING_CENTS) {
            update { it.copy(openingInvalid = true) }
            return
        }
        update { it.copy(openingSaving = true, openingFailed = false, openingConflict = false) }
        viewModelScope.launch {
            when (val result = cashbox.saveOpeningBalance(GroupId(groupId), FinanceVersionToken(version), cents)) {
                is SaqzResult.Success -> {
                    update {
                        it.copy(
                            openingSaving = false,
                            openingSheetOpen = false,
                            openingBalanceCents = result.value.amountCents,
                            openingVersion = result.value.version.value,
                        )
                    }
                    // O saldo acumulado do resumo mudou e o caixa também.
                    load(reset = true, softRefresh = true)
                    emit(StatementEffect.Voided)
                }
                is SaqzResult.Failure -> if (result.error == FinanceError.Conflict) {
                    val fresh = (cashbox.openingBalance(GroupId(groupId)) as? SaqzResult.Success)?.value
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

    private fun StatementState.canAct(itemId: String) =
        !isLoading && !isVoiding && items.any { it.id == itemId && it.manual }

    /**
     * A versão vem da leitura dos lançamentos, não do extrato (que não a carrega). Lançamento que
     * sumiu ou já foi anulado em outro aparelho é conflito: recarrega e avisa. Toque duplo não
     * dispara duas anulações: `isVoiding` trava até a resposta.
     */
    private fun confirmVoid() {
        val current = state.value
        val target = current.voidTargetId ?: return
        if (current.isVoiding) return
        update { it.copy(isVoiding = true, actionError = null) }
        viewModelScope.launch {
            val expense = when (val read = finance.expenses(GroupId(groupId))) {
                is SaqzResult.Failure -> return@launch failVoid(read.error)
                is SaqzResult.Success -> read.value.expenses.firstOrNull { it.id == target }
            }
            if (expense == null || expense.status != ExpenseStatus.Active) {
                return@launch failVoid(FinanceError.Conflict)
            }
            when (
                val result = finance.voidExpense(
                    GroupId(groupId),
                    target,
                    FinanceVersionToken("\"${expense.version}\""),
                )
            ) {
                is SaqzResult.Failure -> failVoid(result.error)
                is SaqzResult.Success -> {
                    update { it.copy(voidTargetId = null, isVoiding = false) }
                    load(reset = true, softRefresh = true)
                    emit(StatementEffect.Voided)
                }
            }
        }
    }

    private fun failVoid(error: FinanceError) {
        update {
            it.copy(
                voidTargetId = null,
                isVoiding = false,
                actionError = when (error) {
                    FinanceError.Conflict -> StatementActionError.Conflict
                    FinanceError.Forbidden -> StatementActionError.Forbidden
                    else -> StatementActionError.Failed
                },
            )
        }
        // Versão velha: recarrega por baixo para a próxima tentativa sair com a versão certa.
        if (error == FinanceError.Conflict) load(reset = true, softRefresh = true)
    }

    /**
     * Volta à tela (e primeira abertura: esta tela não carrega no `init`). Já carregada, recarrega
     * a primeira página por baixo — sem esqueleto, sem tela de erro, mantendo o filtro; ainda sem
     * conteúdo, faz a carga completa. Com carga em voo é ignorado.
     */
    private fun refresh() {
        val current = state.value
        if (loadInFlight || current.isLoading && loadedOnce || current.isLoadingMore) return
        load(reset = true, softRefresh = loadedOnce)
    }

    private fun load(reset: Boolean, softRefresh: Boolean = false) {
        val requestGeneration = ++loadGeneration
        loadInFlight = true
        val offset = if (reset) 0 else state.value.nextOffset
        val filter = state.value.filter
        if (!softRefresh) update {
            it.copy(
                isLoading = reset,
                isLoadingMore = !reset,
                loadFailed = if (reset) false else it.loadFailed,
                paginationFailed = if (reset) null else it.paginationFailed,
                error = if (reset) null else it.error,
                items = if (reset) emptyList() else it.items,
                nextOffset = if (reset) 0 else it.nextOffset,
            )
        }
        val job = viewModelScope.launch {
            when (
                val result = gateway.statement(
                    groupId = GroupId(groupId),
                    query = FinanceStatementQuery(
                        direction = filter.directionOrNull(),
                        limit = PAGE_SIZE,
                        offset = offset,
                    ),
                )
            ) {
                is SaqzResult.Failure -> if (requestGeneration == loadGeneration && !softRefresh) {
                    update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            loadFailed = reset,
                            paginationFailed = if (reset) null else result.error.toUiError(),
                            error = if (reset) result.error.toUiError() else it.error,
                        )
                    }
                }
                is SaqzResult.Success -> if (requestGeneration == loadGeneration) {
                    applyPage(result.value, reset)
                    if (reset) loadOpening(requestGeneration)
                }
            }
        }
        job.invokeOnCompletion { if (requestGeneration == loadGeneration) loadInFlight = false }
    }

    /** O saldo inicial vem do caixa (com a versão para editar); falhou, o cartão fica escondido. */
    private suspend fun loadOpening(generation: Long) {
        val opening = (cashbox.openingBalance(GroupId(groupId)) as? SaqzResult.Success)?.value ?: return
        if (generation != loadGeneration) return
        update {
            it.copy(
                openingLoaded = true,
                openingBalanceCents = opening.amountCents,
                openingVersion = opening.version.value,
            )
        }
    }

    private fun applyPage(page: FinanceStatementPage, reset: Boolean) {
        loadedOnce = true
        update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                loadFailed = false,
                paginationFailed = null,
                error = null,
                items = if (reset) page.items.map(FinanceStatementItem::toUi) else {
                    it.items + page.items.map(FinanceStatementItem::toUi)
                },
                summary = page.summary.toUi().copy(monthName = monthNameOf(page.month)),
                hasMore = page.hasMore,
                nextOffset = page.offset + page.items.size,
            )
        }
    }

    private fun StatementFilter.directionOrNull(): FinanceDirection? = when (this) {
        StatementFilter.All -> null
        StatementFilter.In -> FinanceDirection.In
        StatementFilter.Out -> FinanceDirection.Out
    }

    private fun FinanceError.toUiError(): GroupUiError = when (this) {
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

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_OPENING_CENTS = 99_999_999L
    }
}

private fun FinanceStatementItem.toUi() = StatementItemUi(
    id = id,
    direction = direction,
    title = title,
    // DEB-148: o dia vai no cabeçalho do grupo; a linha diz "Saída · Quadra", "Entrada · Venda" ou o meio ("Pix").
    meta = listOfNotNull(
        if (type.equals("EXPENSE", ignoreCase = true)) {
            (if (direction == FinanceDirection.In) "Entrada · " else "Saída · ") + category.toStatementCategoryLabel()
        } else {
            null
        },
        // FIN-014: o que veio pelo app diz o meio; a taxa automática se identifica e não se edita.
        when {
            appMethod == AppPaymentMethod.Card -> "pelo app · cartão"
            appMethod == AppPaymentMethod.Pix -> "pelo app · Pix"
            else -> paidMethod?.toStatementMethodLabel()
        },
        "automático".takeIf { automatic },
    ).joinToString(" · "),
    amountLabel = if (direction == FinanceDirection.In) formatBrlIn(amountCents) else formatBrlOut(amountCents),
    manual = type.equals("EXPENSE", ignoreCase = true) && !automatic,
    dayKey = occurredAt.toStatementDayKey(),
)

/** O dia local do lançamento ("2026-10-27"); data sem horário volta como veio. */
internal fun String.toStatementDayKey(timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    runCatching { Instant.parse(this).toLocalDateTime(timeZone).date.toString() }.getOrElse { take(10) }

private fun FinanceStatementSummary.toUi() = StatementSummaryUi(
    totalInCents = totalInCents,
    totalOutCents = totalOutCents,
    periodBalanceCents = periodBalanceCents,
    accumulatedBalanceCents = accumulatedBalanceCents,
)

internal fun formatStatementAmount(amountCents: Long, direction: FinanceDirection): String {
    val sign = if (direction == FinanceDirection.In) "+" else "−"
    return "$sign${formatStatementCurrency(amountCents)}"
}

internal fun formatStatementCurrency(amountCents: Long): String {
    val absolute = abs(amountCents)
    val reais = absolute / 100
    val centavos = (absolute % 100).toString().padStart(2, '0')
    return "R$ ${reais.withThousandsSeparator()},$centavos"
}

private fun Long.withThousandsSeparator(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(".")
    .reversed()

private fun String.toStatementCategoryLabel(): String = when (uppercase()) {
    "MONTHLY" -> "Mensalidade"
    "GAME" -> "Jogo"
    "VENUE", "QUADRA" -> "Quadra"
    "EQUIPMENT", "MATERIAL" -> "Material"
    "REFEREE", "ARBITRAGEM" -> "Árbitro"
    "RACHA" -> "Racha"
    "SPONSORSHIP" -> "Patrocínio"
    "SALE" -> "Venda"
    "OTHER", "OUTROS" -> "Outros"
    else -> this.replaceFirstChar { it.uppercase() }
}

private fun br.com.saqz.groups.domain.finance.PaidMethod.toStatementMethodLabel(): String = when (this) {
    br.com.saqz.groups.domain.finance.PaidMethod.Pix -> "Pix"
    br.com.saqz.groups.domain.finance.PaidMethod.Cash -> "Dinheiro"
    br.com.saqz.groups.domain.finance.PaidMethod.Other -> "Outro método"
}

internal fun String.toStatementDateLabel(
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = runCatching { Instant.parse(this).toLocalDateTime(timeZone) }.getOrNull()
    if (local != null) {
        return "${local.day.toString().padStart(2, '0')}/${(local.month.ordinal + 1).toString().padStart(2, '0')}/${local.year}"
    }
    val date = substringBefore('T').split('-')
    return if (date.size == 3 && date.all { it.isNotBlank() }) {
        "${date[2]}/${date[1]}/${date[0]}"
    } else {
        this
    }
}
