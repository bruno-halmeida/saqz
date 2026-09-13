package br.com.saqz.receivables.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
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

object MemberPaymentTags {
    const val History = "member-payment-history"
    const val Screen = "member-payment"
    const val Accept = "member-payment-accept"
    const val Pay = "member-payment-pay"
    const val Copy = "member-payment-copy"
    const val Card = "member-payment-card"
    const val Refresh = "member-payment-refresh"
    const val Name = "member-payment-name"
    const val Document = "member-payment-document"
    const val Status = "member-payment-status"
    const val Terms = "member-payment-terms"
    const val Recurrence = "member-payment-recurrence"
    const val RenewalDate = "member-payment-renewal-date"
    const val Renew = "member-payment-renew"
    const val Export = "member-payment-export"
    fun method(method: ReceiptMethod) = "member-payment-method-${method.name}"
    fun order(id: String) = "member-payment-order-$id"
}

@Composable
fun MemberPaymentHistoryScreen(state: MemberPaymentHistoryState, onIntent: (MemberPaymentHistoryIntent) -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier) =
    PaymentPage(stringResource(Res.string.payment_history_title), MemberPaymentTags.History, onBack, modifier) {
    Text(stringResource(Res.string.payment_intro))
    if (state.loading) SaqzSpinner()
    state.error?.let { PaymentError(it) }
    if (!state.loading && state.error == null && state.orders.isEmpty()) Text(stringResource(Res.string.payment_empty))
    state.orders.forEach { order ->
        SaqzCard {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                OrderHeading(order)
                order.quotes.firstOrNull()?.let { Text(stringResource(Res.string.receipt_base, formatBrl(it.baseCents))) }
                Text(stringResource(paymentStatusText(order.status)))
                SaqzButton(stringResource(Res.string.payment_open), { onIntent(MemberPaymentHistoryIntent.Open(order.id)) },
                    modifier = Modifier.testTag(MemberPaymentTags.order(order.id)))
            }
        }
    }
    if (state.nextCursor != null) SaqzButton(stringResource(Res.string.payment_more), { onIntent(MemberPaymentHistoryIntent.More) },
        enabled = !state.loading, fullWidth = true)
    SaqzButton(stringResource(Res.string.receipt_refresh), { onIntent(MemberPaymentHistoryIntent.Refresh) },
        enabled = !state.loading && state.error != ReceiptError.SIGNED_OUT, fullWidth = true, variant = SaqzButtonVariant.Secondary)
}

