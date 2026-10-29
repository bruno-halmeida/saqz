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
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
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
import br.com.saqz.groups.resources.cash_expense
import br.com.saqz.groups.resources.cash_income
import br.com.saqz.groups.resources.cash_opening_conflict
import br.com.saqz.groups.resources.cash_opening_edit
import br.com.saqz.groups.resources.cash_opening_failed
import br.com.saqz.groups.resources.cash_opening_invalid
import br.com.saqz.groups.resources.cash_opening_label
import br.com.saqz.groups.resources.cash_opening_pinned
import br.com.saqz.groups.resources.cash_opening_pinned_sub
import br.com.saqz.groups.resources.cash_opening_save_short
import br.com.saqz.groups.resources.cash_opening_sheet_body
import br.com.saqz.groups.resources.cash_opening_sheet_title
import br.com.saqz.groups.resources.cash_opening_unanswered
import br.com.saqz.groups.resources.statement_month_in
import br.com.saqz.groups.resources.statement_month_out
import br.com.saqz.groups.presentation.ui.finance.CashIconTile
import br.com.saqz.groups.presentation.ui.finance.CashListRow
import br.com.saqz.groups.presentation.ui.finance.CashMoneyField
import br.com.saqz.groups.presentation.ui.finance.CashRowText
import br.com.saqz.groups.presentation.ui.finance.CashTileTone
import br.com.saqz.groups.presentation.ui.finance.formatBrlShort
import br.com.saqz.groups.presentation.ui.finance.weekdayDayMonth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import br.com.saqz.groups.resources.statement_action_conflict
import br.com.saqz.groups.resources.statement_action_failure
import br.com.saqz.groups.resources.statement_action_forbidden
import br.com.saqz.groups.resources.statement_empty_body
import br.com.saqz.groups.resources.statement_entry_edit
import br.com.saqz.groups.resources.statement_entry_edit_accessibility
import br.com.saqz.groups.resources.statement_entry_void
import br.com.saqz.groups.resources.statement_entry_void_accessibility
import br.com.saqz.groups.resources.statement_void_cancel
import br.com.saqz.groups.resources.statement_void_confirm
import br.com.saqz.groups.resources.statement_void_note
import br.com.saqz.groups.resources.statement_void_title
import br.com.saqz.groups.resources.statement_empty_title
import br.com.saqz.groups.resources.statement_filter_all
import br.com.saqz.groups.resources.statement_filter_in
import br.com.saqz.groups.resources.statement_filter_out
import br.com.saqz.groups.resources.statement_in_accessibility
import br.com.saqz.groups.resources.statement_load_more
import br.com.saqz.groups.resources.statement_pagination_error
import br.com.saqz.groups.resources.statement_pagination_retry
import br.com.saqz.groups.resources.statement_out_accessibility
import br.com.saqz.groups.resources.statement_title
import org.jetbrains.compose.resources.stringResource

internal object StatementTags {
    const val Screen = "finance-statement"
    const val Filter = "finance-statement-filter"
    const val NewEntry = "finance-statement-new-entry"
    const val LoadMore = "finance-statement-load-more"
    const val ActionError = "finance-statement-action-error"
    const val VoidSheet = "finance-statement-void-sheet"
    const val VoidConfirm = "finance-statement-void-confirm"
    const val NewExpense = "finance-statement-new-expense"
    const val Summary = "finance-statement-summary"
    const val Opening = "finance-statement-opening"
    const val OpeningEdit = "finance-statement-opening-edit"
    const val OpeningInput = "finance-statement-opening-input"
    const val OpeningSave = "finance-statement-opening-save"

