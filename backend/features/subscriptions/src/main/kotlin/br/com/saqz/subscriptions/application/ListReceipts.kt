package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.ProcessAsaasWebhook.Companion.EVENT_PAYMENT_CONFIRMED
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

data class Receipt(
    val asaasEventId: String,
    val asaasPaymentId: String?,
    val valueCents: Long?,
    val confirmedAt: Instant?,
    val processedAt: Instant,
)

class ListReceipts(
    private val subscriptions: SubscriptionRepository,
    private val events: SubscriptionEventStore,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    fun execute(ownerUserId: UUID): List<Receipt> {
        val subscription = subscriptions.findByOwnerUserId(ownerUserId) ?: return emptyList()
        val asaasSubId = subscription.asaasSubscriptionId
        return events.listProcessedByType(EVENT_PAYMENT_CONFIRMED)
            .mapNotNull { event -> parseReceipt(event.asaasEventId, event.payload, event.processedAt, asaasSubId) }
    }

    private fun parseReceipt(
        asaasEventId: String,
        payload: String,
        processedAt: Instant?,
        expectedSubscriptionId: String,
    ): Receipt? {
        if (processedAt == null) return null
        val root = runCatching { objectMapper.readTree(payload) }.getOrNull() ?: return null
        val payment = root.path("payment")
        val subscriptionFromPayload = payment.path("subscription").asText(null)
            ?: root.path("subscription").path("id").asText(null)
            ?: root.path("subscription").asText(null)
        if (subscriptionFromPayload != expectedSubscriptionId) return null

        val valueCents = paymentValueToCents(payment.path("value"))
        val confirmedAt = payment.path("confirmedDate").asText(null)
            ?.let { runCatching { Instant.parse(it + if (it.endsWith("Z") || it.contains("T")) "" else "T00:00:00Z") }.getOrNull() }
            ?: payment.path("clientPaymentDate").asText(null)
                ?.let { runCatching { Instant.parse(it + "T00:00:00Z") }.getOrNull() }
            ?: processedAt

        return Receipt(
            asaasEventId = asaasEventId,
            asaasPaymentId = payment.path("id").asText(null),
            valueCents = valueCents,
            confirmedAt = confirmedAt,
            processedAt = processedAt,
        )
    }

    private fun paymentValueToCents(valueNode: JsonNode): Long? {
        if (valueNode.isMissingNode || valueNode.isNull) return null
        val decimal = when {
            valueNode.isNumber -> valueNode.decimalValue()
            valueNode.isTextual -> valueNode.asText().toBigDecimalOrNull()
            else -> null
        } ?: return null
        return decimal
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }
}
