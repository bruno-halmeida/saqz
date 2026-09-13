package br.com.saqz.receivables.data

import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable
internal data class RecurrenceReviewTransport(
    val accountId: String,
    val groupId: String,
    val memberUserId: String,
    val method: String,
    val baseCents: Long,
    val feesCents: Long,
    val totalCents: Long,
    val commissionCents: Long,
    val expectedProviderFeeCents: Long,
    val expectedNetCents: Long,
    val feeScheduleId: String,
    val termsVersion: String,
    val firstDueDate: String,
    val cycle: String,
    val fingerprint: String,
) {
    fun domain() = RecurrenceReview(accountId, groupId, memberUserId, ReceiptMethod.valueOf(method), baseCents,
        feesCents, totalCents, commissionCents, expectedProviderFeeCents, expectedNetCents, feeScheduleId,
        termsVersion, firstDueDate, cycle, fingerprint)
}

@Serializable
internal data class PaymentRecurrenceTransport(
    val id: String,
    val accountId: String,
    val groupId: String,
    val memberUserId: String,
    val method: String,
    val baseCents: Long,
    val feesCents: Long,
    val totalCents: Long,
    val firstDueDate: String,
    val status: String,
    val providerSubscriptionId: String? = null,
    val hostedCheckoutUrl: String? = null,
    val cutoffAt: String? = null,
) {
    fun domain() = PaymentRecurrence(id, accountId, groupId, memberUserId, ReceiptMethod.valueOf(method), baseCents,
        feesCents, totalCents, firstDueDate, status, providerSubscriptionId, hostedCheckoutUrl, cutoffAt)
}

@Serializable internal data class RecurrencePreviewTransport(val requestId: String, val accountId: String,
    val groupId: String, val method: String, val firstDueDate: String)
@Serializable internal data class RecurrencePayerTransport(val name: String, val cpfCnpj: String)
@Serializable internal data class RecurrenceAcceptanceTransport(val requestId: String, val accountId: String,
    val groupId: String, val method: String, val firstDueDate: String, val fingerprint: String, val accepted: Boolean,
    val payer: RecurrencePayerTransport)
@Serializable internal data class RecurrenceCancelTransport(val requestId: String)

@Serializable
internal data class PixRenewalTransport(
    val orderId: String,
    val instrumentId: String,
    val providerPaymentId: String,
    val method: String,
    val status: String,
    val baseCents: Long,
    val feesCents: Long,
    val totalCents: Long,
    val dueDate: String,
    val pixPayload: String?,
    val pixImage: String? = null,
    val expiresAt: String?,
) {
    fun domain() = PixRenewal(orderId, instrumentId, providerPaymentId, ReceiptMethod.valueOf(method), status,
        baseCents, feesCents, totalCents, dueDate, requireNotNull(pixPayload), pixImage, requireNotNull(expiresAt))
}

@Serializable internal data class PixRenewalCommandTransport(val requestId: String, val dueDate: String)
