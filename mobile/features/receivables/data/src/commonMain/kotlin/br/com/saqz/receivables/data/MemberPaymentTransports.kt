package br.com.saqz.receivables.data

import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable internal data class MemberQuoteTransport(val feeScheduleId: String, val termsVersion: String, val method: String,
    val baseCents: Long, val feesCents: Long, val totalCents: Long, val expectedNetCents: Long,
    val commissionCents: Long, val providerFeeCents: Long) {
    fun domain(): MemberPaymentQuote {
        require(feeScheduleId.isNotBlank() && termsVersion.isNotBlank())
        require(listOf(baseCents, feesCents, totalCents, expectedNetCents, commissionCents, providerFeeCents).all { it >= 0 })
        require(baseCents <= Long.MAX_VALUE - feesCents && totalCents == baseCents + feesCents)
        return MemberPaymentQuote(feeScheduleId, termsVersion, ReceiptMethod.valueOf(method), baseCents, feesCents,
            totalCents, expectedNetCents, commissionCents, providerFeeCents)
    }
}
@Serializable internal data class MemberOrderTransport(val id: String, val accountId: String, val chargeId: String,
    val groupId: String, val payerId: String, val dueDate: String, val status: String,
    val quotes: List<MemberQuoteTransport>, val fingerprint: String) {
    fun domain(): MemberPaymentOrder {
        require(listOf(id, accountId, chargeId, groupId, payerId, dueDate, status).all { it.isNotBlank() })
        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
        require(quotes.isNotEmpty() && quotes.map { it.method }.distinct().size == quotes.size)
        return MemberPaymentOrder(id, accountId, chargeId, groupId, payerId, dueDate, status, quotes.map { it.domain() }, fingerprint)
    }
}
@Serializable internal data class MemberPageTransport(val orders: List<MemberOrderTransport>, val nextCursor: String? = null) {
    fun domain(): MemberPaymentPage {
        require(orders.size <= 50 && orders.map { it.id }.distinct().size == orders.size)
        require(nextCursor == null || (orders.isNotEmpty() && nextCursor == orders.last().id))
        return MemberPaymentPage(orders.map { it.domain() }, nextCursor)
    }
}
@Serializable internal data class MemberInstrumentTransport(val id: String, val accountId: String, val orderId: String,
    val quote: MemberQuoteTransport, val status: String, val paymentId: String? = null, val checkoutId: String? = null,
    val pixPayload: String? = null, val pixImage: String? = null, val checkoutUrl: String? = null,
    val confirmed: Boolean, val settled: Boolean, val available: Boolean, val splitSettled: Boolean, val expiresAt: String? = null) {
    fun domain(): MemberPaymentInstrument {
        require(listOf(id, accountId, orderId, status).all { it.isNotBlank() })
        return MemberPaymentInstrument(id, accountId, orderId, quote.domain(), status, paymentId, checkoutId,
            pixPayload, pixImage, checkoutUrl, confirmed, settled, available, splitSettled, expiresAt)
    }
}
@Serializable internal data class MemberDetailTransport(val order: MemberOrderTransport, val instruments: List<MemberInstrumentTransport>) {
    fun domain(expectedOrderId: String): MemberPaymentDetail {
        val mappedOrder = order.domain()
        require(mappedOrder.id == expectedOrderId)
        val mapped = instruments.map { it.domain() }
        require(mapped.map { it.id }.distinct().size == mapped.size)
        require(mapped.all { it.orderId == mappedOrder.id && it.accountId == mappedOrder.accountId && it.quote in mappedOrder.quotes })
        return MemberPaymentDetail(mappedOrder, mapped)
    }
}
@Serializable internal data class MemberPayerTransport(val name: String, val cpfCnpj: String)
@Serializable internal data class MemberCommandTransport(val requestId: String, val method: String,
    val fingerprint: String, val accepted: Boolean, val payer: MemberPayerTransport)
@Serializable internal data class MemberReconcileTransport(val requestId: String)
