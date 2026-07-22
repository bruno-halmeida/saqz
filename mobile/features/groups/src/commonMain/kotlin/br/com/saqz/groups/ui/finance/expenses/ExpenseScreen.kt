package br.com.saqz.groups.ui.finance.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.formatting.formatLocalDatePtBrString
import br.com.saqz.designsystem.component.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.finance.*
import br.com.saqz.groups.presentation.finance.expenses.*
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

object ExpenseTags{const val Create="expense-create";const val Description="expense-description";const val Amount="expense-amount";const val Date="expense-date";const val Custom="expense-custom";const val Notes="expense-notes";const val Save="expense-save";const val VoidConfirm="expense-void-confirm";const val Retry="expense-retry";const val Totals="expense-totals";fun category(value:ExpenseCategoryDto)="expense-category-${value.name}";fun edit(id:String)="expense-edit-$id";fun void(id:String)="expense-void-$id"}
internal val ExpenseMinimumTouchTargetKey=SemanticsPropertyKey<Boolean>("ExpenseMinimumTouchTarget")
internal val ExpenseActionOrderKey=SemanticsPropertyKey<Int>("ExpenseActionOrder")
internal var SemanticsPropertyReceiver.expenseMinimumTouchTarget by ExpenseMinimumTouchTargetKey
internal var SemanticsPropertyReceiver.expenseActionOrder by ExpenseActionOrderKey

@Composable fun ExpenseScreen(state:ExpenseState,onIntent:(ExpenseIntent)->Unit){
    if(!state.routeAvailable)return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.sectionVerticalPadding)){
        Text(stringResource(Res.string.expense_title),style=SaqzTheme.typography.lead,color=SaqzTheme.colors.textPrimary,modifier=Modifier.semantics{heading()})
        Text(stringResource(Res.string.expense_manual_notice),color=SaqzTheme.colors.textSecondary)
        if(state.isLoading)SaqzLoadingState() else {
            state.totals?.let{ExpenseTotals(it)}
            SaqzButton(stringResource(Res.string.expense_create),{onIntent(ExpenseIntent.OpenCreate)},Modifier.expenseAction(1).testTag(ExpenseTags.Create))
            state.draft?.let{ExpenseFormPanel(it,state,onIntent)}
            state.expenses.forEach{ExpenseCard(it,onIntent)}
            state.lastAuditOutcome?.let{Text(it,color=SaqzTheme.colors.textSecondary)}
            state.error?.let{Text(stringResource(if(it==ExpenseError.CONFLICT)Res.string.expense_conflict else Res.string.expense_error),color=SaqzTheme.colors.errorForeground)}
            if(state.retryAvailable)SaqzButton(stringResource(Res.string.action_retry),{onIntent(ExpenseIntent.Retry)},Modifier.expenseAction(99).testTag(ExpenseTags.Retry),SaqzButtonVariant.Secondary)
        }
    }
    state.pendingVoid?.let{expense->SaqzDialog(stringResource(Res.string.expense_void_title),{onIntent(ExpenseIntent.DismissVoid)},primaryAction={SaqzButton(stringResource(Res.string.expense_void_confirm),{onIntent(ExpenseIntent.ConfirmVoid)},Modifier.expenseAction(1).testTag(ExpenseTags.VoidConfirm),SaqzButtonVariant.Destructive,loading=state.isMutating)}){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){Text(stringResource(Res.string.expense_void_body,expense.description),color=SaqzTheme.colors.textSecondary);SaqzButton(stringResource(Res.string.game_detail_dismiss),{onIntent(ExpenseIntent.DismissVoid)},Modifier.expenseAction(2),SaqzButtonVariant.Secondary)}}}
}

@Composable private fun ExpenseTotals(value:FinanceTotalsDto){SaqzCard(Modifier.fillMaxWidth().testTag(ExpenseTags.Totals)){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){Text(stringResource(Res.string.expense_totals),style=SaqzTheme.typography.body,color=SaqzTheme.colors.textPrimary,modifier=Modifier.semantics{heading()});Text(stringResource(Res.string.expense_total_active,formatBrl(value.activeExpenseCents)),color=SaqzTheme.colors.textPrimary);Text(stringResource(Res.string.finance_pending,formatBrl(value.pendingChargeCents)),color=SaqzTheme.colors.textSecondary);Text(stringResource(Res.string.finance_paid,formatBrl(value.paidChargeCents)),color=SaqzTheme.colors.textSecondary)}}}

