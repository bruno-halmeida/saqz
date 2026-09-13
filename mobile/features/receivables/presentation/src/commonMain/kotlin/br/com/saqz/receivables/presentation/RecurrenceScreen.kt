package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.formatting.formatInstantDateTimePtBr
import br.com.saqz.core.common.formatting.formatLocalDatePtBrString
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource

object RecurrenceTags {
    const val Screen = "recurrence"
    const val DueDate = "recurrence-due-date"
    const val Preview = "recurrence-preview"
    const val Accept = "recurrence-accept"
    const val Submit = "recurrence-submit"
    const val Cancel = "recurrence-cancel"
    const val Checkout = "recurrence-checkout"
    const val Status = "recurrence-status"
    const val Terms = "recurrence-terms"
    fun method(method: ReceiptMethod) = "recurrence-method-${method.name}"
}

@Composable
fun RecurrenceScreen(state: RecurrenceState, onIntent: (RecurrenceIntent) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier) = PaymentPage(stringResource(Res.string.recurrence_title), RecurrenceTags.Screen,
    { if (!state.pending) onBack() }, modifier) {
    Text(stringResource(Res.string.recurrence_intro))
    Text(stringResource(Res.string.recurrence_pix_manual))
    if (state.loading) SaqzSpinner()
    state.error?.let { PaymentError(it) }
    if (state.pending) Text(stringResource(Res.string.recurrence_pending))
    state.recurrence?.let { RecurrenceSummary(it) }
    if (state.canEditReview) RecurrenceReviewForm(state, onIntent)
    if (state.canOpenCheckout) SaqzButton(stringResource(Res.string.recurrence_checkout),
        { onIntent(RecurrenceIntent.OpenCheckout) }, fullWidth = true, modifier = Modifier.testTag(RecurrenceTags.Checkout))
    if (state.checkoutOpenFailed) Text(stringResource(Res.string.payment_open_failed))
    if (state.canCancel) SaqzButton(stringResource(Res.string.recurrence_cancel), { onIntent(RecurrenceIntent.Cancel) },
        fullWidth = true, variant = SaqzButtonVariant.Secondary, modifier = Modifier.testTag(RecurrenceTags.Cancel))
    SaqzButton(stringResource(Res.string.payment_refresh), { onIntent(RecurrenceIntent.Refresh) },
        enabled = !state.loading && state.error != ReceiptError.SIGNED_OUT, fullWidth = true,
        variant = SaqzButtonVariant.Secondary)
}

@Composable
private fun RecurrenceSummary(recurrence: PaymentRecurrence) = SaqzCard {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(stringResource(Res.string.recurrence_reference, recurrence.id.takeLast(8)))
        Text(stringResource(recurrenceStatusText(recurrence.status)), Modifier.testTag(RecurrenceTags.Status))
        Text(stringResource(Res.string.recurrence_method,
            stringResource(if (recurrence.method == ReceiptMethod.PIX) Res.string.receipt_pix else Res.string.receipt_card)))
        Text(stringResource(Res.string.payment_due, formatLocalDatePtBrString(recurrence.firstDueDate)))
        Text(stringResource(Res.string.receipt_base, formatBrl(recurrence.baseCents)))
        Text(stringResource(Res.string.receipt_fees, formatBrl(recurrence.feesCents)))
        Text(stringResource(Res.string.receipt_total, formatBrl(recurrence.totalCents)))
        recurrence.cutoffAt?.let { cutoff ->
            Text(stringResource(Res.string.recurrence_cutoff, formatInstantDateTimePtBr(cutoff) ?: cutoff))
        }
    }
}

