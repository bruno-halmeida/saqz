package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzResult

data class PixRenewal(
    val orderId: String,
    val instrumentId: String,
    val providerPaymentId: String,
    val method: ReceiptMethod,
    val status: String,
    val baseCents: Long,
    val feesCents: Long,
    val totalCents: Long,
    val dueDate: String,
    val pixPayload: String,
    val pixImage: String?,
    val expiresAt: String,
) {
    fun validFor(order: MemberPaymentOrder, instrument: MemberPaymentInstrument): Boolean =
        orderId == order.id && instrumentId == instrument.id && providerPaymentId == instrument.paymentId &&
            method == ReceiptMethod.PIX && instrument.quote.method == ReceiptMethod.PIX && status == "ACTIVE" &&
            baseCents == instrument.quote.baseCents && feesCents == instrument.quote.feesCents &&
            totalCents == instrument.quote.totalCents && baseCents > 0 && feesCents >= 0 && totalCents > 0 &&
            totalCents - baseCents == feesCents && dueDate.isPaymentDate() &&
            pixPayload.isNotBlank() && expiresAt.isNotBlank()
}

data class PixRenewalCommand(val requestId: String, val dueDate: String)

interface PixRenewalGateway {
    suspend fun renew(order: MemberPaymentOrder, instrument: MemberPaymentInstrument, command: PixRenewalCommand):
        SaqzResult<PixRenewal, ReceiptError>
    suspend fun recover(order: MemberPaymentOrder, instrument: MemberPaymentInstrument, requestId: String):
        SaqzResult<PixRenewal?, ReceiptError>
}

fun MemberPaymentOrder.canRenew(instrument: MemberPaymentInstrument, expired: Boolean): Boolean =
    id == instrument.orderId && accountId == instrument.accountId && status == "ISSUED" &&
        instrument.quote in quotes && instrument.quote.method == ReceiptMethod.PIX &&
        instrument.status in setOf("ACTIVE", "EXPIRED") && !instrument.paymentId.isNullOrBlank() && expired