@Composable private fun ExpenseFormPanel(draft:ExpenseDraft,state:ExpenseState,onIntent:(ExpenseIntent)->Unit){
    var form by remember(draft.commandKey){mutableStateOf(draft.form)}
    var description by remember(draft.commandKey){mutableStateOf(TextFieldValue(draft.form.description))}
    var amount by remember(draft.commandKey){mutableStateOf(TextFieldValue(draft.form.amountBrl))}
    var date by remember(draft.commandKey){mutableStateOf(TextFieldValue(draft.form.expenseDate))}
    var custom by remember(draft.commandKey){mutableStateOf(TextFieldValue(draft.form.customCategory))}
    var notes by remember(draft.commandKey){mutableStateOf(TextFieldValue(draft.form.notes))}
    fun changed(value:ExpenseForm){form=value;onIntent(ExpenseIntent.UpdateForm(value))}
    SaqzCard(Modifier.fillMaxWidth()){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){
        Text(stringResource(if(draft.expenseId==null)Res.string.expense_form_create else Res.string.expense_form_edit),style=SaqzTheme.typography.body,color=SaqzTheme.colors.textPrimary,modifier=Modifier.semantics{heading()})
        SaqzInput(description,{description=it.copy(text=it.text.take(160));changed(form.copy(description=description.text))},stringResource(Res.string.expense_description),Modifier.testTag(ExpenseTags.Description),errorText=state.fieldErrors["description"]?.firstOrNull())
        SaqzInput(amount,{amount=it.copy(text=it.text.take(12));changed(form.copy(amountBrl=amount.text))},stringResource(Res.string.expense_amount),Modifier.testTag(ExpenseTags.Amount),helperText=stringResource(Res.string.finance_brl_helper),errorText=state.fieldErrors["amountBrl"]?.firstOrNull())
        SaqzInput(date,{date=it.copy(text=it.text.take(10));changed(form.copy(expenseDate=date.text))},stringResource(Res.string.expense_date),Modifier.testTag(ExpenseTags.Date),errorText=state.fieldErrors["expenseDate"]?.firstOrNull())
        Text(stringResource(Res.string.expense_category),color=SaqzTheme.colors.textPrimary)
        ExpenseCategoryDto.entries.forEachIndexed{i,category->SaqzButton((if(form.category==category)"✓ " else "")+category.label(),{if(category!=ExpenseCategoryDto.OTHER)custom=TextFieldValue();changed(form.copy(category=category,customCategory=if(category==ExpenseCategoryDto.OTHER)custom.text else ""))},Modifier.expenseAction(i+1).testTag(ExpenseTags.category(category)),SaqzButtonVariant.Secondary)}
        if(form.category==ExpenseCategoryDto.OTHER)SaqzInput(custom,{custom=it.copy(text=it.text.take(40));changed(form.copy(customCategory=custom.text))},stringResource(Res.string.expense_custom_category),Modifier.testTag(ExpenseTags.Custom),errorText=state.fieldErrors["customCategory"]?.firstOrNull())
        SaqzInput(notes,{notes=it.copy(text=it.text.take(500));changed(form.copy(notes=notes.text))},stringResource(Res.string.expense_notes),Modifier.testTag(ExpenseTags.Notes),errorText=state.fieldErrors["notes"]?.firstOrNull())
        SaqzButton(stringResource(Res.string.expense_save),{onIntent(ExpenseIntent.Submit)},Modifier.expenseAction(10).testTag(ExpenseTags.Save),loading=state.isMutating)
    }}
}

@Composable private fun ExpenseCard(expense:ExpenseDto,onIntent:(ExpenseIntent)->Unit){SaqzCard(Modifier.fillMaxWidth()){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){Text(expense.description,style=SaqzTheme.typography.body,color=SaqzTheme.colors.textPrimary);Text(stringResource(Res.string.expense_value,formatBrl(expense.amountCents)),color=SaqzTheme.colors.textPrimary);Text(stringResource(Res.string.expense_date_value,formatLocalDatePtBrString(expense.expenseDate)),color=SaqzTheme.colors.textSecondary);Text(expense.category.label()+(expense.customCategory?.let{" • $it"}?:""),color=SaqzTheme.colors.textSecondary);expense.notes?.let{Text(it,color=SaqzTheme.colors.textSecondary)};SaqzBadge(expense.status.label(),SaqzBadgeVariant.Neutral);expense.events.lastOrNull()?.let{Text(stringResource(Res.string.expense_audit,it.action.label(),it.occurredAt),color=SaqzTheme.colors.textSecondary)};if(expense.status==ExpenseStatusDto.ACTIVE){SaqzButton(stringResource(Res.string.expense_edit),{onIntent(ExpenseIntent.OpenEdit(expense.id))},Modifier.expenseAction(1).testTag(ExpenseTags.edit(expense.id)),SaqzButtonVariant.Secondary);SaqzButton(stringResource(Res.string.expense_void),{onIntent(ExpenseIntent.RequestVoid(expense.id))},Modifier.expenseAction(2).testTag(ExpenseTags.void(expense.id)),SaqzButtonVariant.Destructive)}}}}

@Composable private fun ExpenseCategoryDto.label()=stringResource(when(this){ExpenseCategoryDto.VENUE->Res.string.expense_category_venue;ExpenseCategoryDto.EQUIPMENT->Res.string.expense_category_equipment;ExpenseCategoryDto.REFEREE->Res.string.expense_category_referee;ExpenseCategoryDto.OTHER->Res.string.expense_category_other})
@Composable private fun ExpenseStatusDto.label()=stringResource(if(this==ExpenseStatusDto.ACTIVE)Res.string.expense_status_active else Res.string.expense_status_voided)
@Composable private fun ExpenseActionDto.label()=stringResource(when(this){ExpenseActionDto.CREATED->Res.string.expense_action_created;ExpenseActionDto.EDITED->Res.string.expense_action_edited;ExpenseActionDto.VOIDED->Res.string.expense_action_voided})
private fun Modifier.expenseAction(order:Int)=fillMaxWidth().heightIn(min=48.dp).semantics{expenseMinimumTouchTarget=true;expenseActionOrder=order}