@Composable
private fun RecurrenceReviewForm(state: RecurrenceState, onIntent: (RecurrenceIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        if (state.canResume) Text(stringResource(Res.string.recurrence_resume_requires_acceptance))
        ReceiptMethod.entries.forEach { method ->
            SaqzSwitch(checked = state.method == method, onCheckedChange = { onIntent(RecurrenceIntent.Method(method)) },
                label = stringResource(if (method == ReceiptMethod.PIX) Res.string.receipt_pix else Res.string.receipt_card),
                enabled = !state.loading, modifier = Modifier.testTag(RecurrenceTags.method(method)))
        }
        SaqzInput(state.firstDueDate, { onIntent(RecurrenceIntent.DueDate(it)) },
            stringResource(Res.string.recurrence_due_date), helperText = stringResource(Res.string.recurrence_due_date_help),
            modifier = Modifier.testTag(RecurrenceTags.DueDate))
        SaqzButton(stringResource(Res.string.recurrence_review), { onIntent(RecurrenceIntent.Preview) },
            enabled = state.canPreview, fullWidth = true, modifier = Modifier.testTag(RecurrenceTags.Preview))
        state.review?.let { review ->
            RecurrenceReviewSummary(review)
            state.terms?.let { terms ->
                Text(stringResource(Res.string.receipt_terms, terms.version))
                Text(terms.content, Modifier.testTag(RecurrenceTags.Terms))
            }
            SaqzInput(state.name, { onIntent(RecurrenceIntent.Name(it)) }, stringResource(Res.string.payment_name),
                enabled = state.canAccept)
            SaqzInput(state.document, { onIntent(RecurrenceIntent.Document(it)) }, stringResource(Res.string.payment_document),
                enabled = state.canAccept, keyboardType = KeyboardType.Number,
                helperText = stringResource(Res.string.payment_document_help))
            SaqzSwitch(checked = state.accepted, onCheckedChange = { onIntent(RecurrenceIntent.Accept(it)) },
                label = stringResource(Res.string.recurrence_accept, review.termsVersion, formatBrl(review.totalCents)),
                enabled = state.canAccept, modifier = Modifier.testTag(RecurrenceTags.Accept))
            SaqzButton(stringResource(if (state.canResume) Res.string.recurrence_resume else Res.string.recurrence_authorize),
                { onIntent(RecurrenceIntent.Submit) }, enabled = state.canSubmit, fullWidth = true,
                modifier = Modifier.testTag(RecurrenceTags.Submit))
        }
    }
}

@Composable
private fun RecurrenceReviewSummary(review: RecurrenceReview) = SaqzCard {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(stringResource(Res.string.recurrence_cycle_monthly))
        Text(stringResource(Res.string.receipt_base, formatBrl(review.baseCents)))
        Text(stringResource(Res.string.receipt_fees, formatBrl(review.feesCents)))
        Text(stringResource(Res.string.receipt_total, formatBrl(review.totalCents)))
        Text(stringResource(Res.string.recurrence_commission, formatBrl(review.commissionCents)))
        Text(stringResource(Res.string.recurrence_provider_fee, formatBrl(review.expectedProviderFeeCents)))
        Text(stringResource(Res.string.recurrence_net, formatBrl(review.expectedNetCents)))
    }
}

internal fun recurrenceStatusText(status: String) = when (status) {
    "AUTHORIZING" -> Res.string.recurrence_status_authorizing
    "ACTIVE" -> Res.string.recurrence_status_active
    "STOP_PENDING" -> Res.string.recurrence_status_stop_pending
    "STOPPED" -> Res.string.recurrence_status_stopped
    else -> Res.string.payment_status_unknown
}

@Preview @Composable private fun RecurrencePreview() = SaqzTheme {
    RecurrenceScreen(RecurrenceState("account", "group", loading = false, firstDueDate = "2026-10-10",
        review = RecurrenceReview("account", "group", "payer", ReceiptMethod.PIX, 10_000, 490, 10_490, 300, 190,
            10_000, "schedule", "receivables-2026-09", "2026-10-10", "MONTHLY", "a".repeat(64)),
        terms = ReceiptTerms("receivables-2026-09", "Termos publicados")), {}, {})
}
