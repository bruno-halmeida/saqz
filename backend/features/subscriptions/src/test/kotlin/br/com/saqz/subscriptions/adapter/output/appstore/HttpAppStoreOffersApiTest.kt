package br.com.saqz.subscriptions.adapter.output.appstore

import br.com.saqz.subscriptions.application.AppStoreOfferCodeSpec
import br.com.saqz.subscriptions.application.CouponStoreException
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpAppStoreOffersApiTest {
    private val server = MockWebServer().apply { start() }
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(keys.private.encoded) + "\n-----END PRIVATE KEY-----"
    private val url = server.url("").toString().trimEnd('/')
    private val api = HttpAppStoreOffersApi("123", AppStoreConnectCredentials("issuer-1", "KEY1", pem), url)
    private val mapper = ObjectMapper()

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `token ES256 com issuer, key id e aud da App Store Connect`() {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"grp"}]}"""))
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"sub-9","attributes":{"productId":"app.saqz.organizador.mensal"}}]}"""))

        assertEquals("sub-9", api.subscriptionId("app.saqz.organizador.mensal"))

        val first = server.takeRequest()
        assertEquals("/v1/apps/123/subscriptionGroups?limit=50", first.path)
        val jwt = first.getHeader("Authorization")!!.removePrefix("Bearer ").split('.')
        val header = mapper.readTree(Base64.getUrlDecoder().decode(jwt[0]))
        val claims = mapper.readTree(Base64.getUrlDecoder().decode(jwt[1]))
        assertEquals("ES256", header.path("alg").asText())
        assertEquals("KEY1", header.path("kid").asText())
        assertEquals("issuer-1", claims.path("iss").asText())
        assertEquals("appstoreconnect-v1", claims.path("aud").asText())
        assertTrue(claims.path("exp").asLong() - claims.path("iat").asLong() <= 1_200)
        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format").apply {
            initVerify(keys.public); update("${jwt[0]}.${jwt[1]}".toByteArray())
        }
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(jwt[2])))
        assertEquals("/v1/subscriptionGroups/grp/subscriptions?filter%5BproductId%5D=app.saqz.organizador.mensal", server.takeRequest().path)
    }

    @Test
    fun `price points de BRA em centavos`() {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"pp1","attributes":{"customerPrice":"41.9"}},{"id":"pp2","attributes":{"customerPrice":"44.90"}}],"links":{}}"""))
        assertEquals(listOf(4_190L, 4_490L), api.brazilPricePoints("sub-9").map { it.customerPriceCents })
        assertEquals("/v1/subscriptions/sub-9/pricePoints?filter%5Bterritory%5D=BRA&limit=8000", server.takeRequest().path)
    }

    @Test
    fun `cria código de oferta pay as you go só em BRA e código personalizado com validade`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"data":{"id":"oc-1"}}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"data":{"id":"cc-1"}}"""))

        assertEquals("oc-1", api.createOfferCode(AppStoreOfferCodeSpec("sub-9", "VOLEI30OM", listOf("NEW"), "ONE_MONTH", 3, "pp1")))
        assertEquals("cc-1", api.createCustomCode("oc-1", "VOLEI30OM", 50, LocalDate.parse("2026-11-30")))

        val offer = mapper.readTree(server.takeRequest().body.readUtf8())
        val attributes = offer.path("data").path("attributes")
        assertEquals("PAY_AS_YOU_GO", attributes.path("offerMode").asText())
        assertEquals(3, attributes.path("numberOfPeriods").asInt())
        assertEquals("ONE_MONTH", attributes.path("duration").asText())
        assertEquals(listOf("NEW"), attributes.path("customerEligibilities").map { it.asText() })
        assertEquals("sub-9", offer.path("data").path("relationships").path("subscription").path("data").path("id").asText())
        val price = offer.path("included").single()
        assertEquals(offer.path("data").path("relationships").path("prices").path("data").single().path("id").asText(), price.path("id").asText())
        assertEquals("BRA", price.path("relationships").path("territory").path("data").path("id").asText())
        assertEquals("pp1", price.path("relationships").path("subscriptionPricePoint").path("data").path("id").asText())

        val custom = server.takeRequest()
        assertEquals("/v1/subscriptionOfferCodeCustomCodes", custom.path)
        val customAttrs = mapper.readTree(custom.body.readUtf8()).path("data").path("attributes")
        assertEquals("VOLEI30OM", customAttrs.path("customCode").asText())
        assertEquals(50, customAttrs.path("numberOfCodes").asInt())
        assertEquals("2026-11-30", customAttrs.path("expirationDate").asText())
    }

    @Test
    fun `desativar manda active false e sem credencial não chama a Apple`() {
        server.enqueue(MockResponse().setBody("{}"))
        api.deactivateCustomCode("cc-1")
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertFalse(mapper.readTree(request.body.readUtf8()).path("data").path("attributes").path("active").asBoolean(true))

        val semChave = HttpAppStoreOffersApi("123", AppStoreConnectCredentials("", "", "lixo"), url)
        assertFalse(assertFailsWith<CouponStoreException> { semChave.deactivateOfferCode("oc-1") }.permanent)
        assertEquals(1, server.requestCount)
    }
}
