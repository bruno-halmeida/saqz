package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Deliberately has no provider mutation capability. */
class HttpAsaasCredentialRecovery(
    private val baseUrl: URI,
    private val platformApiKey: String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build(),
) : CredentialRecoveryProvider {
    private val mapper = jacksonObjectMapper()
    init {
        require(baseUrl.scheme == "https" || (baseUrl.scheme == "http" && baseUrl.host in setOf("localhost", "127.0.0.1", "::1")))
        require(baseUrl.userInfo == null && baseUrl.query == null && baseUrl.fragment == null)
        require(http.followRedirects() == HttpClient.Redirect.NEVER)
    }

    override fun matches(command: CredentialRecoveryCommand, target: CredentialRecoveryTarget, credential: RecoveryCredential): Boolean {
        require(target.cpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}")))
        val accounts = get("/accounts?cpfCnpj=${target.cpfCnpj}&offset=0&limit=100", platformApiKey)
        // A truncated/ambiguous list is not authority to attach a credential, even if one candidate matches.
        val account = singleComplete(accounts) ?: return false
        if (account.path("id").asText() != command.providerAccountId ||
            account.path("walletId").asText() != command.walletId ||
            legalIdentity(account.path("cpfCnpj")) != target.cpfCnpj) return false
        val owner = get("/myAccount/commercialInfo/", credential.value)
        if (legalIdentity(owner.path("cpfCnpj")) != target.cpfCnpj) return false
        val wallet = singleComplete(get("/wallets/", credential.value)) ?: return false
        return wallet.path("id").asText() == command.walletId
    }

    private fun singleComplete(node: JsonNode): JsonNode? {
        if (!node.path("hasMore").isBoolean || node.path("hasMore").booleanValue() ||
            !node.path("totalCount").isIntegralNumber || node.path("totalCount").bigIntegerValue() != BigInteger.ONE ||
            !node.path("data").isArray || node.path("data").size() != 1) return null
        return node.path("data")[0]
    }

    private fun legalIdentity(node: JsonNode): String? = node.takeIf(JsonNode::isTextual)?.asText()
        ?.takeIf { it.matches(Regex("[0-9. /-]{11,20}")) }?.filter(Char::isDigit)

    private fun get(path: String, apiKey: String): JsonNode = try {
        val request = HttpRequest.newBuilder(URI(baseUrl.toString().trimEnd('/') + path))
            .timeout(Duration.ofSeconds(30)).header("access_token", apiKey)
            .header("User-Agent", "Saqz-Credential-Recovery/1.0").header("Accept", "application/json").GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200)
        mapper.readTree(response.body()) ?: error("Empty response")
    } catch (_: Exception) {
        throw IllegalStateException("Credential recovery provider unavailable")
    }
}
