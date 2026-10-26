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

class KtorAthletePaymentsGatewayTest {
    @Test fun meReadsOpenChargesAndKeepsRecurrenceNullBeforeF3() = runTest {
        val gateway = KtorAthletePaymentsGateway(client(MockEngine { request ->
            assertEquals("/api/receivables/groups/ceret/me", request.url.encodedPath)
            assertEquals(HttpMethod.Get, request.method)
            respond(envelope(me), HttpStatusCode.OK, headers)
        }))
        val value = assertIs<SaqzResult.Success<AthleteReceivables>>(gateway.me("ceret")).value
        assertTrue(value.active)
        assertEquals("Marcos Lima", value.payeeName)
        assertEquals(setOf(ReceiptMethod.PIX, ReceiptMethod.CARD), value.methods)
        assertEquals(listOf("c1", "c2"), value.openCharges.map { it.chargeId })
        assertEquals("Rafa", value.openCharges[1].guestName)
        assertEquals(OpenChargeKind.GAME, value.openCharges[0].kind)
        assertTrue(value.openCharges[0].overdue)
        assertEquals(4000, value.totalCents)
        assertNull(value.recurrence)
    }

    @Test fun meReadsTheRecurrenceOfF3() = runTest {
        val body = me.replace("\"recurrence\":null", """"recurrence":{"status":"REFUSED","amountCents":10000,
            "nextChargeDate":"2026-10-10","refusedChargeId":"c1","history":[{"month":"2026-08-01","amountCents":10000,
            "paidAt":"2026-08-10T12:00:00Z","atAdhesion":true}]}""")
        val gateway = KtorAthletePaymentsGateway(client(MockEngine { respond(envelope(body), HttpStatusCode.OK, headers) }))
        val recurrence = assertIs<SaqzResult.Success<AthleteReceivables>>(gateway.me("ceret")).value.recurrence
        assertEquals(RecurrenceStatus.REFUSED, recurrence?.status)
        assertEquals("c1", recurrence?.refusedChargeId)
        assertEquals(RecurrenceCharge("2026-08", 10000, "2026-08-10", true), recurrence?.history?.single())
    }

    @Test fun totalThatDoesNotMatchTheChargesFailsClosed() = runTest {
        val gateway = KtorAthletePaymentsGateway(client(MockEngine {
            respond(envelope(me.replace("\"totalCents\":4000", "\"totalCents\":3000")), HttpStatusCode.OK, headers)
        }))
        assertEquals(SaqzResult.Failure(PaymentError.INVALID), gateway.me("ceret"))
    }

