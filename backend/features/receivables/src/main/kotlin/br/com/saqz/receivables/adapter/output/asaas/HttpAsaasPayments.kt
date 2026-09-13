package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Official /payments hosted invoice flow; deliberately does not use /checkouts or remote idempotency headers. */
class HttpAsaasPayments(private val baseUrl: URI, private val walletId: String,
    private val credentials: PaymentProviderCredentials,
    private val webhookBaseUrl: String? = null, private val webhookEmail: String? = null,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build()) : OneOffPaymentProvider, PaymentWebhookRegistrationProvider {
    private val mapper = jacksonObjectMapper()
    init {
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
        require(walletId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    }
    override fun configureWebhook(accountId: java.util.UUID, token: String, canCreate: Boolean): String? {
        val base = requireNotNull(webhookBaseUrl)
        val uri = URI(base); require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
        val url = base.trimEnd('/') + "/api/receivables/webhooks/asaas/$accountId"
        val key = credentials.apiKey(accountId)
        val name = "Saqz " + paymentDigest(token).take(32)
        if (!canCreate) {
            var offset = 0
            repeat(100) {
                val page = send(key, "GET", "/webhooks?limit=100&offset=$offset")
                require(page.path("data").isArray)
                val matching = page.path("data").filter { it.path("url").asText() == url }
                require(matching.size <= 1)
                if (matching.isNotEmpty()) {
                    val found = matching.single()
                    require(found.path("name").asText() == name && found.path("hasAuthToken").asBoolean() && found.path("enabled").asBoolean())
                    return identifier(found, "id")
                }
                if (!page.path("hasMore").asBoolean(true)) return null
                offset += 100
            }
            return null
        }
        return identifier(send(key, "POST", "/webhooks", mapOf("name" to name, "url" to url,
            "email" to requireNotNull(webhookEmail), "enabled" to true, "interrupted" to false,
            "apiVersion" to 3, "authToken" to token, "sendType" to "SEQUENTIALLY",
            "events" to listOf("PAYMENT_CREATED", "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED", "PAYMENT_DELETED",
                "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE", "PAYMENT_AWAITING_CHARGEBACK_REVERSAL", "PAYMENT_SPLIT_DONE"))), "id")
    }

    override fun prepareCustomer(context: ProviderPaymentContext): Boolean {
        val account = context.instrument.accountId
        val customer = credentials.customer(account, context.payerId)
        if (customer.providerId != null) return true
        val key = credentials.apiKey(account)
        val node = if (customer.canCreate) try { send(key, "POST", "/customers", mapOf(
            "name" to customer.payer.name, "cpfCnpj" to customer.payer.cpfCnpj,
            "externalReference" to customer.reference.toString(), "notificationDisabled" to true)) }
        catch (rejected: DefinitivePaymentRejection) { credentials.rejectCustomer(account, context.payerId); throw rejected }
        else single(send(key, "GET", "/customers?externalReference=${customer.reference}&limit=2")) ?: return false
        require(node.path("externalReference").asText() == customer.reference.toString())
        require(node.path("cpfCnpj").asText() == customer.payer.cpfCnpj)
        credentials.saveCustomer(account, context.payerId, identifier(node, "id"))
        return true
    }
    override fun create(context: ProviderPaymentContext): ProviderPaymentObservation {
        val instrument = context.instrument; val quote = instrument.quote
        val customer = credentials.customer(instrument.accountId, context.payerId).providerId ?: error("Customer unavailable")
        val node = send(credentials.apiKey(instrument.accountId), "POST", "/payments", mapOf(
            "customer" to customer, "billingType" to if (quote.method == PaymentMethod.PIX) "PIX" else "CREDIT_CARD",
            "value" to BigDecimal.valueOf(quote.totalCents, 2), "dueDate" to context.dueDate.toString(),
            "externalReference" to instrument.id.toString(), "description" to "Pagamento Saqz",
            "interest" to mapOf("value" to 0), "fine" to mapOf("value" to 0),
            "split" to if (quote.commissionCents == 0L) emptyList<Any>() else listOf(mapOf("walletId" to walletId, "fixedValue" to quote.fixedSplitValue()))))
        return observation(context, node)
    }
    override fun recover(context: ProviderPaymentContext): ProviderPaymentObservation? {
        val i = context.instrument; val key = credentials.apiKey(i.accountId)
        val node = if (i.paymentId != null) send(key, "GET", "/payments/${safeId(i.paymentId)}")
            else single(send(key, "GET", "/payments?externalReference=${i.id}&limit=2")) ?: return null
        return observation(context, node)
    }
    override fun cancel(context: ProviderPaymentContext): ProviderPaymentObservation? {
        val observed = recover(context) ?: return null
        if (observed.status != "ACTIVE") return observed
        val id = observed.paymentId ?: return null
        val response = send(credentials.apiKey(context.instrument.accountId), "DELETE", "/payments/${safeId(id)}")
        // Only a successful provider deletion is proof; HTTP 404 and timeouts remain unknown.
        if (!response.path("deleted").asBoolean(false) || response.path("id").asText() != id) return null
        return observed.copy(status = "CANCELLED")
    }
    private fun observation(context: ProviderPaymentContext, node: JsonNode): ProviderPaymentObservation {
        val instrument = context.instrument
        val reference = node.path("externalReference").asText()
        require(reference == instrument.id.toString())
        val method = when (node.path("billingType").asText()) {
            "PIX" -> PaymentMethod.PIX; "CREDIT_CARD" -> PaymentMethod.CARD; else -> error("Unexpected payment method")
        }
        val id = identifier(node, "id")
        val total = cents(node, "value")
        val status = if (node.path("deleted").asBoolean(false)) "CANCELLED" else when (node.path("status").asText()) {
            "PENDING", "OVERDUE" -> "ACTIVE"
            "AWAITING_RISK_ANALYSIS" -> "UNKNOWN"
            "CONFIRMED" -> "CONFIRMED"
            "RECEIVED" -> "AVAILABLE"
            "REFUNDED" -> "REFUNDED"
            "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE" -> "DISPUTED"
            "AWAITING_CHARGEBACK_REVERSAL" -> "RECOVERY_PENDING"
            else -> error("Unsupported payment status")
        }
        val splits = node.path("split").filter { it.path("walletId").asText() == walletId }
        require(splits.size <= 1)
        val split = splits.singleOrNull()
        val qr = if (method == PaymentMethod.PIX && status == "ACTIVE")
            send(credentials.apiKey(instrument.accountId), "GET", "/payments/$id/pixQrCode") else null
        val url = if (method == PaymentMethod.CARD && status == "ACTIVE") node.path("invoiceUrl").asText().also {
            val uri = URI(it); require(uri.scheme == "https" && uri.userInfo == null &&
                (uri.host == "asaas.com" || uri.host.endsWith(".asaas.com")))
        } else null
        return ProviderPaymentObservation(paymentId = id, reference = reference, method = method, totalCents = total, status = if (status == "AVAILABLE" && node.path("escrow").path("status").asText() == "ACTIVE") "SETTLED" else status,
            providerFeeCents = node.get("netValue")?.takeIf { it.isNumber }?.let { total - cents(node, "netValue") },
            splitCents = split?.let { cents(it, "fixedValue") } ?: if (instrument.quote.commissionCents == 0L) 0 else null,
            splitId = split?.get("id")?.asText(), splitSettled = split?.path("status")?.asText() == "DONE",
            available = node.path("status").asText() == "RECEIVED" && node.path("escrow").path("status").asText() != "ACTIVE",
            returnedCommissionCents = split?.get("id")?.asText()?.let { splitId ->
                val returned = node.path("refunds").filter { it.path("status").asText() == "DONE" }
                    .flatMap { it.path("refundedSplits").toList() }.filter { it.path("id").asText() == splitId && it.path("done").asBoolean() }
                if (returned.isEmpty()) null else returned.sumOf { cents(it, "value") }
            },
            pixPayload = qr?.let { required(it, "payload") }, pixImage = qr?.let { required(it, "encodedImage") }, checkoutUrl = url,
            expiresAt = qr?.get("expirationDate")?.asText()?.let { java.time.LocalDateTime.parse(it,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant() })
    }
    private fun single(node: JsonNode): JsonNode? {
        require(node.path("data").isArray && !node.path("hasMore").asBoolean(true))
        require(node.path("data").size() <= 1)
        return node.path("data").firstOrNull()
    }
    private fun cents(node: JsonNode, key: String): Long {
        require(node.path(key).isNumber)
        return node.path(key).decimalValue().movePointRight(2).longValueExact()
    }
    private fun identifier(node: JsonNode, key: String) = safeId(required(node, key))
    private fun safeId(id: String): String { require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))); return id }
    private fun required(node: JsonNode, key: String): String = node.path(key).takeIf { it.isTextual }?.asText()
        ?.takeIf { it.isNotBlank() } ?: error("Incomplete payment response")
    private fun send(key: String, method: String, path: String, body: Any? = null): JsonNode {
        val request = HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + path)).timeout(Duration.ofSeconds(30))
            .header("access_token", key).header("User-Agent", "Saqz-Receivables/1.0").header("Accept", "application/json")
            .header("Content-Type", "application/json").method(method, if (body == null) HttpRequest.BodyPublishers.noBody()
                else HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            catch (_: Exception) { error("Payment provider result unknown") }
        if (response.statusCode() == 400 && mapper.readTree(response.body()).path("errors").isArray) throw DefinitivePaymentRejection()
        check(response.statusCode() in 200..299) { "Payment provider result unknown" }
        return mapper.readTree(response.body())
    }
}
