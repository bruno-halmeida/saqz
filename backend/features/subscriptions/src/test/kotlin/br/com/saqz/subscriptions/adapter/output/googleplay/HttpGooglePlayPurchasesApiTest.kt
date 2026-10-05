package br.com.saqz.subscriptions.adapter.output.googleplay

import br.com.saqz.subscriptions.application.GooglePlayLookup
import br.com.saqz.subscriptions.application.GooglePlayUnavailableException
import br.com.saqz.subscriptions.domain.GooglePlayState
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpGooglePlayPurchasesApiTest {
    private val server = MockWebServer().apply { start() }
    private val api = HttpGooglePlayPurchasesApi("app.saqz", { "token-de-acesso" }, server.url("").toString().trimEnd('/'))

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `reads subscriptionsv2 into a purchase`() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "kind": "androidpublisher#subscriptionPurchaseV2",
                  "startTime": "2026-10-05T12:00:00Z",
                  "subscriptionState": "SUBSCRIPTION_STATE_CANCELED",
                  "linkedPurchaseToken": "antigo",
                  "acknowledgementState": "ACKNOWLEDGEMENT_STATE_PENDING",
                  "externalAccountIdentifiers": { "obfuscatedExternalAccountId": "conta-1" },
                  "testPurchase": {},
                  "canceledStateContext": { "userInitiatedCancellation": { "cancelTime": "2026-10-06T10:00:00Z" } },
                  "lineItems": [{
                    "productId": "app.saqz.organizador",
                    "expiryTime": "2026-11-05T12:00:00.123Z",
                    "autoRenewingPlan": { "autoRenewEnabled": false },
                    "offerDetails": { "basePlanId": "mensal" },
                    "latestSuccessfulOrderId": "GPA.3333-1111-2222-44444"
                  }]
                }
                """.trimIndent(),
            ),
        )

        val purchase = assertIs<GooglePlayLookup.Found>(api.subscription("tok/1")).purchase

        val request = server.takeRequest()
        assertEquals("/androidpublisher/v3/applications/app.saqz/purchases/subscriptionsv2/tokens/tok%2F1", request.path)
        assertEquals("Bearer token-de-acesso", request.getHeader("Authorization"))
        assertEquals("app.saqz.organizador", purchase.productId)
        assertEquals("mensal", purchase.basePlanId)
        assertEquals(GooglePlayState.CANCELED, purchase.state)
        assertEquals(Instant.parse("2026-11-05T12:00:00.123Z"), purchase.expiresAt)
        assertEquals(false, purchase.autoRenew)
        assertEquals(Instant.parse("2026-10-06T10:00:00Z"), purchase.canceledAt)
        assertEquals("conta-1", purchase.obfuscatedAccountId)
        assertEquals("antigo", purchase.linkedPurchaseToken)
        assertEquals("GPA.3333-1111-2222-44444", purchase.latestOrderId)
        assertEquals(false, purchase.acknowledged)
        assertTrue(purchase.testPurchase)
    }

    @Test
    fun `tokens Google does not know are not found`() {
        listOf(400, 404, 410).forEach { server.enqueue(MockResponse().setResponseCode(it)) }

        repeat(3) { assertEquals(GooglePlayLookup.NotFound, api.subscription("tok")) }
    }

    @Test
    fun `server errors and missing permissions are unavailable, not invalid`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(401))

        assertFailsWith<GooglePlayUnavailableException> { api.subscription("tok") }
        assertFailsWith<GooglePlayUnavailableException> { api.subscription("tok") }
    }

    @Test
    fun `acknowledges through the v1 subscriptions endpoint`() {
        server.enqueue(MockResponse().setResponseCode(204))

        api.acknowledge("app.saqz.organizador", "tok")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/androidpublisher/v3/applications/app.saqz/purchases/subscriptions/app.saqz.organizador/tokens/tok:acknowledge", request.path)
    }

    @Test
    fun `a failed acknowledgement is unavailable so the caller retries`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertFailsWith<GooglePlayUnavailableException> { api.acknowledge("app.saqz.organizador", "tok") }
    }
}
