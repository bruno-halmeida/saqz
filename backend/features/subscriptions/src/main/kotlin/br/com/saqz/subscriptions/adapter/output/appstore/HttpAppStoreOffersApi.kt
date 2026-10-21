package br.com.saqz.subscriptions.adapter.output.appstore

import br.com.saqz.subscriptions.application.AppStoreOfferCodeSpec
import br.com.saqz.subscriptions.application.AppStoreOffersApi
import br.com.saqz.subscriptions.application.AppStorePricePoint
import br.com.saqz.subscriptions.application.CouponStoreException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Credencial da App Store Connect API (chave de equipe: issuer, key id e o `.p8`). Nunca vai para log. */
class AppStoreConnectCredentials(
    val issuerId: String,
    val keyId: String,
    privateKeyPem: String,
) {
    internal val privateKey: PrivateKey? = privateKeyPem.takeIf { it.isNotBlank() }?.let(::parsePkcs8)

    val configured: Boolean get() = issuerId.isNotBlank() && keyId.isNotBlank() && privateKey != null

    override fun toString(): String = "AppStoreConnectCredentials(keyId=***)"

    private companion object {
        fun parsePkcs8(pem: String): PrivateKey {
            val der = Base64.getDecoder().decode(
                pem.replace("\\n", "\n").lines().filterNot { it.startsWith("-----") }.joinToString("").trim(),
            )
            return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
        }
    }
}

/**
 * App Store Connect API v1. Token JWT ES256 (`aud` appstoreconnect-v1, validade de 20 min, a
 * máxima da Apple), renovado antes de vencer.
 */
