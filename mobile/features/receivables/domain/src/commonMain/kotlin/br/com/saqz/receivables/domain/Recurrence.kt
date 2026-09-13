package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzResult

data class RecurrenceReview(
    val accountId: String,
    val groupId: String,
    val memberUserId: String,
    val method: ReceiptMethod,
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
    fun validFor(accountId: String, groupId: String, actorId: String, method: ReceiptMethod): Boolean =
        this.accountId == accountId && this.groupId == groupId && memberUserId == actorId && this.method == method &&
            baseCents > 0 && feesCents >= 0 && totalCents > 0 && totalCents - baseCents == feesCents &&
            commissionCents >= 0 &&
            expectedProviderFeeCents >= 0 && expectedNetCents >= 0 && commissionCents <= totalCents &&
            expectedProviderFeeCents <= totalCents - commissionCents &&
            expectedNetCents == totalCents - commissionCents - expectedProviderFeeCents &&
            feeScheduleId.isNotBlank() && termsVersion.isNotBlank() &&
            firstDueDate.isPaymentDate() && cycle == "MONTHLY" && fingerprint.matches(FINGERPRINT)
}

data class PaymentRecurrence(
    val id: String,
    val accountId: String,
    val groupId: String,
    val memberUserId: String,
    val method: ReceiptMethod,
    val baseCents: Long,
    val feesCents: Long,
    val totalCents: Long,
    val firstDueDate: String,
    val status: String,
    val providerSubscriptionId: String?,
    val hostedCheckoutUrl: String?,
    val cutoffAt: String?,
) {
    fun validFor(accountId: String, groupId: String, actorId: String): Boolean =
        id.isNotBlank() && this.accountId == accountId && this.groupId == groupId && memberUserId == actorId &&
            baseCents > 0 && feesCents >= 0 && totalCents > 0 && totalCents - baseCents == feesCents && firstDueDate.isPaymentDate() &&
            status in RECURRENCE_STATUSES && when (method) {
                ReceiptMethod.PIX -> hostedCheckoutUrl == null
                ReceiptMethod.CARD -> hostedCheckoutUrl == null || hostedCheckoutUrl.isAsaasHostedUrl()
            }
}

data class RecurrencePreviewCommand(
    val requestId: String,
    val accountId: String,
    val groupId: String,
    val method: ReceiptMethod,
    val firstDueDate: String,
)

data class RecurrenceAcceptanceCommand(
    val requestId: String,
    val accountId: String,
    val groupId: String,
    val method: ReceiptMethod,
    val firstDueDate: String,
    val fingerprint: String,
    val accepted: Boolean,
    val payer: MemberPaymentPayer,
)

data class RecurrenceAttempt(
    val requestId: String,
    val actorId: String,
    val accountId: String,
    val groupId: String,
    val recurrenceId: String?,
    val operation: String,
)

interface RecurrenceGateway {
    suspend fun discover(accountId: String, groupId: String): SaqzResult<PaymentRecurrence?, ReceiptError>
    suspend fun preview(command: RecurrencePreviewCommand): SaqzResult<RecurrenceReview, ReceiptError>
    suspend fun authorize(review: RecurrenceReview, command: RecurrenceAcceptanceCommand):
        SaqzResult<PaymentRecurrence, ReceiptError>
    suspend fun get(recurrenceId: String): SaqzResult<PaymentRecurrence, ReceiptError>
    suspend fun cancel(recurrence: PaymentRecurrence, requestId: String): SaqzResult<PaymentRecurrence, ReceiptError>
    suspend fun resume(stopped: PaymentRecurrence, review: RecurrenceReview, command: RecurrenceAcceptanceCommand):
        SaqzResult<PaymentRecurrence, ReceiptError>
    suspend fun recover(attempt: RecurrenceAttempt): SaqzResult<PaymentRecurrence?, ReceiptError>
}

internal val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
internal val FINGERPRINT = Regex("[a-f0-9]{64}")
internal val RECURRENCE_STATUSES = setOf("AUTHORIZING", "ACTIVE", "STOP_PENDING", "STOPPED")
internal fun String.isAsaasHostedUrl(): Boolean =
    Regex("https://(www\\.)?(sandbox\\.)?asaas\\.com/[^\\s?#]+([?#][^\\s]*)?").matches(this)


fun String.isPaymentDate(): Boolean = matches(ISO_DATE) &&
    runCatching { kotlin.time.Instant.parse("${this}T00:00:00Z") }.isSuccess
