package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.PaymentMethod
import java.time.LocalDate
import java.util.UUID

data class ProviderPaymentContext(val instrument: PaymentInstrument, val payerId: UUID, val dueDate: LocalDate)
data class ProviderPaymentObservation(val paymentId: String? = null, val checkoutId: String? = null,
    val reference: String?, val method: PaymentMethod, val totalCents: Long, val status: String,
    val providerFeeCents: Long? = null, val splitCents: Long? = null, val splitId: String? = null,
    val splitSettled: Boolean = false, val available: Boolean = false,
    val pixPayload: String? = null, val pixImage: String? = null, val checkoutUrl: String? = null,
    val expiresAt: java.time.Instant? = null, val returnedCommissionCents: Long? = null)

interface OneOffPaymentProvider {
    /** Customer creation has a separate durable recovery fence, before payment execution is claimed. */
    fun prepareCustomer(context: ProviderPaymentContext): Boolean
    fun create(context: ProviderPaymentContext): ProviderPaymentObservation?
    fun recover(context: ProviderPaymentContext): ProviderPaymentObservation?
    fun cancel(context: ProviderPaymentContext): ProviderPaymentObservation?
}

data class PaymentCustomer(val reference: UUID, val providerId: String?, val canCreate: Boolean, val payer: PaymentPayer)
class DefinitivePaymentRejection : RuntimeException("Provider rejected input")

interface PaymentProviderCredentials {
    fun apiKey(accountId: UUID): String
    fun customer(accountId: UUID, payerId: UUID): PaymentCustomer
    fun rejectCustomer(accountId: UUID, payerId: UUID)
    fun saveCustomer(accountId: UUID, payerId: UUID, providerId: String)
}

/** Provider facts are monotonic and independent: split is never inferred from payment confirmation. */
object PaymentFacts {
    private val terminal = setOf("REFUNDED", "CHARGEBACK")
    fun next(current: PaymentInstrument, observed: ProviderPaymentObservation): String {
        if (current.status in terminal) return current.status
        if (observed.status in terminal) return observed.status
        if (observed.status in setOf("DISPUTED", "RECOVERY_PENDING")) return observed.status
        val rank = mapOf("CONFIRMED" to 1, "SETTLED" to 2, "AVAILABLE" to 3)
        if ((rank[current.status] ?: 0) > (rank[observed.status] ?: 0)) return current.status
        if (current.status in setOf("CANCELLED", "EXPIRED") && observed.status == "ACTIVE") return current.status
        return observed.status
    }
}

interface PaymentWebhookRegistrationProvider {
    fun configureWebhook(accountId: UUID, token: String, canCreate: Boolean): String?
}
interface PaymentWebhookRegistration {
    fun configure(accountId: UUID, request: FinancialRequest): FinancialResult<String>
}
interface PaymentEventInbox {
    fun accept(accountId: UUID, token: String, body: String): Boolean
}
