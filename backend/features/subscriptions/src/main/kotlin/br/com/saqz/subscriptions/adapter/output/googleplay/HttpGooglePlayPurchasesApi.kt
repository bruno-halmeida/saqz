package br.com.saqz.subscriptions.adapter.output.googleplay

import br.com.saqz.subscriptions.application.GooglePlayLookup
import br.com.saqz.subscriptions.application.GooglePlayPurchasesApi
import br.com.saqz.subscriptions.application.GooglePlayUnavailableException
import br.com.saqz.subscriptions.domain.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.GooglePlayState
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

/**
 * Google Play Developer API v3 via REST. A credencial (conta de serviço convidada no Play
 * Console) chega pronta como [accessToken] — quem monta é o bootstrap, com o Google Auth.
 */
class HttpGooglePlayPurchasesApi(
    private val packageName: String,
    private val accessToken: () -> String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : GooglePlayPurchasesApi {
    private val mapper = ObjectMapper()

    override fun subscription(purchaseToken: String): GooglePlayLookup {
        val response = send(
            HttpRequest.newBuilder(uri("purchases/subscriptionsv2/tokens/${encode(purchaseToken)}")).GET(),
        )
        return when (response.statusCode()) {
            in 200..299 -> GooglePlayLookup.Found(parse(purchaseToken, mapper.readTree(response.body())))
            // 400 "Invalid Value" (token malformado), 404 e 410 (token que não existe mais).
            400, 404, 410 -> GooglePlayLookup.NotFound
            else -> throw GooglePlayUnavailableException("subscriptionsv2.get respondeu ${response.statusCode()}")
        }
    }

    override fun acknowledge(productId: String, purchaseToken: String) {
        val response = send(
            HttpRequest.newBuilder(
                uri("purchases/subscriptions/${encode(productId)}/tokens/${encode(purchaseToken)}:acknowledge"),
            ).POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json"),
        )
        if (response.statusCode() !in 200..299) {
            throw GooglePlayUnavailableException("acknowledge respondeu ${response.statusCode()}")
        }
    }

    private fun send(request: HttpRequest.Builder): HttpResponse<String> {
        val token = try {
            accessToken()
        } catch (failure: IOException) {
            throw GooglePlayUnavailableException("sem token de acesso do Google", failure)
        }
        return try {
            http.send(
                request.timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer $token").build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (failure: IOException) {
            throw GooglePlayUnavailableException("Google Play Developer API fora do ar", failure)
        }
    }

    private fun uri(path: String) = URI.create("$baseUrl/androidpublisher/v3/applications/${encode(packageName)}/$path")

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun parse(purchaseToken: String, root: JsonNode): GooglePlayPurchase {
        val line = root.path("lineItems").firstOrNull() ?: mapper.createObjectNode()
        val canceled = root.path("canceledStateContext")
        return GooglePlayPurchase(
            purchaseToken = purchaseToken,
            productId = line.text("productId").orEmpty(),
            basePlanId = line.path("offerDetails").text("basePlanId"),
            state = GooglePlayState.fromApi(root.text("subscriptionState")) ?: GooglePlayState.PENDING,
            expiresAt = line.text("expiryTime")?.let(Instant::parse),
            autoRenew = line.path("autoRenewingPlan").path("autoRenewEnabled").takeIf { it.isBoolean }?.booleanValue(),
            canceledAt = (
                canceled.path("userInitiatedCancellation").text("cancelTime")
                    ?: canceled.path("systemInitiatedCancellation").text("cancelTime")
                ).let { it?.let(Instant::parse) },
            obfuscatedAccountId = root.path("externalAccountIdentifiers").text("obfuscatedExternalAccountId"),
            linkedPurchaseToken = root.text("linkedPurchaseToken"),
            latestOrderId = line.text("latestSuccessfulOrderId") ?: root.text("latestOrderId"),
            acknowledged = root.text("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
            testPurchase = root.has("testPurchase"),
        )
    }

    private fun JsonNode.text(field: String): String? = path(field).takeIf { it.isTextual }?.asText()?.takeIf(String::isNotBlank)

    companion object {
        const val DEFAULT_BASE_URL = "https://androidpublisher.googleapis.com"
        const val SCOPE = "https://www.googleapis.com/auth/androidpublisher"
    }
}
