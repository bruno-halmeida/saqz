package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate

class HttpAsaasRecurrenceProvider(private val baseUrl: URI, private val platformWalletId: String,
    private val callbackBaseUrl: URI, private val credentials: PaymentProviderCredentials,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build()) : RecurrenceProvider {
    private val mapper = jacksonObjectMapper()
    init {
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
        require(callbackBaseUrl.scheme == "https" && callbackBaseUrl.host != null && callbackBaseUrl.userInfo == null && callbackBaseUrl.query == null && callbackBaseUrl.fragment == null)
        require(platformWalletId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    }

    override fun prepareCustomer(context: RecurrenceAuthorization): Boolean {
        val recurrence = context.recurrence
        val customer = credentials.customer(recurrence.accountId, recurrence.memberUserId)
        if (customer.providerId != null) return true
        val key = credentials.apiKey(recurrence.accountId)
        val node = if (customer.canCreate) try {
            send(key, "POST", "/customers", mapOf("name" to customer.payer.name,
                "cpfCnpj" to customer.payer.cpfCnpj, "externalReference" to customer.reference.toString(),
                "notificationDisabled" to true))
        } catch (rejected: DefinitivePaymentRejection) {
            credentials.rejectCustomer(recurrence.accountId, recurrence.memberUserId); throw rejected
        } else single(send(key, "GET", "/customers?externalReference=${encoded(customer.reference.toString())}&limit=2")) ?: return false
        require(node.path("externalReference").asText() == customer.reference.toString())
        require(node.path("cpfCnpj").asText() == customer.payer.cpfCnpj)
        credentials.saveCustomer(recurrence.accountId, recurrence.memberUserId, identifier(node, "id"))
        return true
    }

    override fun create(context: RecurrenceAuthorization): ProviderRecurrenceResult {
        val recurrence = context.recurrence
        val customer = credentials.customer(recurrence.accountId, recurrence.memberUserId).providerId ?: error("Customer unavailable")
        val key = credentials.apiKey(recurrence.accountId)
        return if (recurrence.method == PaymentMethod.PIX) {
            val node = send(key, "POST", "/subscriptions", mapOf("customer" to customer, "billingType" to "PIX",
                "value" to BigDecimal.valueOf(recurrence.totalCents, 2), "nextDueDate" to recurrence.firstDueDate.toString(),
                "cycle" to "MONTHLY", "description" to "Mensalidade Saqz", "externalReference" to recurrence.id.toString(),
                "interest" to mapOf("value" to 0), "fine" to mapOf("value" to 0), "split" to splits(context)))
            require(node.path("externalReference").asText() == recurrence.id.toString())
            require(node.path("billingType").asText() == "PIX")
            require(cents(node, "value") == recurrence.totalCents)
            ProviderRecurrenceResult(subscriptionId = identifier(node, "id"), status = "ACTIVE")
        } else {
            val callbacks = mapOf("successUrl" to callback("success"), "cancelUrl" to callback("cancel"), "expiredUrl" to callback("expired"))
            val node = send(key, "POST", "/checkouts", mapOf("billingTypes" to listOf("CREDIT_CARD"),
                "chargeTypes" to listOf("RECURRENT"), "minutesToExpire" to 60,
                "externalReference" to recurrence.id.toString(), "customer" to customer, "callback" to callbacks,
                "items" to listOf(mapOf("name" to "Mensalidade Saqz", "description" to "Mensalidade recorrente",
                    "quantity" to 1, "value" to BigDecimal.valueOf(recurrence.totalCents, 2))),
                "subscription" to mapOf("cycle" to "MONTHLY", "nextDueDate" to recurrence.firstDueDate.toString()),
                "splits" to splits(context)))
            val id = identifier(node, "id")
            val link = node.get("link")?.asText()?.takeIf { it.isNotBlank() }
                ?: "https://asaas.com/checkoutSession/show?id=$id"
            validateHosted(link)
            ProviderRecurrenceResult(checkoutId = id, checkoutUrl = link, status = "AUTHORIZING")
        }
    }

    override fun recover(context: RecurrenceAuthorization): ProviderRecurrenceResult? {
        val recurrence = context.recurrence
        val key = credentials.apiKey(recurrence.accountId)
        recurrence.providerSubscriptionId?.let { id ->
            val node = send(key, "GET", "/subscriptions/${safeId(id)}")
            require(node.path("externalReference").asText() == recurrence.id.toString())
            return ProviderRecurrenceResult(subscriptionId = id, checkoutId = context.providerCheckoutId,
                checkoutUrl = recurrence.hostedCheckoutUrl, status = if (node.path("status").asText() == "ACTIVE") "ACTIVE" else "STOPPED")
        }
        if (recurrence.method == PaymentMethod.PIX) {
            val found = single(send(key, "GET", "/subscriptions?externalReference=${encoded(recurrence.id.toString())}&limit=2")) ?: return null
            return ProviderRecurrenceResult(subscriptionId = identifier(found, "id"), status = if (found.path("status").asText() == "ACTIVE") "ACTIVE" else "STOPPED")
        }
        val checkout = context.providerCheckoutId ?: return null
        val payment = single(send(key, "GET", "/payments?checkoutSession=${encoded(checkout)}&limit=2"))
            ?: return ProviderRecurrenceResult(checkoutId = checkout, checkoutUrl = recurrence.hostedCheckoutUrl, status = "AUTHORIZING")
        val subscription = payment.path("subscription").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
            ?: return ProviderRecurrenceResult(checkoutId = checkout, checkoutUrl = recurrence.hostedCheckoutUrl, status = "AUTHORIZING")
        return ProviderRecurrenceResult(subscriptionId = safeId(subscription), checkoutId = checkout,
            checkoutUrl = recurrence.hostedCheckoutUrl, status = "ACTIVE")
    }

    override fun payments(context: RecurrenceAuthorization): List<ProviderRecurringPayment> {
        val subscription = context.recurrence.providerSubscriptionId ?: return emptyList()
        val key = credentials.apiKey(context.recurrence.accountId)
        val result = mutableListOf<ProviderRecurringPayment>()
        var offset = 0
        repeat(100) {
            val page = send(key, "GET", "/subscriptions/${safeId(subscription)}/payments?limit=100&offset=$offset")
            require(page.path("data").isArray)
            page.path("data").forEach { node ->
                val method = when (node.path("billingType").asText()) { "PIX" -> PaymentMethod.PIX; "CREDIT_CARD" -> PaymentMethod.CARD; else -> return@forEach }
                val linked = required(node, "subscription"); require(linked == subscription)
                result += ProviderRecurringPayment(identifier(node, "id"), linked, LocalDate.parse(required(node, "dueDate")),
                    required(node, "status"), method, cents(node, "value"))
            }
            if (!page.path("hasMore").asBoolean(false)) return result
            offset += 100
        }
        error("Subscription payment pagination exceeded")
    }

    override fun stop(context: RecurrenceAuthorization, cutoffDate: LocalDate): Boolean {
        val recurrence = context.recurrence
        val key = credentials.apiKey(recurrence.accountId)
        val subscription = recurrence.providerSubscriptionId
        if (subscription == null) {
            val checkout = context.providerCheckoutId ?: return false
            send(key, "POST", "/checkouts/${safeId(checkout)}/cancel")
            return true
        }
        val updated = send(key, "PUT", "/subscriptions/${safeId(subscription)}", mapOf("status" to "INACTIVE"))
        if (updated.path("id").asText() != subscription || updated.path("status").asText() != "INACTIVE") return false
        payments(context).filter { it.dueDate > cutoffDate && it.status in setOf("PENDING", "OVERDUE") }.forEach { payment ->
            val deleted = send(key, "DELETE", "/payments/${safeId(payment.id)}")
            require(deleted.path("deleted").asBoolean(false) && deleted.path("id").asText() == payment.id)
        }
        return payments(context).none { it.dueDate > cutoffDate && it.status in setOf("PENDING", "OVERDUE") }
    }

    private fun splits(context: RecurrenceAuthorization): List<Map<String, Any>> = if (context.quote.commissionCents == 0L) emptyList()
        else listOf(mapOf("walletId" to platformWalletId, "fixedValue" to context.quote.fixedSplitValue()))
    private fun callback(state: String) = callbackBaseUrl.toString().trimEnd('/') + "/receivables/return/$state"
    private fun validateHosted(value: String) { val uri = URI(value); require(uri.scheme == "https" && uri.userInfo == null && uri.host != null && (uri.host == "asaas.com" || uri.host.endsWith(".asaas.com"))) }
    private fun single(node: JsonNode): JsonNode? { require(node.path("data").isArray && !node.path("hasMore").asBoolean(true) && node.path("data").size() <= 1); return node.path("data").firstOrNull() }
    private fun cents(node: JsonNode, key: String): Long { require(node.path(key).isNumber); return node.path(key).decimalValue().movePointRight(2).longValueExact() }
    private fun identifier(node: JsonNode, key: String) = safeId(required(node, key))
    private fun safeId(id: String): String { require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))); return id }
    private fun required(node: JsonNode, key: String) = node.path(key).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() } ?: error("Incomplete provider response")
    private fun encoded(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun send(key: String, method: String, path: String, body: Any? = null): JsonNode {
        val request = HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + path)).timeout(Duration.ofSeconds(60))
            .header("access_token", key).header("User-Agent", "Saqz-Receivables/1.0").header("Accept", "application/json")
            .header("Content-Type", "application/json").method(method, if (body == null) HttpRequest.BodyPublishers.noBody()
                else HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) } catch (_: Exception) { error("Provider result unknown") }
        if (response.statusCode() == 400 && mapper.readTree(response.body()).path("errors").isArray) throw DefinitivePaymentRejection()
        check(response.statusCode() in 200..299) { "Provider result unknown" }
        return mapper.readTree(response.body())
    }
}
