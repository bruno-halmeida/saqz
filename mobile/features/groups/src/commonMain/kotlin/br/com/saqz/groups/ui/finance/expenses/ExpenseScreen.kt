package br.com.saqz.groups.ui.finance.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.formatting.formatLocalDatePtBrString
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzCard
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseAction
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraft
import br.com.saqz.groups.presentation.finance.expenses.ExpenseError
import br.com.saqz.groups.presentation.finance.expenses.ExpenseForm
import br.com.saqz.groups.presentation.finance.expenses.ExpenseIntent
import br.com.saqz.groups.presentation.finance.expenses.ExpenseState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.action_retry
import br.com.saqz.groups.resources.expense_action_created
import br.com.saqz.groups.resources.expense_action_edited
import br.com.saqz.groups.resources.expense_action_voided
import br.com.saqz.groups.resources.expense_amount
import br.com.saqz.groups.resources.expense_audit
import br.com.saqz.groups.resources.expense_category
import br.com.saqz.groups.resources.expense_category_equipment
import br.com.saqz.groups.resources.expense_category_other
import br.com.saqz.groups.resources.expense_category_referee
import br.com.saqz.groups.resources.expense_category_venue
import br.com.saqz.groups.resources.expense_conflict
import br.com.saqz.groups.resources.expense_create
import br.com.saqz.groups.resources.expense_custom_category
import br.com.saqz.groups.resources.expense_date
import br.com.saqz.groups.resources.expense_date_value
import br.com.saqz.groups.resources.expense_description
import br.com.saqz.groups.resources.expense_edit
import br.com.saqz.groups.resources.expense_error
import br.com.saqz.groups.resources.expense_form_create
import br.com.saqz.groups.resources.expense_form_edit
import br.com.saqz.groups.resources.expense_manual_notice
import br.com.saqz.groups.resources.expense_notes
import br.com.saqz.groups.resources.expense_save
import br.com.saqz.groups.resources.expense_status_active
import br.com.saqz.groups.resources.expense_status_voided
import br.com.saqz.groups.resources.expense_title
import br.com.saqz.groups.resources.expense_total_active
import br.com.saqz.groups.resources.expense_totals
import br.com.saqz.groups.resources.expense_value
import br.com.saqz.groups.resources.expense_void
import br.com.saqz.groups.resources.expense_void_body
import br.com.saqz.groups.resources.expense_void_confirm
import br.com.saqz.groups.resources.expense_void_title
import br.com.saqz.groups.resources.finance_brl_helper
import br.com.saqz.groups.resources.finance_paid
import br.com.saqz.groups.resources.finance_pending
import br.com.saqz.groups.resources.game_detail_dismiss
import org.jetbrains.compose.resources.stringResource

object ExpenseTags {
    const val Create = "expense-create"
    const val Description = "expense-description"
    const val Amount = "expense-amount"
    const val Date = "expense-date"
    const val Custom = "expense-custom"
    const val Notes = "expense-notes"
    const val Save = "expense-save"
    const val VoidConfirm = "expense-void-confirm"
    const val Retry = "expense-retry"
    const val Totals = "expense-totals"

    fun category(value: ExpenseCategory) = "expense-category-${value.name}"
    fun edit(id: String) = "expense-edit-$id"
    fun void(id: String) = "expense-void-$id"
}

internal val ExpenseMinimumTouchTargetKey = SemanticsPropertyKey<Boolean>("ExpenseMinimumTouchTarget")
internal val ExpenseActionOrderKey = SemanticsPropertyKey<Int>("ExpenseActionOrder")
internal var SemanticsPropertyReceiver.expenseMinimumTouchTarget by ExpenseMinimumTouchTargetKey
internal var SemanticsPropertyReceiver.expenseActionOrder by ExpenseActionOrderKey

