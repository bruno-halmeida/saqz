package br.com.saqz.subscriptions.adapter.output.asaas

import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class HttpAsaasGateway(
    private val settings: AsaasClientSettings,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
    private val clock: Clock = Clock.systemUTC(),
) : AsaasGateway {
    override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String): String {
        val body = mapOf(
            "name" to name,
            "email" to email,
            "cpfCnpj" to cpfCnpj,
            "externalReference" to ownerUserId.toString(),
        )
        val response = post("/customers", body)
        return requireId(response, "customer")
    }

    override fun createSubscription(
        asaasCustomerId: String,
        plan: Plan,
        cycle: SubscriptionCycle,
        valueCents: Long,
        billingType: AsaasBillingType,
    ): String {
        val body = mapOf(
            "customer" to asaasCustomerId,
            "billingType" to asaasBillingType(billingType),
            "value" to centsToDecimal(valueCents),
            "nextDueDate" to today().toString(),
            "cycle" to asaasCycle(cycle),
            "description" to "Assinatura Saqz ${plan.name}",
            "externalReference" to plan.name,
        )
        val response = post("/subscriptions", body)
        return requireId(response, "subscription")
    }

    override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) {
        val body = mapOf("value" to centsToDecimal(valueCents))
        put("/subscriptions/$asaasSubscriptionId", body)
    }

    override fun createOneOffCharge(asaasCustomerId: String, valueCents: Long, description: String): String {
        val body = mapOf(
            "customer" to asaasCustomerId,
            "billingType" to "PIX",
            "value" to centsToDecimal(valueCents),
            "dueDate" to today().toString(),
            "description" to description,
        )
        val response = post("/payments", body)
        return requireId(response, "payment")
    }

    override fun regeneratePixPayload(asaasChargeId: String): String {
        val response = get("/payments/$asaasChargeId/pixQrCode")
        val payload = response.path("payload").asText(null)
            ?: throw AsaasException(statusCode = 200, message = "Asaas pixQrCode response missing payload")
        return payload
    }

    private fun post(path: String, body: Map<String, Any?>): JsonNode =
        exchange("POST", path, body)

    private fun put(path: String, body: Map<String, Any?>): JsonNode =
        exchange("PUT", path, body)

    private fun get(path: String): JsonNode =
        exchange("GET", path, body = null)

    private fun exchange(method: String, path: String, body: Map<String, Any?>?): JsonNode {
        val builder = HttpRequest.newBuilder(URI.create(settings.baseUrl + path))
            .header("access_token", settings.apiKey)
            .header("User-Agent", settings.userAgent)
            .header("Accept", "application/json")

        val request = when {
            body == null -> builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
            else -> builder
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build()
        }

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            throw AsaasException(statusCode = 0, message = "Asaas request failed: ${ex.message}", cause = ex)
        }

        val status = response.statusCode()
        val responseBody = response.body().orEmpty()
        if (status !in 200..299) {
            throw AsaasException(
                statusCode = status,
                message = "Asaas $method $path failed with HTTP $status: ${errorSummary(responseBody)}",
            )
        }
        if (responseBody.isBlank()) {
            return objectMapper.createObjectNode()
        }
        return objectMapper.readTree(responseBody)
    }

    private fun requireId(node: JsonNode, resource: String): String {
        val id = node.path("id").asText(null)
        if (id.isNullOrBlank()) {
            throw AsaasException(statusCode = 200, message = "Asaas $resource response missing id")
        }
        return id
    }

    private fun errorSummary(body: String): String {
        if (body.isBlank()) return "(empty body)"
        return try {
            val root = objectMapper.readTree(body)
            val errors = root.path("errors")
            if (errors.isArray && errors.size() > 0) {
                errors.joinToString("; ") { item ->
                    val code = item.path("code").asText("unknown")
                    val description = item.path("description").asText("")
                    "$code: $description".trimEnd(':', ' ')
                }
            } else {
                body.take(500)
            }
        } catch (_: Exception) {
            body.take(500)
        }
    }

    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun asaasCycle(cycle: SubscriptionCycle): String =
        when (cycle) {
            SubscriptionCycle.MONTHLY -> "MONTHLY"
            SubscriptionCycle.ANNUAL -> "YEARLY"
        }

    private fun asaasBillingType(billingType: AsaasBillingType): String =
        when (billingType) {
            AsaasBillingType.PIX -> "PIX"
            AsaasBillingType.CREDIT_CARD -> "CREDIT_CARD"
        }

    private fun centsToDecimal(valueCents: Long): BigDecimal =
        BigDecimal.valueOf(valueCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    companion object {
        fun defaultObjectMapper(): ObjectMapper = jacksonObjectMapper()
    }
}
