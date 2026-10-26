package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.sharedkernel.group.GroupChargePayments
import br.com.saqz.sharedkernel.group.PayableGroupCharge
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PaymentPayer(val name: String, val cpfCnpj: String) {
    fun valid() = name.trim().length in 2..120 && name.none(Char::isISOControl) && cpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}"))
}

data class ChargePaymentReview(val accountId: UUID, val chargeId: UUID, val groupId: UUID,
    val payerId: UUID, val dueDate: LocalDate, val billingMonth: LocalDate?, val quotes: List<FeeQuote>, val fingerprint: String)
data class PaymentOrder(val id: UUID, val accountId: UUID, val chargeId: UUID, val groupId: UUID,
    val payerId: UUID, val dueDate: LocalDate, val status: String, val quotes: List<FeeQuote>, val fingerprint: String)
data class PaymentInstrument(val id: UUID, val accountId: UUID, val orderId: UUID, val quote: FeeQuote,
    val status: String, val paymentId: String? = null, val checkoutId: String? = null,
    val pixPayload: String? = null, val pixImage: String? = null, val checkoutUrl: String? = null,
    val confirmed: Boolean = false, val settled: Boolean = false, val available: Boolean = false,
    val splitSettled: Boolean = false, val expiresAt: Instant? = null)
data class PaymentOrderDetail(val order: PaymentOrder, val instruments: List<PaymentInstrument>)
data class PaymentOrderPage(val orders: List<PaymentOrder>, val nextCursor: UUID?)
data class ChargeOrderLookup(val detail: PaymentOrderDetail?)

interface PaymentStore {
    fun <T> transaction(block: () -> T): T
    fun webhookReady(accountId: UUID): Boolean
    fun order(id: UUID): PaymentOrder?
    fun orderForCharge(chargeId: UUID): PaymentOrder?
    fun ordersForPayer(payerId: UUID, after: UUID?): List<PaymentOrder>
    fun insertOrder(id: UUID, review: ChargePaymentReview, request: FinancialRequest, at: Instant): PaymentOrder
    fun instruments(orderId: UUID): List<PaymentInstrument>
    fun insertInstrument(order: PaymentOrder, quote: FeeQuote, request: FinancialRequest, digest: String, payer: PaymentPayer, at: Instant): PaymentInstrument
    fun instrumentRequest(accountId: UUID, request: FinancialRequest, digest: String): PaymentInstrument?
    fun changeOrder(id: UUID, status: String)
    fun registerLocal(accountId: UUID, resource: UUID, kind: String, request: FinancialRequest, digest: String, at: Instant): UUID
    fun liveInstrument(orderId: UUID): PaymentInstrument? = instruments(orderId).firstOrNull { it.status !in setOf("CANCELLED", "EXPIRED") }
}

/** Network effects run after the durable transaction, with operation leases and recovery-only retries. */
interface PaymentExecution {
    fun create(instrumentId: UUID)
    fun cancel(instrumentId: UUID, request: FinancialRequest)
    fun reconcile(instrumentId: UUID): Boolean
}

/* DEB-142: a aprovação por cobrança (OneOffPayments) e a renovação de Pix de setembro saíram; ordens e instrumentos
 * existentes seguem reconciliados por JdbcPaymentExecution (recorrência no cartão e recuperação). */
fun paymentDigest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
