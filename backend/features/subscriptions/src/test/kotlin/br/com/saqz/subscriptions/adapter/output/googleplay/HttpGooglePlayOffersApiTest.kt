package br.com.saqz.subscriptions.adapter.output.googleplay

import br.com.saqz.subscriptions.application.CouponStoreException
import br.com.saqz.subscriptions.application.GooglePlayOfferSpec
import br.com.saqz.subscriptions.application.GooglePlayOfferState
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpGooglePlayOffersApiTest {
    private val server = MockWebServer().apply { start() }
    private val api = HttpGooglePlayOffersApi("app.saqz", { "token" }, server.url("").toString().trimEnd('/'))
    private val base = "/androidpublisher/v3/applications/app.saqz/subscriptions/app.saqz.organizador/basePlans/mensal/offers"

    @AfterEach
    fun stop() = server.shutdown()

    @Test
    fun `cria a oferta só no Brasil, sem regra de público, e ativa`() {
        server.enqueue(MockResponse().setBody("""{"state":"DRAFT"}"""))
        server.enqueue(MockResponse().setBody("""{"state":"ACTIVE"}"""))

        val state = api.create(GooglePlayOfferSpec("app.saqz.organizador", "mensal", "cupom-volei30", 3, "P1M", 0.3, listOf("cupom", "volei")))
        assertEquals(GooglePlayOfferState.DRAFT, state)
        assertEquals(GooglePlayOfferState.ACTIVE, api.activate("app.saqz.organizador", "mensal", "cupom-volei30"))

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("$base?offerId=cupom-volei30&regionsVersion.version=2022%2F02", create.path)
        assertEquals("Bearer token", create.getHeader("Authorization"))
        val body = ObjectMapper().readTree(create.body.readUtf8())
        assertFalse(body.has("targeting"))
        val phase = body.path("phases").single()
        assertEquals(3, phase.path("recurrenceCount").asInt())
        assertEquals("P1M", phase.path("duration").asText())
        assertEquals("BR", phase.path("regionalConfigs").single().path("regionCode").asText())
        assertEquals(0.3, phase.path("regionalConfigs").single().path("relativeDiscount").asDouble())
        assertTrue(body.path("regionalConfigs").single().path("newSubscriberAvailability").asBoolean())
        assertEquals(listOf("cupom", "volei"), body.path("offerTags").map { it.path("tag").asText() })
        assertEquals("$base/cupom-volei30:activate", server.takeRequest().path)
    }

    @Test
    fun `get devolve null em 404 e erro de validação é permanente`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(api.get("app.saqz.organizador", "mensal", "cupom-x"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Offer id is invalid"}}"""))
        val failure = assertFailsWith<CouponStoreException> { api.deactivate("app.saqz.organizador", "mensal", "cupom-x") }
        assertTrue(failure.permanent)
        assertTrue(failure.message!!.contains("Offer id is invalid"))
        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(assertFailsWith<CouponStoreException> { api.get("app.saqz.organizador", "mensal", "cupom-x") }.permanent)
    }
}