    @Test fun createCodeSendsRequestIdMethodAndChargesAndReadsThePix() = runTest {
        val gateway = KtorAthletePaymentsGateway(client(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/receivables/groups/ceret/payment-codes", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("req", body.getValue("requestId").jsonPrimitive.content)
            assertEquals("PIX", body.getValue("method").jsonPrimitive.content)
            assertEquals(listOf("c1", "c2"), body.getValue("chargeIds").jsonArray.map { it.jsonPrimitive.content })
            respond(envelope(pixCode, "req"), HttpStatusCode.OK, headers)
        }))
        val code = assertIs<SaqzResult.Success<PaymentCode>>(gateway.createCode("ceret", "req", ReceiptMethod.PIX, listOf("c1", "c2"))).value
        assertEquals("000201pix", code.pixPayload)
        assertEquals(PaymentCodeStatus.PENDING, code.status)
        assertNull(code.checkoutUrl)
    }

    @Test fun createCodeMapsTheContractErrors() = runTest {
        listOf(
            Triple(409, "DOCUMENT_REQUIRED", PaymentError.DOCUMENT_REQUIRED),
            Triple(409, "CHARGE_NOT_PAYABLE", PaymentError.CHARGE_NOT_PAYABLE),
            Triple(503, "PIX_NOT_READY", PaymentError.PIX_NOT_READY),
            Triple(403, "RECEIVABLES_INACTIVE", PaymentError.UNAVAILABLE),
        ).forEach { (status, code, expected) ->
            val gateway = KtorAthletePaymentsGateway(client(MockEngine {
                respond("""{"error":"$code","requestId":"req"}""", HttpStatusCode.fromValue(status), headers)
            }))
            assertEquals(SaqzResult.Failure(expected), gateway.createCode("ceret", "req", ReceiptMethod.CARD, listOf("c1")), code)
        }
    }

    @Test fun routeThatDoesNotExistYetIsUnavailableNotACrash() = runTest {
        val gateway = KtorAthletePaymentsGateway(client(MockEngine { respond("", HttpStatusCode.NotFound) }))
        assertEquals(SaqzResult.Failure(PaymentError.UNAVAILABLE), gateway.me("ceret"))
    }

    @Test fun cardCodeOnlyAcceptsTheHostedAsaasPage() = runTest {
        val card = """{"codeId":"k","method":"CARD","amountCents":4000,"chargeIds":["c1"],"status":"PENDING","pix":null,
            "card":{"checkoutUrl":"https://evil.example/pay"}}"""
        val gateway = KtorAthletePaymentsGateway(client(MockEngine { respond(envelope(card, "req"), HttpStatusCode.OK, headers) }))
        assertEquals(SaqzResult.Failure(PaymentError.INVALID), gateway.createCode("ceret", "req", ReceiptMethod.CARD, listOf("c1")))
    }

    @Test fun codeIsPolledByIdAndReadsThePaidStatus() = runTest {
        val gateway = KtorAthletePaymentsGateway(client(MockEngine { request ->
            assertEquals("/api/receivables/payment-codes/k", request.url.encodedPath)
            respond(envelope(pixCode.replace("PENDING", "PAID")), HttpStatusCode.OK, headers)
        }))
        assertEquals(PaymentCodeStatus.PAID, assertIs<SaqzResult.Success<PaymentCode>>(gateway.code("k")).value.status)
    }

    @Test fun documentIsValidatedLocallyAndNeverReadBack() = runTest {
        var calls = 0
        val gateway = KtorAthletePaymentsGateway(client(MockEngine { request ->
            calls++
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/receivables/me/document", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("52998224725", body.getValue("cpf").jsonPrimitive.content)
            respond(envelope("""{"hasDocument":true}""", "req"), HttpStatusCode.OK, headers)
        }))
        assertEquals(SaqzResult.Failure(PaymentError.INVALID_DOCUMENT), gateway.saveDocument("req", "11111111111"))
        assertEquals(0, calls)
        assertEquals(SaqzResult.Success(Unit), gateway.saveDocument("req", "52998224725"))
    }

    @Test fun autopayRoutesUseTheContractPathsAndReason() = runTest {
        val paths = mutableListOf<String>()
        val gateway = KtorAutopayGateway(client(MockEngine { request ->
            paths += "${request.method.value} ${request.url.encodedPath}"
            when {
                request.url.encodedPath.endsWith("/cancel") -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals("PREFER_PIX", body.getValue("reason").jsonPrimitive.content)
                    respond(envelope("{}", "req"), HttpStatusCode.OK, headers)
                }
                request.url.encodedPath.endsWith("/recurrences") -> respond(envelope(
                    """[{"memberUserId":"bia","status":"ACTIVE"},{"memberUserId":"lucas","status":"REFUSED"},
                    {"memberUserId":"x","status":"WHATEVER"}]"""), HttpStatusCode.OK, headers)
                else -> respond(envelope("""{"checkoutUrl":"https://sandbox.asaas.com/c/abc"}""", "req"), HttpStatusCode.OK, headers)
            }
        }))
        assertEquals(SaqzResult.Success("https://sandbox.asaas.com/c/abc"), gateway.start("ceret", "req"))
        assertEquals(SaqzResult.Success(Unit), gateway.cancel("ceret", "req", AutopayCancelReason.PREFER_PIX))
        assertEquals(SaqzResult.Success("https://sandbox.asaas.com/c/abc"), gateway.changeCard("ceret", "req"))
        val members = assertIs<SaqzResult.Success<List<MemberAutopay>>>(gateway.members("ceret", "acc")).value
        assertEquals(listOf(MemberAutopay("bia", MemberAutopayStatus.ACTIVE), MemberAutopay("lucas", MemberAutopayStatus.REFUSED)), members)
        assertEquals(listOf(
            "POST /api/receivables/groups/ceret/recurrence",
            "POST /api/receivables/groups/ceret/recurrence/cancel",
            "POST /api/receivables/groups/ceret/recurrence/card",
            "GET /api/receivables/groups/ceret/recurrences",
        ), paths)
    }

    @Test fun settleLinkResolvesTheGameOrTheMonth() = runTest {
        val gateway = KtorSettleLinkGateway(client(MockEngine { request ->
            assertEquals("/api/receivables/settle-links/AbC123", request.url.encodedPath)
            respond(envelope("""{"groupId":"ceret","gameId":null,"billingMonth":"2026-10-01","groupName":"Vôlei do CERET"}"""),
                HttpStatusCode.OK, headers)
        }))
        assertEquals(SaqzResult.Success(SettleTarget("ceret", null, "2026-10", "Vôlei do CERET")), gateway.resolve("AbC123"))
    }

    @Test fun cpfCheckDigits() {
        assertTrue("52998224725".isValidCpf())
        assertFalse("52998224724".isValidCpf())
        assertFalse("00000000000".isValidCpf())
        assertFalse("5299822472".isValidCpf())
    }

    private val me = """{"active":true,"payeeName":"Marcos Lima","methods":["PIX","CARD"],"pixReady":true,
        "hasDocument":false,"openCharges":[{"chargeId":"c1","kind":"GAME","gameId":"g1","billingMonth":null,
        "guestName":null,"dueDate":"2026-07-21","payableCents":2000,"overdue":true},{"chargeId":"c2","kind":"GAME",
        "gameId":"g2","guestName":"Rafa","dueDate":"2026-07-28","payableCents":2000,"overdue":false}],
        "totalCents":4000,"recurrence":null}"""
    private val pixCode = """{"codeId":"k","method":"PIX","amountCents":4000,"chargeIds":["c1","c2"],"status":"PENDING",
        "pix":{"payload":"000201pix","image":"iVBOR"},"card":null}"""
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private fun envelope(value: String, request: String = "server") = """{"value":$value,"requestId":"$request"}"""
}

internal fun client(engine: MockEngine) = AuthenticatedNetworkClient(
    NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
    object : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) { completion(TokenResult.Available("token")) }
    }, object : SessionInvalidator { override fun invalidate() = Unit },
)