@Composable
fun MemberPaymentScreen(state: MemberPaymentState, onIntent: (MemberPaymentIntent) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier, onOpenRecurrence: (String, String) -> Unit = { _, _ -> }) =
    PaymentPage(stringResource(Res.string.payment_title), MemberPaymentTags.Screen, { if (!state.pending) onBack() }, modifier) {
        if (state.loading) SaqzSpinner()
        state.error?.let { PaymentError(it) }
        if (state.pending) {
            Text(stringResource(Res.string.payment_pending))
            Text(stringResource(Res.string.payment_pending_restored))
            if (state.canReplay) SaqzButton(stringResource(Res.string.payment_replay), { onIntent(MemberPaymentIntent.Replay) },
                enabled = !state.loading, fullWidth = true)
        }
        state.detail?.let { detail ->
            OrderHeading(detail.order)
            SaqzButton(stringResource(Res.string.payment_recurrence),
                { onOpenRecurrence(detail.order.accountId, detail.order.groupId) }, fullWidth = true,
                variant = SaqzButtonVariant.Secondary, modifier = Modifier.testTag(MemberPaymentTags.Recurrence))
            Text(stringResource(paymentStatusText(displayPaymentStatus(state))), Modifier.testTag(MemberPaymentTags.Status),
                style = SaqzTheme.typography.body)
            if (state.canReview || state.method != null) PaymentReview(state, onIntent)
            state.instrument?.let { instrument ->
                QuoteSummary(instrument.quote)
                instrument.paymentId?.let { Text(stringResource(Res.string.payment_id, it)) }
                if (state.pixExpired) Text(stringResource(Res.string.payment_expired_help))
                if (state.canUseInstrument) PaymentInstrumentContent(state, instrument, onIntent)
                if (state.pixExpired && detail.order.canRenew(instrument, expired = true)) {
                    SaqzInput(state.renewalDueDate, { onIntent(MemberPaymentIntent.RenewalDueDate(it)) },
                        stringResource(Res.string.payment_renewal_date), helperText = stringResource(Res.string.recurrence_due_date_help),
                        modifier = Modifier.testTag(MemberPaymentTags.RenewalDate))
                    SaqzButton(stringResource(Res.string.payment_renew_pix), { onIntent(MemberPaymentIntent.RenewPix) },
                        enabled = state.canRenew, fullWidth = true, modifier = Modifier.testTag(MemberPaymentTags.Renew))
                }
            }
            if (state.canExportReceipt || state.receiptSharing) SaqzButton(stringResource(Res.string.payment_export_receipt),
                { onIntent(MemberPaymentIntent.ExportReceipt) }, enabled = state.canExportReceipt,
                fullWidth = true, modifier = Modifier.testTag(MemberPaymentTags.Export))
            if (state.receiptShared) Text(stringResource(Res.string.payment_export_shared))
            if (state.receiptShareFailed) Text(stringResource(Res.string.payment_export_failed))
        }
        if (state.openFailed) Text(stringResource(Res.string.payment_open_failed))
        SaqzButton(stringResource(Res.string.payment_refresh), { onIntent(MemberPaymentIntent.Refresh) },
            modifier = Modifier.testTag(MemberPaymentTags.Refresh), enabled = !state.loading && state.error != ReceiptError.SIGNED_OUT,
            fullWidth = true, variant = SaqzButtonVariant.Secondary)
    }

@Composable
private fun PaymentReview(state: MemberPaymentState, onIntent: (MemberPaymentIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
    Text(stringResource(Res.string.payment_choose))
    state.detail?.order?.quotes?.forEach { quote ->
        SaqzSwitch(checked = state.method == quote.method, onCheckedChange = { onIntent(MemberPaymentIntent.Method(quote.method)) },
            label = stringResource(if (quote.method == ReceiptMethod.PIX) Res.string.receipt_pix else Res.string.receipt_card),
            enabled = state.canReview, modifier = Modifier.testTag(MemberPaymentTags.method(quote.method)))
    }
    state.quote?.let { QuoteSummary(it) }
    SaqzInput(state.name, { onIntent(MemberPaymentIntent.Name(it)) }, stringResource(Res.string.payment_name),
        enabled = state.canReview, modifier = Modifier.testTag(MemberPaymentTags.Name))
    SaqzInput(state.document, { onIntent(MemberPaymentIntent.Document(it)) }, stringResource(Res.string.payment_document),
        enabled = state.canReview, modifier = Modifier.testTag(MemberPaymentTags.Document), keyboardType = KeyboardType.Number,
        helperText = stringResource(Res.string.payment_document_help))
    state.terms?.let {
        Text(stringResource(Res.string.receipt_terms, it.version))
        Text(it.content, Modifier.testTag(MemberPaymentTags.Terms))
    }
    SaqzSwitch(checked = state.accepted, onCheckedChange = { onIntent(MemberPaymentIntent.Accept(it)) },
        label = stringResource(Res.string.payment_accept), enabled = state.canAccept, modifier = Modifier.testTag(MemberPaymentTags.Accept))
    state.quote?.let { quote ->
        SaqzButton(stringResource(Res.string.payment_submit, formatBrl(quote.totalCents)), { onIntent(MemberPaymentIntent.Pay) },
            enabled = state.canPay, fullWidth = true, modifier = Modifier.testTag(MemberPaymentTags.Pay))
    }
    }
}

