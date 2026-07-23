package br.com.saqz.groups.ui.finance.charges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
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
import br.com.saqz.core.common.formatting.formatMonthPtBrString
import br.com.saqz.designsystem.component.*
import br.com.saqz.designsystem.text.UiText
import br.com.saqz.designsystem.text.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.presentation.finance.charges.*
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

object FinanceTags {
    const val Month = "finance-month"
    const val Amount = "finance-amount"
    const val DueDate = "finance-due-date"
    const val Note = "finance-note"
    const val Review = "finance-review"
    const val Generate = "finance-generate"
    const val Retry = "finance-retry"
    const val Totals = "finance-totals"

    fun member(id: String) = "finance-member-$id"

    fun paid(id: String) = "finance-paid-$id"

    fun waived(id: String) = "finance-waived-$id"

    fun cancelled(id: String) = "finance-cancelled-$id"
}

internal val FinanceMinimumTouchTargetKey = SemanticsPropertyKey<Boolean>("FinanceMinimumTouchTarget")
internal val FinanceActionOrderKey = SemanticsPropertyKey<Int>("FinanceActionOrder")
internal var SemanticsPropertyReceiver.financeMinimumTouchTarget by FinanceMinimumTouchTargetKey
internal var SemanticsPropertyReceiver.financeActionOrder by FinanceActionOrderKey

@Composable fun FinanceScreen(
    state: FinanceState,
    onIntent: (FinanceIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.sectionVerticalPadding),
    ) {
        Text(
            stringResource(
                if (state.organizer) Res.string.finance_title_organizer else Res.string.finance_title_athlete,
            ),
            style = SaqzTheme.typography.lead,
            color = SaqzTheme.colors.textPrimary,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
        Text(state.manualTrackingNotice, color = SaqzTheme.colors.textSecondary)
        if (state.isLoading) {
            SaqzLoadingState()
        } else {
            if (state.organizer) {
                state.totals?.let { Totals(it) }
                MonthlyPanel(state, onIntent)
            }
            state.charges.forEach { ChargeCard(it, state.organizer, onIntent) }
            state.lastManualOutcome?.let { Text(it, color = SaqzTheme.colors.textSecondary) }
            state.error?.let {
                Text(
                    financeErrorLabel(it).asString(),
                    color = SaqzTheme.colors.errorForeground,
                )
            }
            if (state.retryAvailable) {
                SaqzButton(stringResource(Res.string.action_retry), {
                    onIntent(FinanceIntent.Retry)
                }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(FinanceTags.Retry), SaqzButtonVariant.Secondary)
            }
        }
    }
}

@Composable private fun Totals(value: ChargeTotalsState) {
    SaqzCard(Modifier.fillMaxWidth().testTag(FinanceTags.Totals)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            Text(
                stringResource(
                    Res.string.finance_totals,
                ),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textPrimary,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Text(stringResource(Res.string.finance_pending, formatBrl(value.pendingCents)), color = SaqzTheme.colors.textPrimary)
            Text(stringResource(Res.string.finance_paid, formatBrl(value.paidCents)), color = SaqzTheme.colors.textSecondary)
            Text(stringResource(Res.string.finance_waived, formatBrl(value.waivedCents)), color = SaqzTheme.colors.textSecondary)
            Text(stringResource(Res.string.finance_cancelled, formatBrl(value.cancelledCents)), color = SaqzTheme.colors.textSecondary)
        }
    }
}

@Composable private fun MonthlyPanel(
    state: FinanceState,
    onIntent: (FinanceIntent) -> Unit,
) {
    val initial = state.monthlyDraft
    var month by remember(state.groupId) { mutableStateOf(TextFieldValue(initial?.month.orEmpty())) }
    var amount by remember(state.groupId) { mutableStateOf(TextFieldValue(initial?.amountBrl.orEmpty())) }
    var due by remember(state.groupId) { mutableStateOf(TextFieldValue(initial?.dueDate.orEmpty())) }
    var selected by remember(state.groupId) { mutableStateOf(initial?.selectedMemberIds.orEmpty()) }

    fun update() {
        onIntent(FinanceIntent.UpdateMonthly(month.text, amount.text, due.text, selected))
    }
    SaqzCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            Text(
                stringResource(
                    Res.string.finance_monthly_title,
                ),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textPrimary,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            SaqzInput(
                month,
                {
                    month = it.copy(text = it.text.take(7))
                    update()
                },
                stringResource(
                    Res.string.finance_month,
                ),
                Modifier.testTag(FinanceTags.Month),
                errorText = state.fieldErrors["month"]?.firstOrNull(),
            )
            SaqzInput(
                amount,
                {
                    amount = it.copy(text = it.text.take(12))
                    update()
                },
                stringResource(
                    Res.string.finance_amount,
                ),
                Modifier.testTag(
                    FinanceTags.Amount,
                ),
                helperText = stringResource(Res.string.finance_brl_helper),
                errorText = state.fieldErrors["amountBrl"]?.firstOrNull(),
            )
            SaqzInput(
                due,
                {
                    due = it.copy(text = it.text.take(10))
                    update()
                },
                stringResource(
                    Res.string.finance_due_date,
                ),
                Modifier.testTag(FinanceTags.DueDate),
                errorText = state.fieldErrors["dueDate"]?.firstOrNull(),
            )
            Text(stringResource(Res.string.finance_members), color = SaqzTheme.colors.textPrimary)
            state.members.forEach { member ->
                val included =
                    member.userId in selected
                SaqzButton((if (included) "✓ " else "") + member.displayName, {
                    selected =
                        if (included) selected - member.userId else selected + member.userId
                    ;update()
                }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(FinanceTags.member(member.userId)), SaqzButtonVariant.Secondary)
            }
            SaqzButton(stringResource(Res.string.finance_review), {
                onIntent(FinanceIntent.ReviewMonthly)
            }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(FinanceTags.Review), enabled = !state.isMutating)
            if (state.monthlyDraft?.reviewed ==
                true
            ) {
                Text(
                    stringResource(
                        Res.string.finance_review_summary,
                        state.monthlyDraft.month,
                        state.monthlyDraft.amountBrl,
                        state.monthlyDraft.dueDate,
                        state.monthlyDraft.selectedMemberIds.size,
                    ),
                    color = SaqzTheme.colors.textSecondary,
                )
                SaqzButton(stringResource(Res.string.finance_generate), {
                    onIntent(FinanceIntent.GenerateMonthly)
                }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(FinanceTags.Generate), loading = state.isMutating)
            }
        }
    }
}

