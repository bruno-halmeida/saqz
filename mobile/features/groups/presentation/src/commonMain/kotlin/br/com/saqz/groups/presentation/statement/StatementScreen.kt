package br.com.saqz.groups.presentation.statement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.statement_empty_body
import br.com.saqz.groups.resources.statement_empty_title
import br.com.saqz.groups.resources.statement_filter_all
import br.com.saqz.groups.resources.statement_filter_in
import br.com.saqz.groups.resources.statement_filter_out
import br.com.saqz.groups.resources.statement_in_accessibility
import br.com.saqz.groups.resources.statement_load_more
import br.com.saqz.groups.resources.statement_new_entry
import br.com.saqz.groups.resources.statement_out_accessibility
import br.com.saqz.groups.resources.statement_summary
import br.com.saqz.groups.resources.statement_title
import org.jetbrains.compose.resources.stringResource

internal object StatementTags {
    const val Screen = "finance-statement"
    const val Filter = "finance-statement-filter"
    const val NewEntry = "finance-statement-new-entry"
    const val LoadMore = "finance-statement-load-more"
}

@Composable
internal fun StatementScreen(
    state: StatementState,
    onBack: () -> Unit,
    onIntent: (StatementIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val statementTitle = stringResource(Res.string.statement_title)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(StatementTags.Screen),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.statement_title), onBack = onBack)
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
        } else if (state.loadFailed) {
            GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(StatementIntent.Retry) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                SaqzSegmented(
                    options = listOf(
                        stringResource(Res.string.statement_filter_all),
                        stringResource(Res.string.statement_filter_in),
                        stringResource(Res.string.statement_filter_out),
                    ),
                    selected = state.filter.ordinal,
                    onSelect = { selected ->
                        onIntent(StatementIntent.SelectFilter(StatementFilter.entries[selected]))
                    },
                    modifier = Modifier
                        .testTag(StatementTags.Filter)
                        .semantics { contentDescription = statementTitle },
                )
                Text(
                    text = stringResource(
                        Res.string.statement_summary,
                        state.items.size,
                        formatStatementBalance(state.summary.periodBalanceCents),
                    ),
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            if (state.items.isEmpty()) {
                SaqzEmptyState(
                    title = stringResource(Res.string.statement_empty_title),
                    description = stringResource(Res.string.statement_empty_body),
                    icon = SaqzIcons.CreditCard,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = metrics.horizontalPadding,
                        vertical = metrics.blockGap,
                    ),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        StatementRow(item)
                        if (index < state.items.lastIndex) SaqzDivider()
                    }
                    if (state.hasMore) {
                        item {
                            SaqzButton(
                                label = stringResource(Res.string.statement_load_more),
                                onClick = { onIntent(StatementIntent.LoadMore) },
                                loading = state.isLoadingMore,
                                enabled = !state.isLoadingMore,
                                fullWidth = true,
                                modifier = Modifier
                                    .padding(top = metrics.blockGap)
                                    .testTag(StatementTags.LoadMore),
                            )
                        }
                    }
                }
            }
            SaqzButton(
                label = stringResource(Res.string.statement_new_entry),
                onClick = { onIntent(StatementIntent.NewEntry) },
                fullWidth = true,
                modifier = Modifier
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
                    .testTag(StatementTags.NewEntry),
            )
        }
    }
}

@Composable
private fun StatementRow(item: StatementItemUi) {
    val colors = SaqzTheme.colors
    val directionLabel = if (item.direction == FinanceDirection.In) {
        stringResource(Res.string.statement_in_accessibility, item.title, item.meta, item.amountLabel)
    } else {
        stringResource(Res.string.statement_out_accessibility, item.title, item.meta, item.amountLabel)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SaqzTheme.metrics.blockGap)
            .semantics { contentDescription = directionLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Box(
            modifier = Modifier
                .size(SaqzTheme.metrics.iconButtonSize)
                .background(
                    if (item.direction == FinanceDirection.In) {
                        colors.success.copy(alpha = 0.12f)
                    } else {
                        colors.errorForeground.copy(alpha = 0.10f)
                    },
                    androidx.compose.foundation.shape.CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(
                icon = if (item.direction == FinanceDirection.In) SaqzIcons.ArrowRight else SaqzIcons.CreditCard,
                tint = if (item.direction == FinanceDirection.In) colors.success else colors.errorForeground,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = SaqzTheme.typography.body, color = colors.textPrimary)
            Text(item.meta, style = SaqzTheme.typography.caption, color = colors.textSecondary)
        }
        Text(
            text = item.amountLabel,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (item.direction == FinanceDirection.In) colors.success else colors.errorForeground,
        )
    }
}

internal fun formatStatementBalance(cents: Long): String = when {
    cents > 0 -> "+${formatStatementCurrency(cents)}"
    cents < 0 -> "−${formatStatementCurrency(-cents)}"
    else -> formatStatementCurrency(0)
}

@Preview
@Composable
private fun StatementPreview() = SaqzTheme {
    StatementScreen(
        state = StatementState(
            isLoading = false,
            items = listOf(
                StatementItemUi("1", FinanceDirection.In, "Mensalidade · Bia", "Pix · 04/08/2026", "+R$ 80,00"),
                StatementItemUi("2", FinanceDirection.Out, "Aluguel da quadra", "Quadra · 03/08/2026", "−R$ 320,00"),
            ),
            summary = StatementSummaryUi(periodBalanceCents = 64000),
        ),
        onBack = {},
        onIntent = {},
    )
}
