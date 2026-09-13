package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

class HttpAsaasWallet(
    private val baseUrl: URI,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build(),
) : WalletProvider {
    private val mapper = jacksonObjectMapper()

    init {
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
    }

    override fun balance(apiKey: String): Pair<Long, Long> {
        val available = positiveCents(sendGet("/finance/balance", apiKey).requiredDecimal("balance"))
        val pending = positiveCents(sendGet("/finance/payment/statistics?status=PENDING", apiKey).requiredDecimal("value"))
        return available to pending
    }

    override fun statement(apiKey: String, offset: Int, limit: Int): WalletStatementPage {
        require(offset >= 0 && limit in 1..100)
        val response = sendGet("/financialTransactions?offset=$offset&limit=$limit&order=asc", apiKey)
        val data = response["data"]?.takeIf(JsonNode::isArray) ?: throw WalletProviderFailure()
        val items = data.map { node ->
            WalletStatementItem(requiredText(node, "id"), requiredText(node, "type"),
                cents(node.requiredDecimal("value")), cents(node.requiredDecimal("balance")),
                LocalDate.parse(requiredText(node, "date")), requiredText(node, "description"))
        }
        val hasMore = response["hasMore"]?.takeIf(JsonNode::isBoolean)?.asBoolean() ?: throw WalletProviderFailure()
        return WalletStatementPage(items, if (hasMore) Math.addExact(offset, items.size) else null)
    }

    override fun withdraw(apiKey: String, operationId: UUID, amountCents: Long,
                          destination: BankDestinationDetails): ProviderWithdrawalResult {
        val body = mapOf(
            "value" to BigDecimal.valueOf(amountCents, 2),
            "bankAccount" to mapOf(
                "bank" to mapOf("code" to destination.bankCode),
                "ownerName" to destination.ownerName,
                "cpfCnpj" to destination.cpfCnpj,
                "agency" to destination.agency,
                "account" to destination.account,
                "accountDigit" to destination.accountDigit,
                "bankAccountType" to when (destination.accountType) {
                    BankAccountType.CHECKING -> "CONTA_CORRENTE"
                    BankAccountType.SAVINGS -> "CONTA_POUPANCA"
                },
            ),
            "externalReference" to operationId.toString(),
        )
        val request = request("/transfers", apiKey).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build()
        val response = try { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        catch (_: Exception) { return ProviderWithdrawalResult.Unknown }
        if (response.statusCode() !in 200..299) return rejection(response.body())
        return try { transfer(mapper.readTree(response.body())) } catch (_: Exception) { ProviderWithdrawalResult.Unknown }
    }

    override fun recoverWithdrawal(apiKey: String, operationId: UUID): ProviderWithdrawalResult {
        val reference = URLEncoder.encode(operationId.toString(), UTF_8)
        val response = try { sendGet("/transfers?externalReference=$reference&limit=2&offset=0", apiKey) }
        catch (_: Exception) { return ProviderWithdrawalResult.Unknown }
        val data = response["data"]?.takeIf(JsonNode::isArray) ?: return ProviderWithdrawalResult.Unknown
        if (data.size() != 1) return ProviderWithdrawalResult.Unknown
        return try { transfer(data[0]) } catch (_: Exception) { ProviderWithdrawalResult.Unknown }
    }

    private fun transfer(node: JsonNode): ProviderWithdrawalResult {
        val id = requiredText(node, "id")
        val fee = positiveCents(node.requiredDecimal("transferFee"))
        return when (requiredText(node, "status")) {
            "PENDING", "BANK_PROCESSING" -> ProviderWithdrawalResult.Known(id, WithdrawalStatus.PROCESSING, fee)
            "DONE" -> ProviderWithdrawalResult.Known(id, WithdrawalStatus.COMPLETED, fee)
            "CANCELLED" -> ProviderWithdrawalResult.Known(id, WithdrawalStatus.CANCELLED, fee)
            "FAILED" -> ProviderWithdrawalResult.Rejected(providerFailureCode(node["failReason"]?.asText()), id)
            else -> ProviderWithdrawalResult.Unknown
        }
    }

    private fun rejection(body: String): ProviderWithdrawalResult.Rejected {
        val safe = try {
            mapper.readTree(body)["errors"]?.takeIf(JsonNode::isArray)?.joinToString(" ") {
                listOfNotNull(it["code"]?.asText(), it["description"]?.asText()).joinToString(" ")
            }.orEmpty()
        } catch (_: Exception) { "" }
        return ProviderWithdrawalResult.Rejected(providerFailureCode(safe))
    }

    private fun providerFailureCode(value: String?): String = if (value.orEmpty().contains("saldo", ignoreCase = true) ||
        value.orEmpty().contains("balance", ignoreCase = true)) "INSUFFICIENT_BALANCE" else "PROVIDER_RESTRICTED"

    private fun sendGet(path: String, apiKey: String): JsonNode {
        val response = try { http.send(request(path, apiKey).GET().build(), HttpResponse.BodyHandlers.ofString()) }
        catch (_: Exception) { throw WalletProviderFailure() }
        if (response.statusCode() !in 200..299) throw WalletProviderFailure()
        return try { mapper.readTree(response.body()) } catch (_: Exception) { throw WalletProviderFailure() }
    }

    private fun request(path: String, apiKey: String): HttpRequest.Builder {
        require(apiKey.isNotBlank())
        return HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + path)).timeout(Duration.ofSeconds(30))
            .header("access_token", apiKey).header("User-Agent", "Saqz-Receivables/1.0").header("Accept", "application/json")
    }

    private fun JsonNode.requiredDecimal(field: String): BigDecimal = this[field]?.takeIf(JsonNode::isNumber)?.decimalValue()
        ?: throw WalletProviderFailure()
    private fun requiredText(node: JsonNode, field: String): String = node[field]?.takeIf(JsonNode::isTextual)?.asText()
        ?.takeIf(String::isNotBlank) ?: throw WalletProviderFailure()
    private fun positiveCents(value: BigDecimal): Long = cents(value).also { if (it < 0) throw WalletProviderFailure() }
    private fun cents(value: BigDecimal): Long = try {
        value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    } catch (_: ArithmeticException) { throw WalletProviderFailure() }
}