@Composable private fun ChargeCard(
    charge: Charge,
    organizer: Boolean,
    onIntent: (FinanceIntent) -> Unit,
) {
    var note by remember(charge.id) { mutableStateOf(TextFieldValue()) }
    SaqzCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            Text(charge.subject(), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
            if (organizer) Text(stringResource(Res.string.finance_member_value, charge.memberId), color = SaqzTheme.colors.textSecondary)
            Text(stringResource(Res.string.finance_charge_amount, formatBrl(charge.amountCents)), color = SaqzTheme.colors.textPrimary)
            Text(
                stringResource(Res.string.finance_charge_due, formatLocalDatePtBrString(charge.dueDate)),
                color = SaqzTheme.colors.textSecondary,
            )
            SaqzBadge(charge.status.label(), SaqzBadgeVariant.Neutral)
            if (charge.reviewRequired) Text(stringResource(Res.string.finance_review_required), color = SaqzTheme.colors.textSecondary)
            charge.audit.lastOrNull()?.let {
                Text(stringResource(Res.string.finance_audit, it.newStatus.label(), it.occurredAt), color = SaqzTheme.colors.textSecondary)
            }
            if (organizer &&
                charge.status == ChargeStatus.Pending
            ) {
                SaqzInput(
                    note,
                    { note = it.copy(text = it.text.take(500)) },
                    stringResource(Res.string.finance_note),
                    Modifier.testTag(FinanceTags.Note),
                )
                SaqzButton(stringResource(Res.string.finance_record_paid), {
                    onIntent(FinanceIntent.UpdateStatus(charge.id, ChargeStatus.Paid, note.text))
                }, Modifier.financeAction(1).testTag(FinanceTags.paid(charge.id)))
                SaqzButton(stringResource(Res.string.finance_record_waived), {
                    onIntent(FinanceIntent.UpdateStatus(charge.id, ChargeStatus.Waived, note.text))
                }, Modifier.financeAction(2).testTag(FinanceTags.waived(charge.id)), SaqzButtonVariant.Secondary)
                SaqzButton(stringResource(Res.string.finance_record_cancelled), {
                    onIntent(FinanceIntent.UpdateStatus(charge.id, ChargeStatus.Cancelled, note.text))
                }, Modifier.financeAction(3).testTag(FinanceTags.cancelled(charge.id)), SaqzButtonVariant.Destructive)
            }
        }
    }
}

@Composable private fun Charge.subject() =
    when (kind) {
        ChargeKind.Game -> stringResource(Res.string.finance_kind_game, gameId.orEmpty())
        ChargeKind.Monthly -> stringResource(Res.string.finance_kind_month, month?.let(::formatMonthPtBrString).orEmpty())
    }

@Composable private fun ChargeStatus.label() =
    stringResource(
        when (this) {
            ChargeStatus.Pending -> Res.string.finance_status_pending
            ChargeStatus.Paid -> Res.string.finance_status_paid
            ChargeStatus.Waived -> Res.string.finance_status_waived
            ChargeStatus.Cancelled -> Res.string.finance_status_cancelled
        },
    )

private fun Modifier.financeAction(order: Int) =
    fillMaxWidth().heightIn(min = 48.dp).semantics {
        financeMinimumTouchTarget = true
        financeActionOrder =
            order
    }

private fun financeErrorLabel(error: FinanceError): UiText = UiText.Res(
    if (error == FinanceError.CONFLICT) Res.string.finance_conflict else Res.string.finance_error,
)
