package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorGroupReceivablesGatewayTest {
    @Test fun accountsAndStateAreAuthenticatedWithoutUserIdentityOrDefaultMethods() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            assertEquals(HttpMethod.Get, request.method)
            if (request.url.encodedPath.endsWith("accounts")) {
                assertEquals("", request.url.encodedQuery)
                respond(envelope("[]"), HttpStatusCode.OK, headers)
            } else {
                assertEquals("accountId=account", request.url.encodedQuery)
                respond(envelope("""{"state":$configuration,"permissions":{"READ":{"allowed":true},"CANCEL":{"allowed":true}}}"""),
                    HttpStatusCode.OK, headers)
            }
        })
        assertEquals(SaqzResult.Success(emptyList()), gateway.accounts())
        val status = assertIs<SaqzResult.Success<ReceiptStatus>>(gateway.status("group", "account")).value
        assertFalse(status.state.enabled)
        assertTrue(status.permissions.getValue("CANCEL").allowed)
    }

    @Test fun previewReadsNumericRatesAndServerPricesAndTerms() = runTest {
        val gateway = gateway(MockEngine { request ->
            if (request.method == HttpMethod.Get) respond(envelope("""{"version":"v1","content":"Termos publicados"}"""), HttpStatusCode.OK, headers)
            else {
                val command = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(setOf("PIX"), command.getValue("methods").jsonArray.map { it.jsonPrimitive.content }.toSet())
                assertFalse(command.getValue("accepted").jsonPrimitive.boolean)
                respond(envelope("""{"state":$configuration,"schedules":[{"method":"PIX","termsVersion":"v1",
                    "providerRate":0.01,"providerFixedCents":0,"providerMinimumCents":29,"providerMaximumCents":199,"commissionRate":0.02,"commissionFixedCents":20}],
                    "prices":[{"kind":"GAME","quotes":[{"method":"PIX","baseCents":1000,"feesCents":69,
                    "totalCents":1069,"expectedNetCents":1000}]}],"permissions":{"ACTIVATE_GROUP":{"allowed":true}},
                    "fingerprint":"${"a".repeat(64)}"}""", "request"), HttpStatusCode.OK, headers)
            }
        })
        val review = assertIs<SaqzResult.Success<ReceiptReview>>(gateway.preview("group", command)).value
        assertEquals("0.01", review.schedules.single().providerRate)
        assertEquals(29L, review.schedules.single().providerMinimumCents)
        assertEquals(199L, review.schedules.single().providerMaximumCents)
        assertEquals(1069L, review.prices.single().totalCents)
        assertEquals("Termos publicados", assertIs<SaqzResult.Success<ReceiptTerms>>(gateway.terms("v1")).value.content)
    }

    @Test fun activationRetryPreservesExactBodyAndOriginalFingerprint() = runTest {
        val bodies = mutableListOf<String>()
        val gateway = gateway(MockEngine { request ->
            bodies.add((request.body as TextContent).text)
            if (bodies.size == 1) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(envelope(configuration, "request"), HttpStatusCode.OK, headers)
        })
        val accepted = command.copy(fingerprint = "a".repeat(64), accepted = true)
        assertIs<SaqzResult.Success<ReceiptConfiguration>>(gateway.activate("group", accepted))
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        assertEquals("a".repeat(64), Json.parseToJsonElement(bodies[0]).jsonObject.getValue("fingerprint").jsonPrimitive.content)
    }

    @Test fun errorsAndInvalidEnvelopeFailClosed() = runTest {
        listOf(403 to ReceiptError.DENIED, 404 to ReceiptError.DENIED, 409 to ReceiptError.STALE, 400 to ReceiptError.INVALID).forEach { (code, error) ->
            val gateway = gateway(MockEngine { respond("""{"error":"CONFLICT","requestId":"request"}""", HttpStatusCode.fromValue(code), headers) })
            assertEquals(SaqzResult.Failure(error), gateway.activate("group", command))
        }
        listOf("{}", envelope(configuration, "wrong-request"), """{"value":null,"requestId":"request"}""").forEach { response ->
            val gateway = gateway(MockEngine { respond(response, HttpStatusCode.OK, headers) })
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.activate("group", command))
        }
    }

    private fun gateway(engine: MockEngine) = KtorGroupReceivablesGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) { completion(TokenResult.Available("token")) }
        }, object : SessionInvalidator { override fun invalidate() = Unit },
    ))
    private val command = ReceiptCommand("request", "account", setOf(ReceiptMethod.PIX))
    private val configuration = """{"accountId":"account","groupId":"group","enabled":false,"pixEnabled":false,"cardEnabled":false}"""
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private fun envelope(value: String, request: String = "server") = """{"value":$value,"requestId":"$request"}"""
}
