package br.com.saqz.groups.presentation.finance.overview

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceOverview
import br.com.saqz.groups.domain.finance.FinanceOverviewGateway
import br.com.saqz.groups.domain.finance.FinanceOverviewQuery
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class FinanceOverviewViewModel(
    private val gateway: FinanceOverviewGateway,
    private val nowPort: GroupNowPort,
) : MviViewModel<FinanceOverviewState, FinanceOverviewIntent, FinanceOverviewEffect>(
    initialState = financeOverviewInitialState(nowPort.now()),
) {
    private var loadGeneration = 0L
    private var loadInFlight = false

    init {
        load(FinanceOverviewPeriodSelection.CurrentMonth)
    }

    override fun handleIntent(intent: FinanceOverviewIntent) {
        when (intent) {
            is FinanceOverviewIntent.SelectPeriod -> load(intent.selection)
            is FinanceOverviewIntent.OpenGroup -> emit(FinanceOverviewEffect.OpenGroup(intent.groupId))
            FinanceOverviewIntent.TabActive -> if (!isFirstAppearance()) refresh()
            FinanceOverviewIntent.Retry -> load(state.value.selectedPeriod)
        }
    }

    /**
     * A aba reaparece (troca de aba, volta de filha, primeiro plano): recarga por baixo — sem
     * esqueleto e sem tela de erro. Com carga em voo é ignorado; sem conteúdo (erro) carrega por inteiro.
     */
    private fun refresh() {
        val current = state.value
        if (loadInFlight || current.isLoading) return
        load(current.selectedPeriod, softRefresh = !current.loadFailed)
    }

    private fun load(selection: FinanceOverviewPeriodSelection, softRefresh: Boolean = false) {
        val periods = financeOverviewPeriodOptions(financeOverviewDate(nowPort.now()))
        val period = periods.first { it.selection == selection }
        val generation = ++loadGeneration
        loadInFlight = true
        // Por baixo o período escolhido fica, mas as opções são recalculadas: na virada do mês
        // "Mês atual" passa a apontar para o mês novo.
        update {
            if (softRefresh) {
                it.copy(periods = periods)
            } else {
                it.copy(isLoading = true, loadFailed = false, selectedPeriod = selection, periods = periods)
            }
        }
        val job = viewModelScope.launch {
            when (val result = gateway.overview(period.query)) {
                is SaqzResult.Failure -> {
                    if (generation < loadGeneration) return@launch
                    if (!softRefresh) update { it.copy(isLoading = false, loadFailed = true) }
                }

                is SaqzResult.Success -> {
                    if (generation < loadGeneration) return@launch
                    update { result.value.toUiState(it) }
                }
            }
        }
        job.invokeOnCompletion { if (generation == loadGeneration) loadInFlight = false }
    }
}

private fun financeOverviewInitialState(now: Instant): FinanceOverviewState {
    return FinanceOverviewState(periods = financeOverviewPeriodOptions(financeOverviewDate(now)))
}

private fun financeOverviewDate(now: Instant): LocalDate =
    now.toLocalDateTime(TimeZone.currentSystemDefault()).date

internal fun financeOverviewPeriodOptions(today: LocalDate): List<FinanceOverviewPeriodOption> {
    val previousMonth = today.minus(DatePeriod(months = 1))
    return listOf(
        FinanceOverviewPeriodOption(
            selection = FinanceOverviewPeriodSelection.CurrentMonth,
            month = today.month.ordinal + 1,
            year = today.year,
            query = FinanceOverviewQuery(month = monthQuery(today)),
        ),
        FinanceOverviewPeriodOption(
            selection = FinanceOverviewPeriodSelection.PreviousMonth,
            month = previousMonth.month.ordinal + 1,
            year = previousMonth.year,
            query = FinanceOverviewQuery(month = monthQuery(previousMonth)),
        ),
        FinanceOverviewPeriodOption(
            selection = FinanceOverviewPeriodSelection.Year,
            month = null,
            year = today.year,
            query = FinanceOverviewQuery(year = today.year),
        ),
    )
}

private fun monthQuery(date: LocalDate): String =
    "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}"

private fun FinanceOverview.toUiState(previous: FinanceOverviewState) = previous.copy(
    isLoading = false,
    loadFailed = false,
    balance = totals.balanceCents.formatCurrency(),
    entered = totals.inCents.formatCurrency(),
    left = totals.outCents.formatCurrency(),
    receivable = totals.pendingCents.formatCurrency(),
    groups = groups.map { group ->
        FinanceOverviewGroupUi(
            id = group.id,
            name = group.name,
            balance = group.balanceCents.formatCurrency(),
            pendingMonthlyCount = group.status.pendingMonthlyCount,
            hasBillingConfigured = group.status.hasBillingConfigured,
        )
    },
    recentTransactions = recentTransactions.map { transaction ->
        FinanceOverviewTransactionUi(
            id = transaction.id,
            title = transaction.description ?: transaction.memberName,
            groupAndDate = "${transaction.groupName} · ${transaction.occurredAt.formatOverviewDate()}",
            amount = transaction.amountCents.formatSignedCurrency(transaction.direction),
            isIncoming = transaction.direction?.let { it == FinanceDirection.In },
        )
    },
)

private fun Long.formatCurrency(): String {
    val absolute = if (this < 0) -this else this
    val whole = absolute / 100
    val cents = (absolute % 100).toString().padStart(2, '0')
    val grouped = whole.toString().reversed().chunked(3).joinToString(".").reversed()
    val sign = if (this < 0) "-" else ""
    return "${sign}R\$ $grouped,$cents"
}

private fun Long.formatSignedCurrency(direction: FinanceDirection?): String {
    if (direction == null) return formatCurrency()

    val amount = when (direction) {
        FinanceDirection.In -> kotlin.math.abs(this)
        FinanceDirection.Out -> -kotlin.math.abs(this)
    }
    val sign = if (amount >= 0) "+" else "-"
    return "$sign${kotlin.math.abs(amount).formatCurrency()}"
}

private fun String.formatOverviewDate(): String = runCatching {
    val local = Instant.parse(this).toLocalDateTime(TimeZone.currentSystemDefault())
    "${local.day.toString().padStart(2, '0')}/${(local.month.ordinal + 1).toString().padStart(2, '0')}"
}.getOrDefault(this)