    fun edit(id: String) = "finance-statement-edit-$id"
    fun void(id: String) = "finance-statement-void-$id"
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
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                val days = state.items.groupBy { it.dayKey }.toList()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = metrics.subGrid),
                    verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
                ) {
                    item(key = "filter") {
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
                    }
                    item(key = "summary") { MonthSummary(state) }
                    state.actionError?.let { error ->
                        item(key = "action-error") {
                            Text(
                                text = stringResource(
                                    when (error) {
                                        StatementActionError.Conflict -> Res.string.statement_action_conflict
                                        StatementActionError.Forbidden -> Res.string.statement_action_forbidden
                                        StatementActionError.Failed -> Res.string.statement_action_failure
                                    },
                                ),
                                style = SaqzTheme.typography.caption,
                                color = SaqzTheme.colors.errorForeground,
                                modifier = Modifier.testTag(StatementTags.ActionError),
                            )
                        }
                    }
                    if (state.openingLoaded && state.filter == StatementFilter.All) {
                        item(key = "opening") { PinnedOpening(state, onIntent) }
                    }
                    if (state.items.isEmpty()) {
                        item(key = "empty") {
                            SaqzEmptyState(
                                title = stringResource(Res.string.statement_empty_title),
                                description = stringResource(Res.string.statement_empty_body),
                                icon = SaqzIcons.CreditCard,
                            )
                        }
                    }
                    days.forEach { (day, items) ->
                        item(key = "day-$day") {
                            Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                                Text(
                                    text = weekdayDayMonth(day),
                                    style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                    color = SaqzTheme.colors.textSecondary,
                                    modifier = Modifier.padding(top = metrics.grid, start = metrics.subGrid),
                                )
                                SaqzCard(padded = false) {
                                    items.forEachIndexed { index, item ->
                                        StatementRow(item, first = index == 0, busy = state.isVoiding, onIntent = onIntent)
                                    }
                                }
                            }
                        }
                    }
                    if (state.hasMore) {
                        item(key = "more") {
                            Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
                                if (state.paginationFailed != null) {
                                    Text(
                                        text = stringResource(Res.string.statement_pagination_error),
                                        style = SaqzTheme.typography.caption,
                                        color = SaqzTheme.colors.errorForeground,
                                    )
                                }
                                SaqzButton(
                                    label = if (state.paginationFailed != null) {
                                        stringResource(Res.string.statement_pagination_retry)
                                    } else {
                                        stringResource(Res.string.statement_load_more)
                                    },
                                    onClick = { onIntent(StatementIntent.LoadMore) },
                                    loading = state.isLoadingMore,
                                    enabled = !state.isLoadingMore,
                                    fullWidth = true,
                                    modifier = Modifier.testTag(StatementTags.LoadMore),
                                )
                            }
                        }
                    }
                }
                SaqzDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaqzTheme.colors.surface)
                        .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                    horizontalArrangement = Arrangement.spacedBy(metrics.grid),
                ) {
                    SaqzButton(
                        label = stringResource(Res.string.cash_income),
                        onClick = { onIntent(StatementIntent.NewEntry) },
                        variant = SaqzButtonVariant.Secondary,
                        leadingContent = { SaqzIcon(SaqzIcons.Plus, tint = it) },
                        modifier = Modifier.weight(1f).testTag(StatementTags.NewEntry),
                    )
                    SaqzButton(
                        label = stringResource(Res.string.cash_expense),
                        onClick = { onIntent(StatementIntent.NewExpense) },
                        variant = SaqzButtonVariant.Ghost,
                        contentColor = SaqzTheme.colors.errorForeground,
                        borderColor = SaqzTheme.colors.errorForeground,
                        leadingContent = { SaqzIcon(SaqzIcons.Minus, tint = it) },
                        modifier = Modifier.weight(1f).testTag(StatementTags.NewExpense),
                    )
                }
            }
        }
        val voidItem = state.items.firstOrNull { it.id == state.voidTargetId }
        VoidEntrySheet(
            open = voidItem != null,
            item = voidItem,
            busy = state.isVoiding,
            onClose = { onIntent(StatementIntent.DismissVoid) },
            onConfirm = { onIntent(StatementIntent.ConfirmVoid) },
        )
        OpeningSheet(state, onIntent)
    }
}

/** "Outubro · entrou R$ 880 · saiu R$ 640", com o saiu em vermelho. */
@Composable
private fun MonthSummary(state: StatementState) {
    val summary = state.summary
    val colors = SaqzTheme.colors
    val inText = stringResource(Res.string.statement_month_in, formatBrlShort(summary.totalInCents))
    val outText = stringResource(Res.string.statement_month_out, formatBrlShort(kotlin.math.abs(summary.totalOutCents)))
    Text(
        text = buildAnnotatedString {
            append(summary.monthName.replaceFirstChar(Char::uppercase))
            append(" · ")
            append(inText)
            append(" · ")
            withStyle(SpanStyle(color = colors.errorForeground, fontWeight = FontWeight.SemiBold)) { append(outText) }
        },
        style = SaqzTheme.typography.support,
        color = colors.textSecondary,
        modifier = Modifier.testTag(StatementTags.Summary),
    )
}

