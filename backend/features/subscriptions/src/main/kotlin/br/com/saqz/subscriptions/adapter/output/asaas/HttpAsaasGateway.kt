package br.com.saqz.subscriptions.adapter.output.asaas

import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.AsaasConcurrentOperationException
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.AsaasIdempotencyStore
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class HttpAsaasGateway(
    private val settings: AsaasClientSettings,
    private val idempotencyStore: AsaasIdempotencyStore,
    private val httpClient: HttpClient = defaultHttpClient(),
    private val objectMapper: ObjectMapper = defaultObjectMapper(),
    private val clock: Clock = Clock.systemUTC(),
    private val requestTimeout: Duration = REQUEST_TIMEOUT,
    private val maxIdempotencyPolls: Int = DEFAULT_MAX_IDEMPOTENCY_POLLS,
    private val idempotencyPollWait: (attempt: Int) -> Unit = defaultIdempotencyPollWait,
    private val abandonAfter: Duration = DEFAULT_ABANDON_AFTER,
) : AsaasGateway {
    override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String): String {
        val externalReference = ownerUserId.toString()
        return withIdempotency(
            storeKey = customerIdempotencyKey(ownerUserId),
            collectionPath = "/customers",
            externalReference = externalReference,
        ) {
            val body = mapOf(
                "name" to name,
                "email" to email,
                "cpfCnpj" to cpfCnpj,
                "externalReference" to externalReference,
            )
            requireId(post("/customers", body), "customer")
        }
    }

    override fun createSubscription(
        asaasCustomerId: String,
        plan: Plan,
        cycle: SubscriptionCycle,
        valueCents: Long,
        billingType: AsaasBillingType,
        idempotencyKey: String,
    ): String =
        withIdempotency(
            storeKey = idempotencyKey,
            collectionPath = "/subscriptions",
            externalReference = idempotencyKey,
        ) {
            val body = mapOf(
                "customer" to asaasCustomerId,
                "billingType" to asaasBillingType(billingType),
                "value" to centsToDecimal(valueCents),
                "nextDueDate" to today().toString(),
                "cycle" to asaasCycle(cycle),
                "description" to "Assinatura Saqz ${plan.name}",
                "externalReference" to idempotencyKey,
            )
            requireId(post("/subscriptions", body), "subscription")
        }

    override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) {
        val body = mapOf("value" to centsToDecimal(valueCents))
        put("/subscriptions/$asaasSubscriptionId", body)
    }

    override fun createOneOffCharge(
        asaasCustomerId: String,
        valueCents: Long,
        description: String,
        idempotencyKey: String,
    ): String =
        withIdempotency(
            storeKey = idempotencyKey,
            collectionPath = "/payments",
            externalReference = idempotencyKey,
        ) {
            val body = mapOf(
                "customer" to asaasCustomerId,
                "billingType" to "PIX",
                "value" to centsToDecimal(valueCents),
                "dueDate" to today().toString(),
                "description" to description,
                "externalReference" to idempotencyKey,
            )
            requireId(post("/payments", body), "payment")
        }

    override fun regeneratePixPayload(asaasChargeId: String): String {
        val response = get("/payments/$asaasChargeId/pixQrCode")
        val payload = response.path("payload").asText(null)
            ?: throw AsaasException(statusCode = 200, message = "Asaas pixQrCode response missing payload")
        return payload
    }

    private fun withIdempotency(
        storeKey: String,
        collectionPath: String,
        externalReference: String,
        create: () -> String,
    ): String {
        require(storeKey.isNotBlank()) { "idempotencyKey must not be blank" }

        if (idempotencyStore.tryBegin(storeKey, clock.instant())) {
            return executeCreate(storeKey, collectionPath, externalReference, create)
        }

        pollLocalResourceId(storeKey)?.let { return it }
        reconcileAndComplete(storeKey, collectionPath, externalReference)?.let { return it }

        val reservation = idempotencyStore.find(storeKey)
            ?: throw AsaasConcurrentOperationException(storeKey)
        if (reservation.resourceId != null) {
            return reservation.resourceId
        }

        // Só libera se a reserva for antiga o bastante — worker lento ≠ abandonado.
        if (!isAbandoned(reservation.createdAt)) {
            throw AsaasConcurrentOperationException(storeKey)
        }

        idempotencyStore.release(storeKey)
        if (idempotencyStore.tryBegin(storeKey, clock.instant())) {
            return executeCreate(storeKey, collectionPath, externalReference, create)
        }

        pollLocalResourceId(storeKey)?.let { return it }
        reconcileAndComplete(storeKey, collectionPath, externalReference)?.let { return it }
        throw AsaasConcurrentOperationException(storeKey)
    }

    private fun executeCreate(
        storeKey: String,
        collectionPath: String,
        externalReference: String,
        create: () -> String,
    ): String =
        try {
            val resourceId = create()
            idempotencyStore.complete(storeKey, resourceId)
            resourceId
        } catch (ex: Exception) {
            handleCreateFailure(storeKey, collectionPath, externalReference, ex)
        }

    private fun handleCreateFailure(
        storeKey: String,
        collectionPath: String,
        externalReference: String,
        ex: Exception,
    ): String {
        if (isDefinitiveClientRejection(ex)) {
            idempotencyStore.release(storeKey)
            throw ex
        }

        val existing = try {
            findIdByExternalReference(collectionPath, externalReference)
        } catch (_: Exception) {
            throw ex
        }
        if (existing != null) {
            idempotencyStore.complete(storeKey, existing)
            return existing
        }
        // ponytail: a single immediate reconciliation GET can still race Asaas's own
        // write-commit — an empty result isn't a guaranteed "not created", just "not
        // yet visible". See VUL-114, gated on real Asaas production traffic.
        idempotencyStore.release(storeKey)
        throw ex
    }

    private fun pollLocalResourceId(storeKey: String): String? {
        repeat(maxIdempotencyPolls) { attempt ->
            idempotencyStore.find(storeKey)?.resourceId?.let { return it }
            if (attempt < maxIdempotencyPolls - 1) {
                idempotencyPollWait(attempt)
            }
        }
        return idempotencyStore.find(storeKey)?.resourceId
    }

    private fun reconcileAndComplete(
        storeKey: String,
        collectionPath: String,
        externalReference: String,
    ): String? {
        val existing = findIdByExternalReference(collectionPath, externalReference) ?: return null
        idempotencyStore.complete(storeKey, existing)
        return existing
    }

    private fun isAbandoned(createdAt: java.time.Instant): Boolean =
        Duration.between(createdAt, clock.instant()) >= abandonAfter

    private fun findIdByExternalReference(collectionPath: String, externalReference: String): String? {
        val encoded = URLEncoder.encode(externalReference, StandardCharsets.UTF_8)
        val response = get("$collectionPath?externalReference=$encoded&limit=1")
        val data = response.path("data")
        if (!data.isArray || data.size() == 0) return null
        return data[0].path("id").asText(null)?.takeIf { it.isNotBlank() }
    }

    private fun isDefinitiveClientRejection(ex: Exception): Boolean =
        ex is AsaasException && ex.statusCode in 400..499

    private fun customerIdempotencyKey(ownerUserId: UUID): String = "customer:$ownerUserId"

    private fun post(path: String, body: Map<String, Any?>): JsonNode =
        exchange("POST", path, body)

    private fun put(path: String, body: Map<String, Any?>): JsonNode =
        exchange("PUT", path, body)

    private fun get(path: String): JsonNode =
        exchange("GET", path, body = null)

    private fun exchange(method: String, path: String, body: Map<String, Any?>?): JsonNode {
        val builder = HttpRequest.newBuilder(URI.create(settings.baseUrl + path))
            .timeout(requestTimeout)
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
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AsaasException(
                statusCode = 0,
                message = "Asaas request interrupted: ${ex.message}",
                cause = ex,
            )
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
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(15)
        val DEFAULT_ABANDON_AFTER: Duration = Duration.ofSeconds(30)
        const val DEFAULT_MAX_IDEMPOTENCY_POLLS: Int = 3

        val defaultIdempotencyPollWait: (Int) -> Unit = { attempt ->
            Thread.sleep(50L * (attempt + 1))
        }

        fun defaultObjectMapper(): ObjectMapper = jacksonObjectMapper()

        fun defaultHttpClient(): HttpClient =
            HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build()
    }
}
