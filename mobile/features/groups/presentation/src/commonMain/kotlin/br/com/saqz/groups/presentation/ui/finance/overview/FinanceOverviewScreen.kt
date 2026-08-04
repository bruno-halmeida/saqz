package br.com.saqz.groups.presentation.ui.finance.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewGroupUi
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewIntent
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewPeriodOption
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewPeriodSelection
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewState
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewTransactionUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.finance_overview_all_clear
import br.com.saqz.groups.resources.finance_overview_balance_eyebrow
import br.com.saqz.groups.resources.finance_overview_empty_body
import br.com.saqz.groups.resources.finance_overview_empty_title
import br.com.saqz.groups.resources.finance_overview_entered
import br.com.saqz.groups.resources.finance_overview_group_count
import br.com.saqz.groups.resources.finance_overview_group_count_one
import br.com.saqz.groups.resources.finance_overview_groups
import br.com.saqz.groups.resources.finance_overview_left
import br.com.saqz.groups.resources.finance_overview_load_error_body
import br.com.saqz.groups.resources.finance_overview_load_error_title
import br.com.saqz.groups.resources.finance_overview_month_april
import br.com.saqz.groups.resources.finance_overview_month_august
import br.com.saqz.groups.resources.finance_overview_month_december
import br.com.saqz.groups.resources.finance_overview_month_february
import br.com.saqz.groups.resources.finance_overview_month_january
import br.com.saqz.groups.resources.finance_overview_month_july
import br.com.saqz.groups.resources.finance_overview_month_june
import br.com.saqz.groups.resources.finance_overview_month_march
import br.com.saqz.groups.resources.finance_overview_month_may
import br.com.saqz.groups.resources.finance_overview_month_november
import br.com.saqz.groups.resources.finance_overview_month_october
import br.com.saqz.groups.resources.finance_overview_month_september
import br.com.saqz.groups.resources.finance_overview_no_billing
import br.com.saqz.groups.resources.finance_overview_pending_monthly
import br.com.saqz.groups.resources.finance_overview_pending_monthly_one
import br.com.saqz.groups.resources.finance_overview_receivable
import br.com.saqz.groups.resources.finance_overview_recent
import br.com.saqz.groups.resources.finance_overview_retry
import br.com.saqz.groups.resources.finance_overview_title
import br.com.saqz.groups.resources.finance_overview_transaction_in
import br.com.saqz.groups.resources.finance_overview_transaction_neutral
import br.com.saqz.groups.resources.finance_overview_transaction_out
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object FinanceOverviewTags {
    const val Screen = "finance-overview-screen"
    const val Balance = "finance-overview-balance"
    const val BalanceValue = "finance-overview-balance-value"
    const val Metrics = "finance-overview-metrics"
    const val Periods = "finance-overview-periods"
    const val Empty = "finance-overview-empty"
    const val Loading = "finance-overview-loading"
    const val Failure = "finance-overview-failure"
    const val Retry = "finance-overview-retry"

    fun period(selection: FinanceOverviewPeriodSelection) = "finance-overview-period-${selection.name}"

    fun group(id: String) = "finance-overview-group-$id"

    fun transaction(id: String) = "finance-overview-transaction-$id"
}

