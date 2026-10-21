package br.com.saqz.subscriptions.adapter.output.googleplay

import br.com.saqz.subscriptions.application.CouponStoreException
import br.com.saqz.subscriptions.application.GooglePlayOfferSpec
import br.com.saqz.subscriptions.application.GooglePlayOfferState
import br.com.saqz.subscriptions.application.GooglePlayOffersApi
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Ofertas dos planos base pela Android Publisher API v3 (mesma conta de serviço das compras, com
 * "Gerenciar presença na loja"). Oferta sem `targeting` = elegibilidade decidida pelo app/backend.
 */
class HttpGooglePlayOffersApi(
    private val packageName: String,
    private val accessToken: () -> String,
    private val baseUrl: String = HttpGooglePlayPurchasesApi.DEFAULT_BASE_URL,
    private val regionsVersion: String = REGIONS_VERSION,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : GooglePlayOffersApi {
    private val mapper = ObjectMapper()

    override fun get(productId: String, basePlanId: String, offerId: String): GooglePlayOfferState? {
        val response = send(HttpRequest.newBuilder(uri(productId, basePlanId, "/${encode(offerId)}")).GET())
        if (response.statusCode() == 404) return null
        return state(response, "offers.get")
    }

    override fun create(spec: GooglePlayOfferSpec): GooglePlayOfferState {
        val body = mapper.createObjectNode().apply {
            put("packageName", packageName)
            put("productId", spec.productId)
            put("basePlanId", spec.basePlanId)
            put("offerId", spec.offerId)
            putArray("phases").addObject().apply {
                put("recurrenceCount", spec.cycles)
                put("duration", spec.period)
                putArray("regionalConfigs").addObject().apply {
                    put("regionCode", REGION)
                    put("relativeDiscount", spec.relativeDiscount)
                }
            }
            putArray("regionalConfigs").addObject().apply {
                put("regionCode", REGION)
                put("newSubscriberAvailability", true)
            }
            putArray("offerTags").apply { spec.tags.forEach { addObject().put("tag", it) } }
        }
        val query = "?offerId=${encode(spec.offerId)}&regionsVersion.version=${encode(regionsVersion)}"
        val response = send(
            HttpRequest.newBuilder(uri(spec.productId, spec.basePlanId, query))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .header("Content-Type", "application/json"),
        )
        return state(response, "offers.create")
    }

    override fun activate(productId: String, basePlanId: String, offerId: String) =
        action(productId, basePlanId, offerId, "activate")

    override fun deactivate(productId: String, basePlanId: String, offerId: String) =
        action(productId, basePlanId, offerId, "deactivate")

    private fun action(productId: String, basePlanId: String, offerId: String, verb: String): GooglePlayOfferState {
        val response = send(
            HttpRequest.newBuilder(uri(productId, basePlanId, "/${encode(offerId)}:$verb"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json"),
        )
        return state(response, "offers.$verb")
    }

    private fun state(response: HttpResponse<String>, what: String): GooglePlayOfferState {
        val status = response.statusCode()
        if (status in 200..299) {
            return when (mapper.readTree(response.body()).path("state").asText()) {
                "DRAFT" -> GooglePlayOfferState.DRAFT
                "ACTIVE" -> GooglePlayOfferState.ACTIVE
                "INACTIVE" -> GooglePlayOfferState.INACTIVE
                else -> GooglePlayOfferState.UNKNOWN
            }
        }
        val message = errorMessage(response.body())
        // 401/403/429/5xx podem passar (token, permissão recém-dada, cota); 400/404/409 é o pedido.
        val permanent = status in setOf(400, 404, 409, 422)
        throw CouponStoreException("Google Play: $what respondeu $status${message?.let { ": $it" }.orEmpty()}", permanent)
    }

    private fun errorMessage(body: String?): String? = runCatching {
        mapper.readTree(body).path("error").path("message").asText(null)
    }.getOrNull()?.take(MAX_MESSAGE)

    private fun send(request: HttpRequest.Builder): HttpResponse<String> {
        val token = try {
            accessToken()
        } catch (failure: IOException) {
            throw CouponStoreException("Google Play: sem token de acesso do Google", permanent = false, failure)
        }
        return try {
            http.send(
                request.timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer $token").build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (failure: IOException) {
            throw CouponStoreException("Google Play Developer API fora do ar", permanent = false, failure)
        }
    }

    private fun uri(productId: String, basePlanId: String, suffix: String) = URI.create(
        "$baseUrl/androidpublisher/v3/applications/${encode(packageName)}/subscriptions/${encode(productId)}" +
            "/basePlans/${encode(basePlanId)}/offers$suffix",
    )

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val REGION = "BR"

        /** Versão de regiões do Play (RegionsVersion); a "2022/02" é a que vale para o Brasil. */
        const val REGIONS_VERSION = "2022/02"
        private const val MAX_MESSAGE = 300
    }
}
