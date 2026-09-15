package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.FinancialFeeProvider
import br.com.saqz.receivables.application.FinancialFeesUnavailable
import br.com.saqz.receivables.application.ProviderPaymentFee
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/** GET /myAccount/fees authenticates as the account that will receive the payment. */
class HttpAsaasFees(
    private val baseUrl: URI,
    private val apiKey: (UUID?) -> String?,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build(),
) : FinancialFeeProvider {
    private val mapper = jacksonObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)

    init {
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
    }

    override fun current(methods: Set<PaymentMethod>, at: Instant, accountId: UUID?): Map<PaymentMethod, ProviderPaymentFee> {
        if (methods.isEmpty()) return emptyMap()
        return try {
            val key = apiKey(accountId)?.takeIf(String::isNotBlank) ?: throw FinancialFeesUnavailable()
            val request = HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + "/myAccount/fees/"))
                .timeout(Duration.ofSeconds(10)).header("access_token", key)
                .header("User-Agent", "Saqz-Receivables/1.0").header("Accept", "application/json").GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) throw FinancialFeesUnavailable()
            val payment = mapper.readTree(response.body())["payment"] ?: throw FinancialFeesUnavailable()
            methods.associateWith { method ->
                when (method) {
                    PaymentMethod.PIX -> pix(payment["pix"] ?: throw FinancialFeesUnavailable(), at)
                    PaymentMethod.CARD -> card(payment["creditCard"] ?: throw FinancialFeesUnavailable(), at)
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw FinancialFeesUnavailable()
        } catch (_: Exception) { throw FinancialFeesUnavailable() }
    }

    private fun card(node: JsonNode, at: Instant): ProviderPaymentFee {
        val rate = if (discountActive(node, at)) node.decimal("discountOneInstallmentPercentage")
            ?: node.requiredDecimal("oneInstallmentPercentage") else node.requiredDecimal("oneInstallmentPercentage")
        // Saqz issues credit card payments without installments.
        return ProviderPaymentFee(rate.movePointLeft(2), cents(node.requiredDecimal("operationValue")))
    }

    private fun pix(node: JsonNode, at: Instant): ProviderPaymentFee {
        val fixed = if (discountActive(node, at)) node.decimal("fixedFeeValueWithDiscount")
            ?: node.decimal("fixedFeeValue") else node.decimal("fixedFeeValue")
        val percentage = node.decimal("percentageFee")
        if (fixed == null && percentage == null) throw FinancialFeesUnavailable()
        // Pix contracts use either a fixed tariff or a bounded percentage; ambiguous contracts cannot be quoted.
        if (fixed != null && fixed.signum() > 0 && percentage != null && percentage.signum() > 0) throw FinancialFeesUnavailable()
        val proportional = percentage != null && percentage.signum() > 0
        val fee = ProviderPaymentFee((percentage ?: BigDecimal.ZERO).movePointLeft(2), cents(fixed ?: BigDecimal.ZERO),
            if (proportional) cents(node.requiredDecimal("minimumFeeValue")) else 0,
            if (proportional) cents(node.requiredDecimal("maximumFeeValue")) else null)
        val allowance = node.decimal("monthlyCreditsWithoutFee")?.longValueExact() ?: 0
        val used = node.decimal("creditsReceivedOfCurrentMonth")?.longValueExact()
        if (allowance > 0 && used == null) throw FinancialFeesUnavailable()
        return if (allowance > 0 && used!! < allowance) ProviderPaymentFee(BigDecimal.ZERO, 0) else fee
    }

    private fun discountActive(node: JsonNode, at: Instant): Boolean {
        val expiration = node["discountExpiration"]?.takeUnless(JsonNode::isNull) ?: return false
        if (!expiration.isTextual) throw FinancialFeesUnavailable()
        val instant = try { Instant.parse(expiration.textValue()) } catch (_: DateTimeParseException) {
            LocalDateTime.parse(expiration.textValue(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.of("America/Sao_Paulo")).toInstant()
        }
        return at < instant
    }

    private fun JsonNode.decimal(field: String): BigDecimal? {
        val value = this[field]?.takeUnless(JsonNode::isNull) ?: return null
        if (!value.isNumber || value.decimalValue().signum() < 0) throw FinancialFeesUnavailable()
        return value.decimalValue()
    }
    private fun JsonNode.requiredDecimal(field: String) = decimal(field) ?: throw FinancialFeesUnavailable()
    private fun cents(value: BigDecimal) = value.movePointRight(2).longValueExact()
}
