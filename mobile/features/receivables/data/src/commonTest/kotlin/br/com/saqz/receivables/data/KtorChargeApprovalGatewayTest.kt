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

class KtorChargeApprovalGatewayTest {
    @Test fun lookupAuthenticatesExactAccountAndPreservesEmptyAndExistingResult() = runTest {
        for (exists in listOf(false, true)) {
            val g = gateway(MockEngine { r ->
                assertEquals(HttpMethod.Get, r.method); assertEquals("/api/receivables/charges/charge/order", r.url.encodedPath)
                assertEquals("accountId=account", r.url.encodedQuery); assertEquals("Bearer token", r.headers[HttpHeaders.Authorization])
                respond(envelope("""{"detail":${if (exists) detailJson else "null"}}"""), HttpStatusCode.OK, headers)
            })
            assertEquals(SaqzResult.Success(if (exists) MemberPaymentDetail(order, emptyList()) else null), g.lookup(target))
        }
    }
    @Test fun previewUsesExplicitChargeAndAccountAndReturnsExactReview() = runTest {
        val g = gateway(MockEngine { r ->
            assertEquals(HttpMethod.Post, r.method); assertEquals("/api/receivables/charges/charge/preview", r.url.encodedPath)
            assertEquals(Json.parseToJsonElement("""{"requestId":"request","accountId":"account"}"""), Json.parseToJsonElement((r.body as TextContent).text))
            respond(envelope(reviewJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(ChargeApprovalReview(target, "payer", "2026-09-20", "2026-08-01", listOf(quote), fingerprint)),
            g.preview(target, "request"))
    }
    @Test fun approvalRetriesIdenticalAcceptedFingerprintAndNeverSendsPayerData() = runTest {
        val bodies = mutableListOf<String>()
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/charges/charge/approve", r.url.encodedPath); assertEquals(HttpMethod.Post, r.method)
            assertEquals("Bearer token", r.headers[HttpHeaders.Authorization]); bodies += (r.body as TextContent).text
            if (bodies.size == 1) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(envelope(orderJson), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(order), g.approve(target, command))
        assertEquals(2, bodies.size); assertEquals(bodies[0], bodies[1])
        assertEquals(Json.parseToJsonElement("""{"requestId":"request","accountId":"account","fingerprint":"$fingerprint","accepted":true}"""),
            Json.parseToJsonElement(bodies[0]))
    }
    @Test fun cancelRetriesOnlySameCancelAndPreservesPendingStatus() = runTest {
        val bodies = mutableListOf<String>()
        val g = gateway(MockEngine { r ->
            assertEquals("/api/receivables/orders/order/cancel", r.url.encodedPath); assertEquals(HttpMethod.Post, r.method)
            bodies += (r.body as TextContent).text
            if (bodies.size == 1) respond("", HttpStatusCode.ServiceUnavailable)
            else respond(envelope(detailJson.replace("ISSUED", "CANCEL_PENDING")), HttpStatusCode.OK, headers)
        })
        assertEquals(SaqzResult.Success(MemberPaymentDetail(order.copy(status = "CANCEL_PENDING"), emptyList())), g.cancel(target, "order", "request"))
        assertEquals(listOf("""{"requestId":"request"}""", """{"requestId":"request"}"""), bodies)
    }
    @Test fun mismatchedIdentifiersAndSnapshotsFailClosedAcrossEveryResponse() = runTest {
        for (field in listOf("account", "charge", "group")) {
            val g = gateway(MockEngine { r ->
                val value = when { r.url.encodedPath.endsWith("preview") -> reviewJson
                    r.url.encodedPath.endsWith("approve") -> orderJson
                    r.url.encodedPath.endsWith("cancel") -> detailJson
                    else -> """{"detail":$detailJson}""" }
                respond(envelope(value.replace("\"$field\"", "\"foreign\"")), HttpStatusCode.OK, headers)
            })
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.lookup(target), field)
            assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.preview(target, "request"), field)
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.approve(target, command), field)
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.cancel(target, "order", "request"), field)
        }
        for (bad in listOf(orderJson.replace(fingerprint, "b".repeat(64)), orderJson.replace("1061", "9999"))) {
            val g = gateway(MockEngine { respond(envelope(bad), HttpStatusCode.OK, headers) })
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.approve(target, command))
        }
        val g = gateway(MockEngine { respond(envelope(detailJson.replace("\"order\"", "\"foreign-order\"")), HttpStatusCode.OK, headers) })
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.cancel(target, "order", "request"))
    }
    @Test fun errorsDistinguishLostWritesFromRejectedReview() = runTest {
        for ((code, error) in listOf(400 to ReceiptError.INVALID, 401 to ReceiptError.SIGNED_OUT,
            403 to ReceiptError.DENIED, 404 to ReceiptError.DENIED, 409 to ReceiptError.STALE)) {
            val g = gateway(MockEngine { respond("{}", HttpStatusCode.fromValue(code), headers) })
            assertEquals(SaqzResult.Failure(error), g.lookup(target)); assertEquals(SaqzResult.Failure(error), g.preview(target, "request"))
            assertEquals(SaqzResult.Failure(error), g.approve(target, command)); assertEquals(SaqzResult.Failure(error), g.cancel(target, "order", "request"))
        }
        val g = gateway(MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable, headers) })
        assertEquals(SaqzResult.Failure(ReceiptError.UNAVAILABLE), g.lookup(target))
        assertEquals(SaqzResult.Failure(ReceiptError.UNAVAILABLE), g.preview(target, "request"))
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.approve(target, command))
        assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.cancel(target, "order", "request"))
    }
    @Test fun malformedOrUncorrelatedWriteEnvelopeCannotAcknowledgeAnOperation() = runTest {
        for (value in listOf("{", "{}", """{"value":$orderJson,"requestId":"other"}""", """{"value":null,"requestId":"request"}""")) {
            val g = gateway(MockEngine { respond(value, HttpStatusCode.OK, headers) })
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.approve(target, command))
            assertEquals(SaqzResult.Failure(ReceiptError.UNCERTAIN), g.cancel(target, "order", "request"))
        }
    }
    @Test fun invalidSelectionOrAcceptanceDoesNotWrite() = runTest {
        val g = gateway(MockEngine { error("No request expected") })
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.approve(target, command.copy(accepted = false)))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.approve(target, command.copy(fingerprint = "bad")))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.approve(target, command.copy(requestId = "")))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.cancel(target, "order", ""))
        assertEquals(SaqzResult.Failure(ReceiptError.INVALID), g.preview(target, ""))
    }
    private fun gateway(engine: MockEngine) = KtorChargeApprovalGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://example.test/")),
        object : IdTokenProvider { override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token")) },
        object : SessionInvalidator { override fun invalidate() = Unit }))
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private val target = ChargeApprovalTarget("group", "charge", "account")
    private val fingerprint = "a".repeat(64)
    private val quote = MemberPaymentQuote("schedule", "v1", ReceiptMethod.PIX, 1000, 61, 1061, 1000, 20, 41)
    private val order = MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-20", "ISSUED", listOf(quote), fingerprint)
    private val command = ChargeApprovalCommand("request", fingerprint, true)
    private val quoteJson = """{"feeScheduleId":"schedule","termsVersion":"v1","method":"PIX","baseCents":1000,"feesCents":61,
        "totalCents":1061,"expectedNetCents":1000,"commissionCents":20,"providerFeeCents":41}"""
    private val reviewJson = """{"accountId":"account","chargeId":"charge","groupId":"group","payerId":"payer","dueDate":"2026-09-20",
        "billingMonth":"2026-08-01","quotes":[$quoteJson],"fingerprint":"$fingerprint"}"""
    private val orderJson = """{"id":"order","accountId":"account","chargeId":"charge","groupId":"group","payerId":"payer","dueDate":"2026-09-20",
        "status":"ISSUED","quotes":[$quoteJson],"fingerprint":"$fingerprint"}"""
    private val detailJson = """{"order":$orderJson,"instruments":[]}"""
    private fun envelope(value: String) = """{"value":$value,"requestId":"request"}"""
}
