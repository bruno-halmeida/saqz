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
import java.util.UUID

/**
 * Recebimento pelo app v1 no Asaas, isolado aqui (FIN-011). Pix: QR estático da subconta com valor e uso único; cartão:
 * cobrança CREDIT_CARD com a divisão da comissão; comissão do Pix por transferência para a carteira do Saqz. Mesmas
 * travas de setembro: chave da subconta cifrada, `access_token`, timeouts e resultado incerto consultado, não recriado.
 */
class HttpAsaasPaymentCodes(private val baseUrl: URI, private val platformWalletId: String,
    private val credentials: PaymentProviderCredentials,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build()) : PaymentCodeProvider, PaymentFactsProvider {
    private val mapper = jacksonObjectMapper()
    init {
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
        require(platformWalletId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    }

    override fun ensurePixKey(accountId: UUID): PixKey? {
        val key = credentials.apiKey(accountId)
        val existing = send(key, "GET", "/pix/addressKeys?limit=100").path("data")
            .filter { it.path("type").asText() == "EVP" && it.path("status").asText() !in setOf("DELETED", "REMOVED") }
        val node = existing.firstOrNull { it.path("status").asText() == "ACTIVE" } ?: existing.firstOrNull()
            ?: send(key, "POST", "/pix/addressKeys", mapOf("type" to "EVP"))
        val id = identifier(node, "id")
        val value = node.path("key").asText().takeIf { it.isNotBlank() } ?: return PixKey(id, "", false)
        return PixKey(id, value, node.path("status").asText() == "ACTIVE")
    }

    override fun createPixQr(accountId: UUID, addressKey: String, codeId: UUID, amountCents: Long, description: String): PixQr {
        val node = send(credentials.apiKey(accountId), "POST", "/pix/qrCodes/static", mapOf(
            "addressKey" to addressKey, "description" to description.take(37), "value" to BigDecimal.valueOf(amountCents, 2),
            "format" to "ALL", "allowsMultiplePayments" to false, "externalReference" to codeId.toString()))
        return PixQr(identifier(node, "id"), required(node, "payload"), required(node, "encodedImage"))
    }

    override fun createCardCharge(accountId: UUID, codeId: UUID, customer: CardCustomer, amountCents: Long, commissionCents: Long,
                                  dueDate: LocalDate, description: String, savedCustomer: (String) -> Unit): CardCharge? {
        val key = credentials.apiKey(accountId)
        val customerId = customer.providerId ?: run {
            val found = single(send(key, "GET", "/customers?externalReference=${encoded(customer.reference.toString())}&limit=2"))
            val node = found ?: send(key, "POST", "/customers", mapOf("name" to customer.name, "cpfCnpj" to customer.cpf,
                "externalReference" to customer.reference.toString(), "notificationDisabled" to true))
            require(node.path("externalReference").asText() == customer.reference.toString())
            identifier(node, "id").also(savedCustomer)
        }
        val created = try {
            send(key, "POST", "/payments", mapOf("customer" to customerId, "billingType" to "CREDIT_CARD",
                "value" to BigDecimal.valueOf(amountCents, 2), "dueDate" to dueDate.toString(), "description" to description,
                "externalReference" to codeId.toString(),
                "split" to if (commissionCents == 0L) emptyList<Any>() else listOf(mapOf("walletId" to platformWalletId,
                    "fixedValue" to BigDecimal.valueOf(commissionCents, 2)))))
        } catch (rejected: DefinitivePaymentRejection) { throw rejected }
        catch (_: Exception) { single(send(key, "GET", "/payments?externalReference=${encoded(codeId.toString())}&limit=2")) ?: return null }
        require(created.path("externalReference").asText() == codeId.toString())
        require(cents(created, "value") == amountCents)
        val url = required(created, "invoiceUrl")
        val uri = URI(url)
        require(uri.scheme == "https" && uri.userInfo == null && uri.host != null && (uri.host == "asaas.com" || uri.host.endsWith(".asaas.com")))
        return CardCharge(identifier(created, "id"), url)
    }

    override fun cancelPixQr(accountId: UUID, qrId: String): Boolean =
        send(credentials.apiKey(accountId), "DELETE", "/pix/qrCodes/static/${safeId(qrId)}").path("deleted").asBoolean(true)

    override fun cancelPayment(accountId: UUID, paymentId: String): Boolean {
        val node = send(credentials.apiKey(accountId), "DELETE", "/payments/${safeId(paymentId)}")
        return node.path("deleted").asBoolean(false) && node.path("id").asText() == paymentId
    }

    override fun payment(accountId: UUID, paymentId: String): ObservedPayment {
        val key = credentials.apiKey(accountId)
        val node = send(key, "GET", "/payments/${safeId(paymentId)}")
        val method = when (node.path("billingType").asText()) { "PIX" -> PaymentMethod.PIX; "CREDIT_CARD" -> PaymentMethod.CARD; else -> null }
        val customer = node.path("customer").asText().takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
        val payer = customer?.let { runCatching { send(key, "GET", "/customers/$it").path("name").asText().takeIf(String::isNotBlank) }.getOrNull() }
        return ObservedPayment(identifier(node, "id"), cents(node, "value"),
            node.get("netValue")?.takeIf { it.isNumber }?.let { cents(node, "netValue") }, method, node.path("status").asText(),
            node.path("externalReference").asText().takeIf { it.isNotBlank() }, node.path("pixQrCodeId").asText().takeIf { it.isNotBlank() },
            payer, node.path("deleted").asBoolean(false))
    }

    override fun transfer(accountId: UUID, operationId: UUID, amountCents: Long): String? {
        val key = credentials.apiKey(accountId)
        val node = try {
            send(key, "POST", "/transfers", mapOf("value" to BigDecimal.valueOf(amountCents, 2), "walletId" to platformWalletId,
                "externalReference" to operationId.toString(), "description" to "Comissão Saqz"))
        } catch (rejected: DefinitivePaymentRejection) { throw rejected }
        catch (_: Exception) { return findTransfer(accountId, operationId) }
        require(cents(node, "value") == amountCents)
        return identifier(node, "id")
    }

    override fun findTransfer(accountId: UUID, operationId: UUID): String? {
        val key = credentials.apiKey(accountId)
        var offset = 0
        repeat(20) {
            val page = send(key, "GET", "/transfers?limit=100&offset=$offset")
            page.path("data").firstOrNull { it.path("externalReference").asText() == operationId.toString() }
                ?.let { return identifier(it, "id") }
            if (!page.path("hasMore").asBoolean(false)) return null
            offset += 100
        }
        return null
    }

    private fun single(node: JsonNode): JsonNode? {
        require(node.path("data").isArray && node.path("data").size() <= 1)
        return node.path("data").firstOrNull()
    }
    private fun cents(node: JsonNode, key: String): Long {
        require(node.path(key).isNumber)
        return node.path(key).decimalValue().movePointRight(2).longValueExact()
    }
    private fun identifier(node: JsonNode, key: String) = safeId(required(node, key))
    private fun safeId(id: String): String { require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))); return id }
    private fun required(node: JsonNode, key: String): String = node.path(key).takeIf { it.isTextual }?.asText()
        ?.takeIf { it.isNotBlank() } ?: error("Incomplete provider response")
    private fun encoded(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun send(key: String, method: String, path: String, body: Any? = null): JsonNode {
        val request = HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + path)).timeout(Duration.ofSeconds(30))
            .header("access_token", key).header("User-Agent", "Saqz-Receivables/1.0").header("Accept", "application/json")
            .header("Content-Type", "application/json").method(method, if (body == null) HttpRequest.BodyPublishers.noBody()
                else HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            catch (_: Exception) { error("Payment provider result unknown") }
        if (response.statusCode() == 400 && runCatching { mapper.readTree(response.body()).path("errors").isArray }.getOrDefault(false))
            throw DefinitivePaymentRejection()
        check(response.statusCode() in 200..299) { "Payment provider result unknown" }
        return mapper.readTree(response.body())
    }
}