/** O saldo inicial fixo no topo do extrato, com Editar (FIN-006). */
@Composable
private fun PinnedOpening(state: StatementState, onIntent: (StatementIntent) -> Unit) = SaqzCard(
    padded = false,
    modifier = Modifier.testTag(StatementTags.Opening),
) {
    CashListRow(first = true) {
        CashIconTile(SaqzIcons.Flag, CashTileTone.Brand)
        CashRowText(stringResource(Res.string.cash_opening_pinned), stringResource(Res.string.cash_opening_pinned_sub))
        Text(
            text = state.openingBalanceCents?.let(::formatBrlShort) ?: stringResource(Res.string.cash_opening_unanswered),
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzButton(
            label = stringResource(Res.string.cash_opening_edit),
            onClick = { onIntent(StatementIntent.EditOpening) },
            variant = SaqzButtonVariant.Ghost,
            size = SaqzButtonSize.Sm,
            modifier = Modifier.testTag(StatementTags.OpeningEdit),
        )
    }
}

@Composable
private fun OpeningSheet(state: StatementState, onIntent: (StatementIntent) -> Unit) = SaqzBottomSheet(
    open = state.openingSheetOpen,
    onClose = { onIntent(StatementIntent.DismissOpening) },
    title = stringResource(Res.string.cash_opening_sheet_title),
    description = stringResource(Res.string.cash_opening_sheet_body),
    footer = {
        SaqzButton(
            label = stringResource(Res.string.cash_opening_save_short),
            onClick = { onIntent(StatementIntent.SaveOpening) },
            fullWidth = true,
            loading = state.openingSaving,
            modifier = Modifier.testTag(StatementTags.OpeningSave),
        )
    },
) {
    CashMoneyField(
        value = state.openingInput,
        onValueChange = { onIntent(StatementIntent.OpeningInputChanged(it)) },
        label = stringResource(Res.string.cash_opening_label),
        errorText = when {
            state.openingInvalid -> stringResource(Res.string.cash_opening_invalid)
            state.openingFailed -> stringResource(Res.string.cash_opening_failed)
            else -> null
        },
        helperText = if (state.openingConflict) stringResource(Res.string.cash_opening_conflict) else null,
        modifier = Modifier.fillMaxWidth().testTag(StatementTags.OpeningInput),
    )
}

/** Confirmação de "Anular": o lançamento sai do saldo e não volta. */
@Composable
internal fun VoidEntrySheet(
    open: Boolean,
    item: StatementItemUi?,
    busy: Boolean,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        modifier = Modifier.testTag(StatementTags.VoidSheet),
        title = stringResource(Res.string.statement_void_title),
        description = item?.title,
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.statement_void_cancel),
                onClick = onClose,
                variant = SaqzButtonVariant.Secondary,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.statement_void_confirm),
                onClick = onConfirm,
                variant = SaqzButtonVariant.Danger,
                enabled = item != null && !busy,
                loading = busy,
                modifier = Modifier.weight(1f).testTag(StatementTags.VoidConfirm),
            )
        },
    ) {
        item?.let {
            Text(
                text = "${it.title} · ${it.amountLabel}",
                style = SaqzTheme.typography.subtitle,
                color = SaqzTheme.colors.textPrimary,
            )
        }
        Text(
            text = stringResource(Res.string.statement_void_note),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun StatementRow(item: StatementItemUi, first: Boolean, busy: Boolean, onIntent: (StatementIntent) -> Unit) {
    val out = item.direction == FinanceDirection.Out
    val directionLabel = if (!out) {
        stringResource(Res.string.statement_in_accessibility, item.title, item.meta, item.amountLabel)
    } else {
        stringResource(Res.string.statement_out_accessibility, item.title, item.meta, item.amountLabel)
    }
    CashListRow(
        first = first,
        modifier = Modifier.semantics { contentDescription = directionLabel },
        below = if (item.manual) {
            {
                val editLabel = stringResource(Res.string.statement_entry_edit_accessibility, item.title)
                val voidLabel = stringResource(Res.string.statement_entry_void_accessibility, item.title)
                Row(
                    modifier = Modifier.padding(start = SaqzTheme.metrics.grid * 6),
                    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
                ) {
                    SaqzButton(
                        label = stringResource(Res.string.statement_entry_edit),
                        onClick = { onIntent(StatementIntent.Edit(item.id)) },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                        enabled = !busy,
                        modifier = Modifier
                            .testTag(StatementTags.edit(item.id))
                            .semantics { contentDescription = editLabel },
                    )
                    SaqzButton(
                        label = stringResource(Res.string.statement_entry_void),
                        onClick = { onIntent(StatementIntent.AskVoid(item.id)) },
                        variant = SaqzButtonVariant.Ghost,
                        size = SaqzButtonSize.Sm,
                        enabled = !busy,
                        modifier = Modifier
                            .testTag(StatementTags.void(item.id))
                            .semantics { contentDescription = voidLabel },
                    )
                }
            }
        } else null,
    ) {
        CashIconTile(if (out) SaqzIcons.MoneyOut else SaqzIcons.MoneyIn, if (out) CashTileTone.Out else CashTileTone.In)
        CashRowText(item.title, item.meta)
        Text(
            text = item.amountLabel,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
            color = if (out) SaqzTheme.colors.errorForeground else SaqzTheme.colors.successForeground,
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
                StatementItemUi("1", FinanceDirection.In, "Mensalidade · Bia", "Pix", "+R$ 80", dayKey = "2026-08-04"),
                StatementItemUi("2", FinanceDirection.Out, "Aluguel da quadra", "Saída · Quadra", "−R$ 320", manual = true,
                    dayKey = "2026-08-03"),
            ),
            summary = StatementSummaryUi(totalInCents = 8000, totalOutCents = 32000, monthName = "agosto"),
            openingLoaded = true,
            openingBalanceCents = 25000,
        ),
        onBack = {},
        onIntent = {},
    )
}
