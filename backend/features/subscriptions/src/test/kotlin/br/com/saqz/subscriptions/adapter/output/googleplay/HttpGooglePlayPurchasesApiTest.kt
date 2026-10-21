package br.com.saqz.subscriptions.adapter.output.googleplay

import br.com.saqz.subscriptions.application.GooglePlayLookup
import br.com.saqz.subscriptions.application.GooglePlayOrderAmount
import br.com.saqz.subscriptions.application.GooglePlayOrdersRejectedException
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
    fun `a deferred plan change keeps the current item and records the plan that comes next`() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
                  "linkedPurchaseToken": "antigo",
                  "acknowledgementState": "ACKNOWLEDGEMENT_STATE_PENDING",
                  "lineItems": [
                    {
                      "productId": "app.saqz.titular",
                      "autoRenewingPlan": { "autoRenewEnabled": true },
                      "offerDetails": { "basePlanId": "mensal" }
                    },
                    {
                      "productId": "app.saqz.organizador",
                      "expiryTime": "2026-11-05T12:00:00Z",
                      "autoRenewingPlan": { "autoRenewEnabled": false },
                      "offerDetails": { "basePlanId": "mensal" },
                      "deferredItemReplacement": { "productId": "app.saqz.titular" }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val purchase = assertIs<GooglePlayLookup.Found>(api.subscription("novo")).purchase

        assertEquals("app.saqz.organizador", purchase.productId)
        assertEquals(Instant.parse("2026-11-05T12:00:00Z"), purchase.expiresAt)
        assertEquals("app.saqz.titular", purchase.pendingProductId)
        assertEquals("mensal", purchase.pendingBasePlanId)
        assertEquals(true, purchase.autoRenew)
        assertEquals("antigo", purchase.linkedPurchaseToken)
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

    @Test
    fun `orders batchGet reads total, tax, currency and state of each order`() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "orders": [
                    {
                      "orderId": "GPA.1",
                      "state": "PROCESSED",
                      "total": { "currencyCode": "BRL", "units": "59", "nanos": 900000000 },
                      "tax": { "currencyCode": "BRL", "units": "5", "nanos": 390000000 },
                      "lineItems": [{ "productId": "app.saqz.organizador", "total": { "currencyCode": "BRL", "units": "59", "nanos": 900000000 } }]
                    },
                    {
                      "orderId": "GPA.2",
                      "state": "REFUNDED",
                      "lineItems": [{ "total": { "currencyCode": "USD", "units": "9", "nanos": 995000000 } }]
                    },
                    { "orderId": "GPA.3", "state": "PENDING" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val orders = api.orders(listOf("GPA.1", "GPA.2", "GPA.3"))

        val request = server.takeRequest()
        assertEquals(
            "/androidpublisher/v3/applications/app.saqz/orders:batchGet?orderIds=GPA.1&orderIds=GPA.2&orderIds=GPA.3",
            request.path,
        )
        assertEquals("GET", request.method)
        assertEquals("Bearer token-de-acesso", request.getHeader("Authorization"))
        assertEquals(
            listOf(
                GooglePlayOrderAmount("GPA.1", 5_990, 539, "BRL", "PROCESSED"),
                // Sem Order.total, vale o total da única linha; sem imposto informado, nulo.
                GooglePlayOrderAmount("GPA.2", 1_000, null, "USD", "REFUNDED"),
            ),
            orders,
        )
    }

    @Test
    fun `orders batchGet with an unknown id is rejected and a server error is unavailable`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertFailsWith<GooglePlayOrdersRejectedException> { api.orders(listOf("GPA.x")) }
        server.enqueue(MockResponse().setResponseCode(403))
        assertFailsWith<GooglePlayUnavailableException> { api.orders(listOf("GPA.x")) }
        server.enqueue(MockResponse().setResponseCode(503))
        assertFailsWith<GooglePlayUnavailableException> { api.orders(listOf("GPA.x")) }
    }
}