class HttpAppStoreOffersApi(
    private val appAppleId: String,
    private val credentials: AppStoreConnectCredentials,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clock: Clock = Clock.systemUTC(),
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : AppStoreOffersApi {
    private val mapper = ObjectMapper()
    private val subscriptionIds = ConcurrentHashMap<String, String>()

    @Volatile private var token: Pair<String, Long>? = null

    override fun subscriptionId(productId: String): String = subscriptionIds[productId] ?: run {
        val groups = getJson("v1/apps/${encode(appAppleId)}/subscriptionGroups?limit=50", "subscriptionGroups")
        val id = groups.path("data").firstNotNullOfOrNull { group ->
            val subs = getJson(
                "v1/subscriptionGroups/${encode(group.path("id").asText())}/subscriptions?filter%5BproductId%5D=${encode(productId)}",
                "subscriptions",
            )
            subs.path("data").firstOrNull { it.path("attributes").path("productId").asText() == productId }?.path("id")?.asText()
        } ?: throw CouponStoreException("App Store: assinatura $productId não encontrada no App Store Connect", permanent = true)
        subscriptionIds[productId] = id
        id
    }

    override fun brazilPricePoints(subscriptionId: String): List<AppStorePricePoint> {
        val points = mutableListOf<AppStorePricePoint>()
        var next: String? = "v1/subscriptions/${encode(subscriptionId)}/pricePoints?filter%5Bterritory%5D=BRA&limit=8000"
        while (next != null) {
            val page = getJson(next, "pricePoints")
            page.path("data").forEach { point ->
                val price = point.path("attributes").path("customerPrice").asText(null)?.toBigDecimalOrNull() ?: return@forEach
                points += AppStorePricePoint(point.path("id").asText(), price.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact())
            }
            next = page.path("links").path("next").asText(null)?.removePrefix(baseUrl)?.removePrefix("/")
        }
        return points
    }

    override fun createOfferCode(spec: AppStoreOfferCodeSpec): String {
        val body = mapper.createObjectNode()
        body.putObject("data").apply {
            put("type", "subscriptionOfferCodes")
            putObject("attributes").apply {
                put("name", spec.name)
                putArray("customerEligibilities").apply { spec.customerEligibilities.forEach(::add) }
                put("offerEligibility", "STACK_WITH_INTRO_OFFERS")
                put("duration", spec.duration)
                put("offerMode", "PAY_AS_YOU_GO")
                put("numberOfPeriods", spec.numberOfPeriods)
            }
            putObject("relationships").apply {
                relation("subscription", "subscriptions", spec.subscriptionId)
                putObject("prices").putArray("data").addObject().put("type", "subscriptionOfferCodePrices").put("id", "\${price-bra}")
            }
        }
        body.putArray("included").addObject().apply {
            put("type", "subscriptionOfferCodePrices")
            put("id", "\${price-bra}")
            putObject("relationships").apply {
                relation("territory", "territories", "BRA")
                relation("subscriptionPricePoint", "subscriptionPricePoints", spec.pricePointId)
            }
        }
        return send("POST", "v1/subscriptionOfferCodes", body, "subscriptionOfferCodes.create").path("data").path("id").asText()
    }

    override fun createCustomCode(offerCodeId: String, customCode: String, numberOfCodes: Int): String {
        val body = mapper.createObjectNode()
        body.putObject("data").apply {
            put("type", "subscriptionOfferCodeCustomCodes")
            putObject("attributes").apply {
                put("customCode", customCode)
                put("numberOfCodes", numberOfCodes)
            }
            putObject("relationships").relation("offerCode", "subscriptionOfferCodes", offerCodeId)
        }
        return send("POST", "v1/subscriptionOfferCodeCustomCodes", body, "subscriptionOfferCodeCustomCodes.create")
            .path("data").path("id").asText()
    }

    override fun deactivateOfferCode(offerCodeId: String) {
        send("PATCH", "v1/subscriptionOfferCodes/${encode(offerCodeId)}", inactive("subscriptionOfferCodes", offerCodeId), "subscriptionOfferCodes.update")
    }

    override fun deactivateCustomCode(customCodeId: String) {
        send(
            "PATCH",
            "v1/subscriptionOfferCodeCustomCodes/${encode(customCodeId)}",
            inactive("subscriptionOfferCodeCustomCodes", customCodeId),
            "subscriptionOfferCodeCustomCodes.update",
        )
    }

    private fun inactive(type: String, id: String): ObjectNode = mapper.createObjectNode().apply {
        putObject("data").apply {
            put("type", type)
            put("id", id)
            putObject("attributes").put("active", false)
        }
    }

    private fun ObjectNode.relation(name: String, type: String, id: String) {
        putObject(name).putObject("data").put("type", type).put("id", id)
    }

    private fun getJson(path: String, what: String): JsonNode = send("GET", path, null, what)

    private fun send(method: String, path: String, body: JsonNode?, what: String): JsonNode {
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(it)) }
            ?: HttpRequest.BodyPublishers.noBody()
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/$path"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer ${bearer()}")
            .header("Content-Type", "application/json")
            .method(method, publisher)
            .build()
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (failure: IOException) {
            throw CouponStoreException("App Store Connect API fora do ar", permanent = false, failure)
        }
        val status = response.statusCode()
        if (status in 200..299) return if (response.body().isNullOrBlank()) mapper.createObjectNode() else mapper.readTree(response.body())
        val detail = runCatching {
            mapper.readTree(response.body()).path("errors").firstOrNull()?.let { it.path("detail").asText(null) ?: it.path("title").asText(null) }
        }.getOrNull()?.take(MAX_MESSAGE)
        // 401/403 costuma ser chave errada ou sem papel: não adianta insistir em loop, mas a credencial muda no deploy.
        val permanent = status in setOf(400, 404, 409, 422)
        throw CouponStoreException("App Store: $what respondeu $status${detail?.let { ": $it" }.orEmpty()}", permanent)
    }

    private fun bearer(): String {
        val now = clock.instant().epochSecond
        token?.let { (value, expiresAt) -> if (expiresAt - now > TOKEN_MARGIN_SECONDS) return value }
        val key = credentials.privateKey
        if (!credentials.configured || key == null) {
            throw CouponStoreException("App Store: credenciais da App Store Connect API não configuradas", permanent = false)
        }
        val exp = now + TOKEN_LIFETIME_SECONDS
        val header = b64(mapper.writeValueAsBytes(mapOf("alg" to "ES256", "kid" to credentials.keyId, "typ" to "JWT")))
        val claims = b64(mapper.writeValueAsBytes(mapOf("iss" to credentials.issuerId, "iat" to now, "exp" to exp, "aud" to "appstoreconnect-v1")))
        val signer = Signature.getInstance("SHA256withECDSAinP1363Format").apply {
            initSign(key)
            update("$header.$claims".toByteArray(StandardCharsets.US_ASCII))
        }
        val jwt = "$header.$claims.${b64(signer.sign())}"
        token = jwt to exp
        return jwt
    }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.appstoreconnect.apple.com"
        private const val TOKEN_LIFETIME_SECONDS = 1_200L
        private const val TOKEN_MARGIN_SECONDS = 60L
        private const val MAX_MESSAGE = 300
    }
}
