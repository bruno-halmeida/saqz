package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzResult

/** Approved, immutable server prices. The client never recalculates fees. */
data class MemberPaymentQuote(val feeScheduleId: String, val termsVersion: String, val method: ReceiptMethod,
    val baseCents: Long, val feesCents: Long, val totalCents: Long, val expectedNetCents: Long,
    val commissionCents: Long, val providerFeeCents: Long)
data class MemberPaymentOrder(val id: String, val accountId: String, val chargeId: String, val groupId: String,
    val payerId: String, val dueDate: String, val status: String, val quotes: List<MemberPaymentQuote>, val fingerprint: String)
data class MemberPaymentPage(val orders: List<MemberPaymentOrder>, val nextCursor: String?)
data class MemberPaymentDetail(val order: MemberPaymentOrder, val instruments: List<MemberPaymentInstrument>)

/** Milestones are historical: available=true is not a balance, and does not override REFUNDED/DISPUTED. */
data class MemberPaymentInstrument(val id: String, val accountId: String, val orderId: String,
    val quote: MemberPaymentQuote, val status: String, val paymentId: String?, val checkoutId: String?,
    val pixPayload: String?, val pixImage: String?, val checkoutUrl: String?, val confirmed: Boolean,
    val settled: Boolean, val available: Boolean, val splitSettled: Boolean, val expiresAt: String?)
data class MemberPaymentPayer(val name: String, val cpfCnpj: String)
data class MemberPaymentCommand(val requestId: String, val method: ReceiptMethod, val fingerprint: String,
    val accepted: Boolean, val payer: MemberPaymentPayer)

interface MemberPaymentsGateway {
    suspend fun orders(after: String? = null): SaqzResult<MemberPaymentPage, ReceiptError>
    suspend fun detail(orderId: String): SaqzResult<MemberPaymentDetail, ReceiptError>
    /** Persist the request ID before calling; retain the exact command in memory for any retry. */
    suspend fun instrument(order: MemberPaymentOrder, command: MemberPaymentCommand): SaqzResult<MemberPaymentInstrument, ReceiptError>
    /** Recovery only. Neither this operation nor a checkout return creates another instrument. */
    suspend fun reconcile(orderId: String, requestId: String): SaqzResult<MemberPaymentDetail, ReceiptError>
}