@Composable
fun ExpenseScreen(state: ExpenseState, onIntent: (ExpenseIntent) -> Unit) {
    if (!state.routeAvailable) return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.sectionVerticalPadding),
    ) {
        Text(
            stringResource(Res.string.expense_title),
            style = SaqzTheme.typography.lead,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.expense_manual_notice), color = SaqzTheme.colors.textSecondary)
        if (state.isLoading) {
            SaqzLoadingState()
        } else {
            state.totals?.let { ExpenseTotals(it) }
            SaqzButton(
                stringResource(Res.string.expense_create),
                { onIntent(ExpenseIntent.OpenCreate) },
                Modifier.expenseAction(1).testTag(ExpenseTags.Create),
            )
            state.draft?.let { ExpenseFormPanel(it, state, onIntent) }
            state.expenses.forEach { ExpenseCard(it, onIntent) }
            state.lastAuditOutcome?.let { Text(it, color = SaqzTheme.colors.textSecondary) }
            state.error?.let {
                Text(
                    stringResource(
                        if (it == ExpenseError.CONFLICT) Res.string.expense_conflict else Res.string.expense_error,
                    ),
                    color = SaqzTheme.colors.errorForeground,
                )
            }
            if (state.retryAvailable) {
                SaqzButton(
                    stringResource(Res.string.action_retry),
                    { onIntent(ExpenseIntent.Retry) },
                    Modifier.expenseAction(99).testTag(ExpenseTags.Retry),
                    SaqzButtonVariant.Secondary,
                )
            }
        }
    }
    state.pendingVoid?.let { expense ->
        SaqzDialog(
            stringResource(Res.string.expense_void_title),
            { onIntent(ExpenseIntent.DismissVoid) },
            primaryAction = {
                SaqzButton(
                    stringResource(Res.string.expense_void_confirm),
                    { onIntent(ExpenseIntent.ConfirmVoid) },
                    Modifier.expenseAction(1).testTag(ExpenseTags.VoidConfirm),
                    SaqzButtonVariant.Destructive,
                    loading = state.isMutating,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
                Text(
                    stringResource(Res.string.expense_void_body, expense.description),
                    color = SaqzTheme.colors.textSecondary,
                )
                SaqzButton(
                    stringResource(Res.string.game_detail_dismiss),
                    { onIntent(ExpenseIntent.DismissVoid) },
                    Modifier.expenseAction(2),
                    SaqzButtonVariant.Secondary,
                )
            }
        }
    }
}