@Composable
fun FinanceOverviewScreen(
    state: FinanceOverviewState,
    onIntent: (FinanceOverviewIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentModifier = modifier
        .fillMaxSize()
        .background(SaqzTheme.colors.background)
    BoxWithConstraints(modifier = contentModifier.testTag(FinanceOverviewTags.Screen)) {
        val branchModifier = Modifier.fillMaxWidth().height(maxHeight)
        when {
            state.isLoading -> FinanceOverviewLoading(branchModifier)
            state.loadFailed -> FinanceOverviewFailure(branchModifier, onIntent)
            state.isEmpty -> FinanceOverviewEmpty(branchModifier)
            else -> FinanceOverviewContent(state, onIntent, branchModifier)
        }
    }
}

@Composable
private fun FinanceOverviewLoading(modifier: Modifier) {
    Box(modifier = modifier.testTag(FinanceOverviewTags.Loading), contentAlignment = Alignment.Center) {
        SaqzSpinner()
    }
}

@Composable
private fun FinanceOverviewFailure(
    modifier: Modifier,
    onIntent: (FinanceOverviewIntent) -> Unit,
) {
    Column(
        modifier = modifier
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(FinanceOverviewTags.Failure),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.finance_overview_load_error_title),
            style = SaqzTheme.typography.subtitle,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.finance_overview_load_error_body),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
        )
        SaqzButton(
            label = stringResource(Res.string.finance_overview_retry),
            onClick = { onIntent(FinanceOverviewIntent.Retry) },
            modifier = Modifier.testTag(FinanceOverviewTags.Retry),
            variant = br.com.saqz.designsystem.SaqzButtonVariant.Secondary,
            size = br.com.saqz.designsystem.SaqzButtonSize.Sm,
        )
    }
}

@Composable
private fun FinanceOverviewEmpty(modifier: Modifier) {
    Column(
        modifier = modifier
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(FinanceOverviewTags.Empty),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.finance_overview_empty_title),
            style = SaqzTheme.typography.subtitle,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.finance_overview_empty_body),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun FinanceOverviewContent(
    state: FinanceOverviewState,
    onIntent: (FinanceOverviewIntent) -> Unit,
    modifier: Modifier,
) {
    val metrics = SaqzTheme.metrics
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = metrics.horizontalPadding,
            end = metrics.horizontalPadding,
            top = metrics.blockGap,
            bottom = metrics.sectionGap,
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(Res.string.finance_overview_title),
                style = SaqzTheme.typography.headline,
                color = SaqzTheme.colors.textPrimary,
            )
        }
        item(key = "periods") {
            FinanceOverviewPeriodSelector(state, onIntent)
        }
        item(key = "balance") {
            FinanceOverviewBalanceCard(state)
        }
        item(key = "metrics") {
            FinanceOverviewMetrics(state)
        }
        item(key = "groups-header") {
            Text(
                text = stringResource(Res.string.finance_overview_groups),
                style = SaqzTheme.typography.subtitle,
                color = SaqzTheme.colors.textPrimary,
            )
        }
        item(key = "groups") {
            FinanceOverviewGroupList(state.groups, onIntent)
        }
        if (state.recentTransactions.isNotEmpty()) {
            item(key = "recent-header") {
                Text(
                    text = stringResource(Res.string.finance_overview_recent),
                    style = SaqzTheme.typography.subtitle,
                    color = SaqzTheme.colors.textPrimary,
                )
            }
            item(key = "recent") {
                FinanceOverviewTransactions(state.recentTransactions)
            }
        }
    }
}

