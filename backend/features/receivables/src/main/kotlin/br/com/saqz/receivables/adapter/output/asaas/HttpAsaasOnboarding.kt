package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.FinancialDocument
import br.com.saqz.receivables.application.FinancialOnboardingProvider
import br.com.saqz.receivables.application.LegalRegistration
import br.com.saqz.receivables.application.ProviderAccount
import br.com.saqz.receivables.domain.RegistrationStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

class HttpAsaasOnboarding(
    private val baseUrl: URI,
    private val platformApiKey: String,
    private val baasEnabled: Boolean,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build(),
) : FinancialOnboardingProvider {
    override val canCreateAccounts: Boolean get() = baasEnabled
    private val mapper = jacksonObjectMapper()

    init {
        require(baseUrl.scheme == "https" ||
            (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
    }

    override fun create(registration: LegalRegistration): ProviderAccount {
        check(baasEnabled) { "Financial BaaS onboarding is disabled" }
        require(registration.isValid())
        val body = mapOf(
            "name" to registration.name, "email" to registration.email, "cpfCnpj" to registration.cpfCnpj,
            "mobilePhone" to registration.mobilePhone, "incomeValue" to BigDecimal.valueOf(registration.incomeCents, 2),
            "address" to registration.address, "addressNumber" to registration.addressNumber,
            "province" to registration.province, "postalCode" to registration.postalCode,
            "birthDate" to registration.birthDate?.toString(), "companyType" to registration.companyType,
        ).filterValues { it != null }
        val response = send("/accounts", platformApiKey,
            HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)), "application/json")
        return ProviderAccount(required(response, "id"), required(response, "walletId"), required(response, "apiKey"))
    }

    override fun documents(apiKey: String): List<FinancialDocument> {
        val response = send("/myAccount/documents", apiKey)
        require(response.path("data").isArray) { "Financial provider response invalid" }
        return response["data"].map {
            FinancialDocument(required(it, "id"), required(it, "type"), required(it, "status"),
                it["onboardingUrl"]?.takeUnless(JsonNode::isNull)?.asText()?.takeIf(String::isNotBlank)?.also { link ->
                    require(URI(link).scheme == "https") { "Financial provider link invalid" }
                }, it["description"]?.takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank))
        }
    }

    override fun status(apiKey: String): RegistrationStatus =
        when (send("/myAccount/status", apiKey).path("general").asText()) {
            "APPROVED" -> RegistrationStatus.APPROVED
            "AWAITING_APPROVAL" -> RegistrationStatus.UNDER_REVIEW
            "PENDING" -> RegistrationStatus.CORRECTION_REQUIRED
            "REJECTED" -> RegistrationStatus.REJECTED
            else -> throw IllegalStateException("Financial provider status unknown")
        }

    override fun upload(apiKey: String, documentId: String, type: String, contentType: String, bytes: ByteArray) {
        require(documentId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(type.matches(Regex("[A-Z_]{1,64}")))
        require(contentType in setOf("application/pdf", "image/jpeg", "image/png"))
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024)
        val boundary = "saqz-${UUID.randomUUID()}"
        val before = "--$boundary\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\n$type\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"documentFile\"; filename=\"document\"\r\n" +
            "Content-Type: $contentType\r\n\r\n"
        val body = before.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        send("/myAccount/documents/$documentId", apiKey, HttpRequest.BodyPublishers.ofByteArray(body),
            "multipart/form-data; boundary=$boundary")
    }

    private fun send(path: String, apiKey: String, body: HttpRequest.BodyPublisher? = null,
                     contentType: String = "application/json"): JsonNode {
        val builder = HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + path))
            .timeout(Duration.ofSeconds(30)).header("access_token", apiKey)
            .header("User-Agent", "Saqz-Receivables/1.0").header("Accept", "application/json")
        if (body == null) builder.GET() else builder.header("Content-Type", contentType).POST(body)
        val response = try { http.send(builder.build(), HttpResponse.BodyHandlers.ofString()) }
        catch (_: Exception) { throw IllegalStateException("Financial provider result unavailable") }
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Financial provider request unsuccessful")
        }
        return try { mapper.readTree(response.body()) }
        catch (_: Exception) { throw IllegalStateException("Financial provider response invalid") }
    }

    private fun required(node: JsonNode, field: String): String = node[field]?.takeIf(JsonNode::isTextual)
        ?.asText()?.takeIf(String::isNotBlank) ?: throw IllegalStateException("Financial provider response incomplete")
}