@Composable
private fun ExpenseTotals(value: FinanceTotals) {
    SaqzCard(Modifier.fillMaxWidth().testTag(ExpenseTags.Totals)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            Text(
                stringResource(Res.string.expense_totals),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.expense_total_active, formatBrl(value.activeExpenseCents)),
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                stringResource(Res.string.finance_pending, formatBrl(value.pendingChargeCents)),
                color = SaqzTheme.colors.textSecondary,
            )
            Text(
                stringResource(Res.string.finance_paid, formatBrl(value.paidChargeCents)),
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ExpenseFormPanel(draft: ExpenseDraft, state: ExpenseState, onIntent: (ExpenseIntent) -> Unit) {
    var form by remember(draft.commandKey) { mutableStateOf(draft.form) }
    var description by remember(draft.commandKey) { mutableStateOf(TextFieldValue(draft.form.description)) }
    var amount by remember(draft.commandKey) { mutableStateOf(TextFieldValue(draft.form.amountBrl)) }
    var date by remember(draft.commandKey) { mutableStateOf(TextFieldValue(draft.form.expenseDate)) }
    var custom by remember(draft.commandKey) { mutableStateOf(TextFieldValue(draft.form.customCategory)) }
    var notes by remember(draft.commandKey) { mutableStateOf(TextFieldValue(draft.form.notes)) }

    fun changed(value: ExpenseForm) {
        form = value
        onIntent(ExpenseIntent.UpdateForm(value))
    }

    SaqzCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            Text(
                stringResource(
                    if (draft.expenseId == null) Res.string.expense_form_create else Res.string.expense_form_edit,
                ),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            SaqzInput(
                description,
                {
                    description = it.copy(text = it.text.take(160))
                    changed(form.copy(description = description.text))
                },
                stringResource(Res.string.expense_description),
                Modifier.testTag(ExpenseTags.Description),
                errorText = state.fieldErrors["description"]?.firstOrNull(),
            )
            SaqzInput(
                amount,
                {
                    amount = it.copy(text = it.text.take(12))
                    changed(form.copy(amountBrl = amount.text))
                },
                stringResource(Res.string.expense_amount),
                Modifier.testTag(ExpenseTags.Amount),
                helperText = stringResource(Res.string.finance_brl_helper),
                errorText = state.fieldErrors["amountBrl"]?.firstOrNull(),
            )
            SaqzInput(
                date,
                {
                    date = it.copy(text = it.text.take(10))
                    changed(form.copy(expenseDate = date.text))
                },
                stringResource(Res.string.expense_date),
                Modifier.testTag(ExpenseTags.Date),
                errorText = state.fieldErrors["expenseDate"]?.firstOrNull(),
            )
            Text(stringResource(Res.string.expense_category), color = SaqzTheme.colors.textPrimary)
            ExpenseCategory.entries.forEachIndexed { index, category ->
                SaqzButton(
                    (if (form.category == category) "✓ " else "") + category.label(),
                    {
                        if (category != ExpenseCategory.Other) custom = TextFieldValue()
                        changed(
                            form.copy(
                                category = category,
                                customCategory = if (category == ExpenseCategory.Other) custom.text else "",
                            ),
                        )
                    },
                    Modifier.expenseAction(index + 1).testTag(ExpenseTags.category(category)),
                    SaqzButtonVariant.Secondary,
                )
            }
            if (form.category == ExpenseCategory.Other) {
                SaqzInput(
                    custom,
                    {
                        custom = it.copy(text = it.text.take(40))
                        changed(form.copy(customCategory = custom.text))
                    },
                    stringResource(Res.string.expense_custom_category),
                    Modifier.testTag(ExpenseTags.Custom),
                    errorText = state.fieldErrors["customCategory"]?.firstOrNull(),
                )
            }
            SaqzInput(
                notes,
                {
                    notes = it.copy(text = it.text.take(500))
                    changed(form.copy(notes = notes.text))
                },
                stringResource(Res.string.expense_notes),
                Modifier.testTag(ExpenseTags.Notes),
                errorText = state.fieldErrors["notes"]?.firstOrNull(),
            )
            SaqzButton(
                stringResource(Res.string.expense_save),
                { onIntent(ExpenseIntent.Submit) },
                Modifier.expenseAction(10).testTag(ExpenseTags.Save),
                loading = state.isMutating,
            )
        }
    }
}

@Composable
private fun ExpenseCard(expense: Expense, onIntent: (ExpenseIntent) -> Unit) {
    SaqzCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            Text(
                expense.description,
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                stringResource(Res.string.expense_value, formatBrl(expense.amountCents)),
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                stringResource(
                    Res.string.expense_date_value,
                    formatLocalDatePtBrString(expense.expenseDate),
                ),
                color = SaqzTheme.colors.textSecondary,
            )
            Text(
                expense.category.label() + (expense.customCategory?.let { " • $it" } ?: ""),
                color = SaqzTheme.colors.textSecondary,
            )
            expense.notes?.let { Text(it, color = SaqzTheme.colors.textSecondary) }
            SaqzBadge(expense.status.label(), SaqzBadgeVariant.Neutral)
            expense.audit.lastOrNull()?.let {
                Text(
                    stringResource(Res.string.expense_audit, it.action.label(), it.occurredAt),
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            if (expense.status == ExpenseStatus.Active) {
                SaqzButton(
                    stringResource(Res.string.expense_edit),
                    { onIntent(ExpenseIntent.OpenEdit(expense.id)) },
                    Modifier.expenseAction(1).testTag(ExpenseTags.edit(expense.id)),
                    SaqzButtonVariant.Secondary,
                )
                SaqzButton(
                    stringResource(Res.string.expense_void),
                    { onIntent(ExpenseIntent.RequestVoid(expense.id)) },
                    Modifier.expenseAction(2).testTag(ExpenseTags.void(expense.id)),
                    SaqzButtonVariant.Destructive,
                )
            }
        }
    }
}

@Composable
private fun ExpenseCategory.label() = stringResource(
    when (this) {
        ExpenseCategory.Venue -> Res.string.expense_category_venue
        ExpenseCategory.Equipment -> Res.string.expense_category_equipment
        ExpenseCategory.Referee -> Res.string.expense_category_referee
        ExpenseCategory.Other -> Res.string.expense_category_other
    },
)

@Composable
private fun ExpenseStatus.label() = stringResource(
    if (this == ExpenseStatus.Active) Res.string.expense_status_active else Res.string.expense_status_voided,
)

@Composable
private fun ExpenseAction.label() = stringResource(
    when (this) {
        ExpenseAction.Created -> Res.string.expense_action_created
        ExpenseAction.Edited -> Res.string.expense_action_edited
        ExpenseAction.Voided -> Res.string.expense_action_voided
    },
)

private fun Modifier.expenseAction(order: Int) =
    fillMaxWidth()
        .heightIn(min = 48.dp)
        .semantics {
            expenseMinimumTouchTarget = true
            expenseActionOrder = order
        }