@Composable
private fun FinanceOverviewPeriodSelector(
    state: FinanceOverviewState,
    onIntent: (FinanceOverviewIntent) -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.blockRadius))
            .background(SaqzTheme.colors.surfaceSoft)
            .padding(metrics.subGrid)
            .testTag(FinanceOverviewTags.Periods),
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        state.periods.forEach { option ->
            val selected = option.selection == state.selectedPeriod
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(metrics.minimumTouchTarget)
                    .clip(CircleShape)
                    .background(if (selected) SaqzTheme.colors.primary else Color.Transparent)
                    .clickable(
                        onClickLabel = periodLabel(option),
                        role = Role.Button,
                    ) { onIntent(FinanceOverviewIntent.SelectPeriod(option.selection)) }
                    .testTag(FinanceOverviewTags.period(option.selection)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = periodLabel(option),
                    style = SaqzTheme.typography.caption,
                    color = if (selected) SaqzTheme.colors.onPrimary else SaqzTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun periodLabel(option: FinanceOverviewPeriodOption): String = option.month
    ?.let { monthResource(it) }
    ?.let { stringResource(it) }
    ?: option.year.toString()

private fun monthResource(month: Int): StringResource = when (month) {
    1 -> Res.string.finance_overview_month_january
    2 -> Res.string.finance_overview_month_february
    3 -> Res.string.finance_overview_month_march
    4 -> Res.string.finance_overview_month_april
    5 -> Res.string.finance_overview_month_may
    6 -> Res.string.finance_overview_month_june
    7 -> Res.string.finance_overview_month_july
    8 -> Res.string.finance_overview_month_august
    9 -> Res.string.finance_overview_month_september
    10 -> Res.string.finance_overview_month_october
    11 -> Res.string.finance_overview_month_november
    12 -> Res.string.finance_overview_month_december
    else -> Res.string.finance_overview_title
}

@Composable
private fun FinanceOverviewBalanceCard(state: FinanceOverviewState) {
    SaqzCard(
        modifier = Modifier.testTag(FinanceOverviewTags.Balance),
        tone = SaqzCardTone.Soft,
    ) {
        Text(
            text = stringResource(Res.string.finance_overview_balance_eyebrow),
            style = SaqzTheme.typography.eyebrow,
            color = SaqzTheme.colors.textSecondary,
        )
        Text(
            text = state.balance,
            style = SaqzTheme.typography.headline,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.testTag(FinanceOverviewTags.BalanceValue),
        )
        val option = state.periods.first { it.selection == state.selectedPeriod }
        val period = periodLabel(option)
        Text(
            text = if (state.groups.size == 1) {
                stringResource(Res.string.finance_overview_group_count_one, state.groups.size, period)
            } else {
                stringResource(Res.string.finance_overview_group_count, state.groups.size, period)
            },
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun FinanceOverviewMetrics(state: FinanceOverviewState) {
    SaqzCard(modifier = Modifier.testTag(FinanceOverviewTags.Metrics), padded = false) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SaqzTheme.metrics.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinanceOverviewMetric(state.entered, stringResource(Res.string.finance_overview_entered))
            SaqzDivider(modifier = Modifier.height(SaqzTheme.metrics.buttonHeight), vertical = true)
            FinanceOverviewMetric(state.left, stringResource(Res.string.finance_overview_left))
            SaqzDivider(modifier = Modifier.height(SaqzTheme.metrics.buttonHeight), vertical = true)
            FinanceOverviewMetric(state.receivable, stringResource(Res.string.finance_overview_receivable))
        }
    }
}

@Composable
private fun RowScope.FinanceOverviewMetric(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(
            text = value,
            style = SaqzTheme.typography.support.copy(fontWeight = SaqzTheme.typography.label.fontWeight),
            color = SaqzTheme.colors.textPrimary,
            maxLines = 1,
        )
        Text(label, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
    }
}

@Composable
private fun FinanceOverviewGroupList(
    groups: List<FinanceOverviewGroupUi>,
    onIntent: (FinanceOverviewIntent) -> Unit,
) {
    SaqzCard(padded = false) {
        groups.forEachIndexed { index, group ->
            FinanceOverviewGroupRow(group, onIntent)
            if (index < groups.lastIndex) SaqzDivider()
        }
    }
}

@Composable
private fun FinanceOverviewGroupRow(
    group: FinanceOverviewGroupUi,
    onIntent: (FinanceOverviewIntent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = group.name,
                role = Role.Button,
            ) { onIntent(FinanceOverviewIntent.OpenGroup(group.id)) }
            .testTag(FinanceOverviewTags.group(group.id))
            .padding(SaqzTheme.metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(group.name, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
            Text(groupStatus(group), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(group.balance, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
            SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun groupStatus(group: FinanceOverviewGroupUi): String = when {
    group.pendingMonthlyCount == 1 -> stringResource(Res.string.finance_overview_pending_monthly_one)
    group.pendingMonthlyCount > 1 -> stringResource(
        Res.string.finance_overview_pending_monthly,
        group.pendingMonthlyCount,
    )
    group.hasBillingConfigured -> stringResource(Res.string.finance_overview_all_clear)
    else -> stringResource(Res.string.finance_overview_no_billing)
}

@Composable
private fun FinanceOverviewTransactions(transactions: List<FinanceOverviewTransactionUi>) {
    SaqzCard(padded = false) {
        transactions.forEachIndexed { index, transaction ->
            FinanceOverviewTransactionRow(transaction)
            if (index < transactions.lastIndex) SaqzDivider()
        }
    }
}

@Composable
private fun FinanceOverviewTransactionRow(transaction: FinanceOverviewTransactionUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FinanceOverviewTags.transaction(transaction.id))
            .padding(SaqzTheme.metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = transaction.title ?: stringResource(
                    when (transaction.isIncoming) {
                        true -> Res.string.finance_overview_transaction_in
                        false -> Res.string.finance_overview_transaction_out
                        null -> Res.string.finance_overview_transaction_neutral
                    },
                ),
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(transaction.groupAndDate, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
        }
        Text(
            text = transaction.amount,
            style = SaqzTheme.typography.label,
            color = when (transaction.isIncoming) {
                true -> SaqzTheme.colors.success
                false -> SaqzTheme.colors.errorForeground
                null -> SaqzTheme.colors.textPrimary
            },
        )
    }
}

internal object FinanceOverviewSamples {
    private val options = listOf(
        FinanceOverviewPeriodOption(
            FinanceOverviewPeriodSelection.CurrentMonth,
            month = 7,
            year = 2026,
            query = br.com.saqz.groups.domain.finance.FinanceOverviewQuery(month = "2026-07"),
        ),
        FinanceOverviewPeriodOption(
            FinanceOverviewPeriodSelection.PreviousMonth,
            month = 6,
            year = 2026,
            query = br.com.saqz.groups.domain.finance.FinanceOverviewQuery(month = "2026-06"),
        ),
        FinanceOverviewPeriodOption(
            FinanceOverviewPeriodSelection.Year,
            month = null,
            year = 2026,
            query = br.com.saqz.groups.domain.finance.FinanceOverviewQuery(year = 2026),
        ),
    )

    val filled = FinanceOverviewState(
        isLoading = false,
        periods = options,
        balance = "R$ 60.000,00",
        entered = "R$ 120.000,00",
        left = "R$ 60.000,00",
        receivable = "R$ 40.000,00",
        groups = listOf(
            FinanceOverviewGroupUi("group-1", "Vôlei do CERET", "R$ 32.000,00", 8, true),
            FinanceOverviewGroupUi("group-2", "Areia do Ibira", "R$ 28.000,00", 0, true),
            FinanceOverviewGroupUi("group-3", "Futevôlei da Vila", "R$ 0,00", 0, false),
        ),
        recentTransactions = listOf(
            FinanceOverviewTransactionUi("transaction-1", "Mensalidade de julho", "Vôlei do CERET · 02/07", "+R$ 120,00", true),
            FinanceOverviewTransactionUi("transaction-2", "Aluguel da quadra", "Areia do Ibira · 01/07", "-R$ 70,00", false),
        ),
    )
    val empty = FinanceOverviewState(isLoading = false, periods = options)
    val loading = FinanceOverviewState(periods = options)
    val failed = FinanceOverviewState(isLoading = false, loadFailed = true, periods = options)
}

@Composable
private fun FinanceOverviewPreviewShell(state: FinanceOverviewState) = SaqzTheme {
    FinanceOverviewScreen(state = state, onIntent = {})
}

@Preview(name = "5a — visão cheia", widthDp = 390, heightDp = 844)
@Composable
private fun FinanceOverviewFilledPreview() = FinanceOverviewPreviewShell(FinanceOverviewSamples.filled)

@Preview(name = "5f — sem grupo administrado", widthDp = 390, heightDp = 844)
@Composable
private fun FinanceOverviewEmptyPreview() = FinanceOverviewPreviewShell(FinanceOverviewSamples.empty)

@Preview(name = "5a — carregando", widthDp = 390, heightDp = 844)
@Composable
private fun FinanceOverviewLoadingPreview() = FinanceOverviewPreviewShell(FinanceOverviewSamples.loading)

@Preview(name = "5a — falha", widthDp = 390, heightDp = 844)
@Composable
private fun FinanceOverviewFailurePreview() = FinanceOverviewPreviewShell(FinanceOverviewSamples.failed)
