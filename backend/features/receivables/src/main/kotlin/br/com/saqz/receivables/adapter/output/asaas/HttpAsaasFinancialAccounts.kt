package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpAsaasFinancialAccounts(
    private val baseUrl: URI,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build(),
) : FinancialAccountsProvider {
    private val mapper = jacksonObjectMapper()
    init {
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
    }

    override fun readCommercialInfo(apiKey: String) = decode(send(apiKey, null))

    override fun updateCommercialInfo(apiKey: String, commercial: CommercialRegistration): CommercialRegistration {
        val c = commercial.correction
        val body = linkedMapOf<String, Any?>(
            "personType" to commercial.personType,
            "cpfCnpj" to commercial.cpfCnpj,
            "birthDate" to commercial.birthDate,
            "companyType" to commercial.companyType,
            "companyName" to commercial.companyName,
            "incomeValue" to BigDecimal.valueOf(c.incomeCents, 2),
            "taxRegime" to commercial.taxRegime,
            "email" to c.email,
            "phone" to c.phone,
            "mobilePhone" to c.mobilePhone,
            "site" to c.site,
            "postalCode" to c.postalCode,
            "address" to c.address,
            "addressNumber" to c.addressNumber,
            "complement" to c.complement,
            "province" to c.province,
        )
        return decode(send(apiKey, mapper.writeValueAsString(body)))
    }

    private fun send(apiKey: String, body: String?): JsonNode {
        require(apiKey.isNotBlank())
        val request = HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + "/myAccount/commercialInfo/"))
            .timeout(Duration.ofSeconds(30)).header("access_token", apiKey)
            .header("Accept", "application/json").header("User-Agent", "Saqz-Receivables/1.0")
        if (body == null) request.GET() else request.header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        val response = try { http.send(request.build(), HttpResponse.BodyHandlers.ofString()) }
        catch (_: Exception) { throw FinancialProviderUnavailable() }
        if (response.statusCode() in 400..499) throw FinancialProviderRejected()
        if (response.statusCode() !in 200..299) throw FinancialProviderUnavailable()
        return try { mapper.readTree(response.body()) } catch (_: Exception) { throw FinancialProviderUnavailable() }
    }

    private fun decode(node: JsonNode): CommercialRegistration {
        fun required(name: String) = node[name]?.takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
            ?: throw FinancialProviderUnavailable()
        fun optional(name: String) = node[name]?.takeUnless(JsonNode::isNull)?.takeIf(JsonNode::isTextual)?.asText()
        val income = node["incomeValue"]?.takeIf(JsonNode::isNumber)?.decimalValue()
            ?.movePointRight(2)?.longValueExact() ?: throw FinancialProviderUnavailable()
        val correction = RegistrationCorrection(required("email"), optional("phone"), required("mobilePhone"),
            optional("site"), income, required("postalCode").filter(Char::isDigit), required("address"),
            required("addressNumber"), optional("complement"), required("province"))
        if (!correction.isValid()) throw FinancialProviderUnavailable()
        return CommercialRegistration(required("personType"), required("cpfCnpj").filter(Char::isDigit),
            optional("birthDate"), optional("companyType"), optional("companyName"), optional("taxRegime"), correction)
    }
}
