package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.ProcessAsaasWebhook.Companion.CONFIRMING_EVENT_TYPES
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
    private val events: SubscriptionEventStore,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    /**
     * Lists receipts already scoped by owner in SQL. Historical rows without an owner are
     * intentionally omitted instead of parsing their payload to infer ownership.
     *
     * Boleto/PIX liquidam com PAYMENT_RECEIVED e nem sempre mandam PAYMENT_CONFIRMED — listar
     * so CONFIRMED deixava o recibo invisivel, e o app trata "sem recibo" como "nao pagou"
     * (`PaymentViewModel.checkNow`), entao o botao "Ja paguei" nao saia do lugar.
     *
     * Quando o Asaas manda o par para a mesma cobranca os dois viram linha processada, entao
     * [distinctBy] colapsa em um recibo so. O efeito colateral e a pagina poder voltar menor
     * que `limit`: dedup depois do LIMIT do SQL nao tem como ser exato sem a cobranca virar
     * coluna. Chamador que precisa de contagem exata deve paginar ate a lista vir vazia.
     */
    fun execute(ownerUserId: UUID, limit: Int = DEFAULT_LIMIT, offset: Int = 0): List<Receipt> {
        if (limit <= 0 || offset < 0) {
            throw InvalidReceiptPaginationException()
        }
        return events.listProcessedByTypesForOwner(CONFIRMING_EVENT_TYPES, ownerUserId, limit, offset)
            .mapNotNull { event -> parseReceipt(event.asaasEventId, event.payload, event.processedAt) }
            .distinctBy { it.asaasPaymentId ?: it.asaasEventId }
    }

    private fun parseReceipt(
        asaasEventId: String,
        payload: String,
        processedAt: Instant?,
    ): Receipt? {
        if (processedAt == null) return null
        val root = runCatching { objectMapper.readTree(payload) }.getOrNull() ?: return null
        val payment = root.path("payment")

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

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
