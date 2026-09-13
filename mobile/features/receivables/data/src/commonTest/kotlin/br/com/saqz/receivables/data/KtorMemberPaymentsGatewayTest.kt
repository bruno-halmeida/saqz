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

class KtorMemberPaymentsGatewayTest {
    @Test fun ownOrdersUseAuthenticatedPaginationAndPreserveApprovedSnapshot() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/receivables/orders", request.url.encodedPath)
            assertEquals(mapOf("after" to listOf("previous")), request.url.parameters.entries().associate { it.toPair() })
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            respond(envelope("""{"orders":[$orderJson],"nextCursor":"order"}"""), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(MemberPaymentPage(listOf(order), "order")), gateway.orders("previous"))
    }

    @Test fun emptyHistoryHasNoCursorAndSendsNoIdentity() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals("", request.url.encodedQuery)
            respond(envelope("""{"orders":[],"nextCursor":null}"""), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(MemberPaymentPage(emptyList(), null)), gateway.orders())
    }

    @Test fun detailPreservesPixExpirationAndRefundStatusDespiteHistoricalMilestones() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/receivables/orders/order", request.url.encodedPath)
            respond(envelope(detailJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(MemberPaymentDetail(order, listOf(instrument))), gateway.detail("order"))
        val refunded = gateway(MockEngine { respond(envelope(detailJson.replace("ACTIVE", "REFUNDED")
            .replace("ISSUED", "REFUNDED").replace("false", "true")), HttpStatusCode.OK, headers) })
        val result = assertIs<SaqzResult.Success<MemberPaymentDetail>>(refunded.detail("order")).value
        assertEquals("REFUNDED", result.order.status)
        assertEquals("REFUNDED", result.instruments.single().status)
        assertTrue(result.instruments.single().available)
        assertTrue(result.instruments.single().confirmed)
    }

    @Test fun instrumentRetryKeepsExactAcceptedBodyAndHostedCardResult() = runTest {
        val bodies = mutableListOf<String>()
        val gateway = gateway(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/receivables/orders/order/instruments", request.url.encodedPath)
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            bodies += (request.body as TextContent).text
            if (bodies.size == 1) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(envelope(instrumentJson.replace("PIX", "CARD"), "request"), HttpStatusCode.OK, headers)
        })
        val cardQuote = quote.copy(method = ReceiptMethod.CARD)
        assertEquals(SaqzResult.Success(instrument.copy(quote = cardQuote)),
            gateway.instrument(order.copy(quotes = listOf(cardQuote)), command.copy(method = ReceiptMethod.CARD)))
        assertEquals(2, bodies.size)
        assertEquals(bodies[0], bodies[1])
        assertEquals(Json.parseToJsonElement("""{"requestId":"request","method":"CARD","fingerprint":"$fingerprint","accepted":true,
            "payer":{"name":"Pessoa Teste","cpfCnpj":"12345678909"}}"""), Json.parseToJsonElement(bodies[0]))
    }

    @Test fun reconcileRetriesOnlyReconcileAndUnknownNeverCreatesAnotherInstrument() = runTest {
        val bodies = mutableListOf<String>()
        val gateway = gateway(MockEngine { request ->
            assertEquals("/api/receivables/orders/order/reconcile", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            bodies += (request.body as TextContent).text
            if (bodies.size == 1) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(envelope(detailJson.replace("ACTIVE", "UNKNOWN"), "request"), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(MemberPaymentDetail(order, listOf(instrument.copy(status = "UNKNOWN")))),
            gateway.reconcile("order", "request"))
        assertEquals(listOf("""{"requestId":"request"}""", """{"requestId":"request"}"""), bodies)
    }

    @Test fun documentedHttpFailuresRemainTypedForReadAndWrite() = runTest {
        for ((code, expected) in listOf(401 to ReceiptError.SIGNED_OUT, 403 to ReceiptError.DENIED,
            404 to ReceiptError.DENIED, 409 to ReceiptError.STALE, 400 to ReceiptError.INVALID)) {
            val gateway = gateway(MockEngine { respond("{}", HttpStatusCode.fromValue(code), headers) })
            assertEquals(SaqzResult.Failure(expected), gateway.detail("order"))
            assertEquals(SaqzResult.Failure(expected), gateway.instrument(order, command))
        }
        val unavailable = gateway(MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable, headers) })
        assertEquals(SaqzResult.Failure(ReceiptError.UNAVAILABLE), unavailable.detail("order"))
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), unavailable.instrument(order, command))
    }

    @Test fun invalidWriteEnvelopeAndMismatchedInstrumentCannotBecomeSuccess() = runTest {
        for (response in listOf("{}", "{", envelope(instrumentJson, "other"), envelope("null", "request"),
            envelope(instrumentJson.replace("\"order\"", "\"foreign\""), "request"),
            envelope(instrumentJson.replace("\"account\"", "\"foreign\""), "request"),
            envelope(instrumentJson.replace("PIX", "CARD"), "request"),
            envelope(instrumentJson.replace("1061", "9999"), "request"),
            envelope(instrumentJson.replace("terms-v1", "terms-v2"), "request"))) {
            val gateway = gateway(MockEngine { respond(response, HttpStatusCode.OK, headers) })
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.instrument(order, command), response)
        }
    }

    @Test fun invalidReadAndReconciliationResourcesFailClosed() = runTest {
        for (value in listOf(detailJson.replace("\"order\"", "\"foreign\""), detailJson.replace("1061", "1000"),
            detailJson.replace("\"orderId\":\"order\"", "\"orderId\":\"foreign\""))) {
            val gateway = gateway(MockEngine { respond(envelope(value, "request"), HttpStatusCode.OK, headers) })
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.detail("order"))
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.reconcile("order", "request"))
        }
        val gateway = gateway(MockEngine { respond(envelope("""{"orders":[],"nextCursor":"foreign"}"""), HttpStatusCode.OK, headers) })
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.orders())
    }

    @Test fun detailAndReconcileRejectValidInstrumentSnapshotsThatDifferFromApprovedOrder() = runTest {
        for (changed in listOf(instrumentJson.replace("terms-v1", "terms-v2"),
            instrumentJson.replace("PIX", "CARD"), instrumentJson.replace("\"account\"", "\"foreign\""))) {
            val response = """{"order":$orderJson,"instruments":[$changed]}"""
            val gateway = gateway(MockEngine { respond(envelope(response, "request"), HttpStatusCode.OK, headers) })
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.detail("order"))
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.reconcile("order", "request"))
        }
    }

    @Test fun detailAndReconcileRejectAnotherOrderEvenWithInternallyConsistentInstruments() = runTest {
        val foreignOrder = orderJson.replace("\"id\":\"order\"", "\"id\":\"foreign\"")
        val foreignInstrument = instrumentJson.replace("\"orderId\":\"order\"", "\"orderId\":\"foreign\"")
        val response = """{"order":$foreignOrder,"instruments":[$foreignInstrument]}"""
        val gateway = gateway(MockEngine { respond(envelope(response, "request"), HttpStatusCode.OK, headers) })
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.detail("order"))
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.reconcile("order", "request"))
    }

    @Test fun detailAndReconcileRejectDuplicateInstrumentIdentities() = runTest {
        val response = """{"order":$orderJson,"instruments":[$instrumentJson,$instrumentJson]}"""
        val gateway = gateway(MockEngine { respond(envelope(response, "request"), HttpStatusCode.OK, headers) })
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.detail("order"))
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.reconcile("order", "request"))
    }

    @Test fun timeoutAndConnectionLossRemainUncertainForWritesWithoutChangingTheCommand() = runTest {
        for (timeout in listOf(true, false)) {
            val bodies = mutableListOf<String>()
            val gateway = gateway(MockEngine { request ->
                if (request.method == HttpMethod.Post) bodies += (request.body as TextContent).text
                if (timeout) throw io.ktor.client.plugins.HttpRequestTimeoutException(request)
                else throw io.ktor.util.network.UnresolvedAddressException()
            })
            assertEquals(SaqzResult.Failure(ReceiptError.NETWORK), gateway.detail("order"))
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), gateway.instrument(order, command))
            assertEquals(4, bodies.size)
            assertEquals(1, bodies.toSet().size)
            assertEquals("request", Json.parseToJsonElement(bodies.first()).jsonObject.getValue("requestId").jsonPrimitive.content)
        }
    }

    @Test fun wrongFingerprintAndUnapprovedMethodCannotSendInstrumentCommand() = runTest {
        val gateway = gateway(MockEngine { error("Invalid command must not reach the server") })
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.instrument(order, command.copy(fingerprint = "wrong")))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.instrument(order, command.copy(method = ReceiptMethod.CARD)))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), gateway.instrument(order, command.copy(accepted = false)))
    }

    private fun gateway(engine: MockEngine) = KtorMemberPaymentsGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) { completion(TokenResult.Available("token")) }
        }, object : SessionInvalidator { override fun invalidate() = Unit },
    ))
    private val fingerprint = "a".repeat(64)
    private val quote = MemberPaymentQuote("schedule", "terms-v1", ReceiptMethod.PIX, 1000, 61, 1061, 1000, 20, 41)
    private val order = MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-20", "ISSUED", listOf(quote), fingerprint)
    private val instrument = MemberPaymentInstrument("instrument", "account", "order", quote, "ACTIVE", "payment", null,
        "pix-copy", "base64-image", "https://asaas.com/i/payment", false, false, false, false, "2026-09-20T12:00:00Z")
    private val command = MemberPaymentCommand("request", ReceiptMethod.PIX, fingerprint, true, MemberPaymentPayer("Pessoa Teste", "12345678909"))
    private val quoteJson = """{"feeScheduleId":"schedule","termsVersion":"terms-v1","method":"PIX","baseCents":1000,
        "feesCents":61,"totalCents":1061,"expectedNetCents":1000,"commissionCents":20,"providerFeeCents":41}"""
    private val orderJson = """{"id":"order","accountId":"account","chargeId":"charge","groupId":"group","payerId":"payer",
        "dueDate":"2026-09-20","status":"ISSUED","quotes":[$quoteJson],"fingerprint":"$fingerprint"}"""
    private val instrumentJson = """{"id":"instrument","accountId":"account","orderId":"order","quote":$quoteJson,"status":"ACTIVE",
        "paymentId":"payment","checkoutId":null,"pixPayload":"pix-copy","pixImage":"base64-image","checkoutUrl":"https://asaas.com/i/payment",
        "confirmed":false,"settled":false,"available":false,"splitSettled":false,"expiresAt":"2026-09-20T12:00:00Z"}"""
    private val detailJson = """{"order":$orderJson,"instruments":[$instrumentJson]}"""
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private fun envelope(value: String, request: String = "server") = """{"value":$value,"requestId":"$request"}"""
}
