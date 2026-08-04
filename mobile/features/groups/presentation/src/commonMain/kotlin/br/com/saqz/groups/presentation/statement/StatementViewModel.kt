package br.com.saqz.groups.presentation.statement

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementItem
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.coroutines.launch
import kotlin.math.abs

class StatementViewModel(
    private val groupId: String,
    private val gateway: FinanceStatementGateway,
) : MviViewModel<StatementState, StatementIntent, StatementEffect>(StatementState()) {
    private var loadGeneration = 0L

    override fun onIntent(intent: StatementIntent) {
        when (intent) {
            StatementIntent.Retry -> load(reset = true)
            is StatementIntent.SelectFilter -> if (intent.filter != state.value.filter) {
                update { it.copy(filter = intent.filter) }
                load(reset = true)
            }
            StatementIntent.LoadMore -> if (!state.value.isLoadingMore && state.value.hasMore) {
                load(reset = false)
            }
            StatementIntent.NewEntry -> emit(StatementEffect.OpenNewEntry(groupId))
        }
    }

    private fun load(reset: Boolean) {
        val requestGeneration = ++loadGeneration
        val offset = if (reset) 0 else state.value.nextOffset
        val filter = state.value.filter
        update {
            it.copy(
                isLoading = reset,
                isLoadingMore = !reset,
                loadFailed = false,
                error = null,
                items = if (reset) emptyList() else it.items,
                nextOffset = if (reset) 0 else it.nextOffset,
            )
        }
        viewModelScope.launch {
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
                is SaqzResult.Failure -> if (requestGeneration == loadGeneration) {
                    update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            loadFailed = true,
                            error = result.error.toUiError(),
                        )
                    }
                }
                is SaqzResult.Success -> if (requestGeneration == loadGeneration) {
                    applyPage(result.value, reset)
                }
            }
        }
    }

    private fun applyPage(page: FinanceStatementPage, reset: Boolean) {
        update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                loadFailed = false,
                error = null,
                items = if (reset) page.items.map(FinanceStatementItem::toUi) else {
                    it.items + page.items.map(FinanceStatementItem::toUi)
                },
                summary = page.summary.toUi(),
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
    }
}

private fun FinanceStatementItem.toUi() = StatementItemUi(
    id = id,
    direction = direction,
    title = title,
    meta = listOfNotNull(
        category.toStatementCategoryLabel(),
        paidMethod?.toStatementMethodLabel(),
        occurredAt.toStatementDateLabel(),
    ).joinToString(" · "),
    amountLabel = formatStatementAmount(amountCents, direction),
)

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
    "VENUE", "QUADRA" -> "Quadra"
    "EQUIPMENT", "MATERIAL" -> "Material"
    "REFEREE", "ARBITRAGEM" -> "Arbitragem"
    "RACHA" -> "Racha"
    "OTHER", "OUTROS" -> "Outros"
    else -> this.replaceFirstChar { it.uppercase() }
}

private fun br.com.saqz.groups.domain.finance.PaidMethod.toStatementMethodLabel(): String = when (this) {
    br.com.saqz.groups.domain.finance.PaidMethod.Pix -> "Pix"
    br.com.saqz.groups.domain.finance.PaidMethod.Cash -> "Dinheiro"
    br.com.saqz.groups.domain.finance.PaidMethod.Other -> "Outro método"
}

private fun String.toStatementDateLabel(): String {
    val date = substringBefore('T').split('-')
    return if (date.size == 3 && date.all { it.isNotBlank() }) {
        "${date[2]}/${date[1]}/${date[0]}"
    } else {
        this
    }
}