@Composable
private fun PaymentInstrumentContent(state: MemberPaymentState, instrument: MemberPaymentInstrument,
    onIntent: (MemberPaymentIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
    if (instrument.quote.method == ReceiptMethod.PIX) {
        instrument.pixImage?.let { PaymentQrImage(it) }
        instrument.expiresAt?.let { Text(stringResource(Res.string.payment_expires, formatInstantDateTimePtBr(it) ?: it)) }
        SaqzButton(stringResource(if (state.copied) Res.string.payment_copied else Res.string.payment_copy),
            { onIntent(MemberPaymentIntent.CopyPix) }, enabled = !instrument.pixPayload.isNullOrBlank(),
            fullWidth = true, modifier = Modifier.testTag(MemberPaymentTags.Copy))
    } else {
        Text(stringResource(Res.string.payment_card_help))
        SaqzButton(stringResource(Res.string.payment_card_open), { onIntent(MemberPaymentIntent.OpenCard) },
            fullWidth = true, modifier = Modifier.testTag(MemberPaymentTags.Card))
    }
    Text(stringResource(Res.string.payment_tracking))
    }
}

@Composable
private fun OrderHeading(order: MemberPaymentOrder) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
    Text(stringResource(Res.string.payment_reference, order.id.takeLast(8)), style = SaqzTheme.typography.body)
    Text(stringResource(Res.string.payment_due, formatLocalDatePtBrString(order.dueDate)))
    }
}
@Composable
internal fun QuoteSummary(quote: MemberPaymentQuote) = SaqzCard {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(stringResource(Res.string.receipt_base, formatBrl(quote.baseCents)))
        Text(stringResource(Res.string.receipt_fees, formatBrl(quote.feesCents)))
        Text(stringResource(Res.string.receipt_total, formatBrl(quote.totalCents)), style = SaqzTheme.typography.body)
    }
}
@Composable
internal fun PaymentPage(title: String, tag: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(tag)) {
        SaqzTopAppBar(title = title, onBack = onBack, modifier = Modifier.zIndex(1f))
        Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap), content = content)
    }
}
@Composable
internal fun PaymentError(error: ReceiptError) = Text(stringResource(when (error) {
    ReceiptError.DENIED -> Res.string.receipt_error_DENIED
    ReceiptError.STALE -> Res.string.receipt_error_STALE
    ReceiptError.INVALID -> Res.string.receipt_error_INVALID
    ReceiptError.UNAVAILABLE -> Res.string.receipt_error_UNAVAILABLE
    ReceiptError.NETWORK -> Res.string.receipt_error_NETWORK
    ReceiptError.UNCERTAIN -> Res.string.receipt_error_UNCERTAIN
    ReceiptError.SIGNED_OUT -> Res.string.receipt_error_SIGNED_OUT
}))
internal fun displayPaymentStatus(state: MemberPaymentState): String {
    val order = state.detail?.order?.status
    if (order in setOf("REFUNDED", "CHARGEBACK", "CANCELLED", "CANCEL_PENDING")) {
        return if (order == "CANCELLED") "ORDER_CANCELLED" else requireNotNull(order)
    }
    if (state.pixExpired && state.instrument?.status in setOf("ACTIVE", "EXPIRED")) return "EXPIRED"
    return state.instrument?.status ?: order.orEmpty()
}
internal fun paymentStatusText(status: String) = when (status) {
    "ISSUED" -> Res.string.payment_status_issued
    "ACTIVE" -> Res.string.payment_status_active
    "PAID", "CONFIRMED", "SETTLED", "AVAILABLE" -> Res.string.payment_status_confirmed
    "UNKNOWN", "CREATING", "RECOVERY_PENDING" -> Res.string.payment_status_pending
    "CANCEL_PENDING" -> Res.string.payment_status_cancel_pending
    "ORDER_CANCELLED" -> Res.string.payment_status_order_cancelled
    "CANCELLED" -> Res.string.payment_status_cancelled
    "EXPIRED" -> Res.string.payment_status_expired
    "REFUNDED" -> Res.string.payment_status_refunded
    "DISPUTED", "CHARGEBACK" -> Res.string.payment_status_disputed
    else -> Res.string.payment_status_unknown
}
@Preview @Composable private fun MemberHistoryPreview() = SaqzTheme {
    MemberPaymentHistoryScreen(MemberPaymentHistoryState(false), {}, {})
}
@Preview @Composable private fun MemberPaymentPreview() = SaqzTheme {
    MemberPaymentScreen(MemberPaymentState(false, error = ReceiptError.NETWORK), {}, {})
}
