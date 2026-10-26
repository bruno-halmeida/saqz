package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorGroupPaymentsGatewayTest {
    @Test fun feePolicyIsAPutWithAccountAndReturnsTheNewState() = runTest {
        val gateway = KtorGroupPaymentsGateway(client(MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/receivables/groups/ceret/fee-policy", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("PASS", body.getValue("feePolicy").jsonPrimitive.content)
            assertEquals("acc", body.getValue("accountId").jsonPrimitive.content)
            respond(envelope("""{"state":${state("PASS")},"permissions":{}}""", "req"), HttpStatusCode.OK, headers)
        }))
        val status = assertIs<SaqzResult.Success<ReceiptStatus>>(gateway.setFeePolicy("ceret", "req", "acc", FeePolicy.PASS)).value
        assertEquals(FeePolicy.PASS, status.state.feePolicy)
        assertTrue(status.state.pixReady)
        assertEquals(1, status.state.openReviews)
    }

    @Test fun quoteReadsBothPoliciesFromTheServer() = runTest {
        val gateway = KtorGroupPaymentsGateway(client(MockEngine { request ->
            assertEquals("/api/receivables/groups/ceret/quote", request.url.encodedPath)
            assertEquals("2000", request.url.parameters["baseCents"])
            assertEquals("acc", request.url.parameters["accountId"])
            respond(envelope("""{"absorb":{"athletePaysCents":2000,"receivesPixCents":1951,"receivesCardCents":1880},
                "pass":{"athletePaysCents":2128,"receivesPixCents":2077,"receivesCardCents":2000},
                "pixFeeLabel":"R$ 0,49 + 1%","cardFeeLabel":"4,99% + R$ 0,49"}"""), HttpStatusCode.OK, headers)
        }))
        val quote = assertIs<SaqzResult.Success<FeeQuote>>(gateway.quote("ceret", "acc", 2000)).value
        assertEquals(2000, quote.side(FeePolicy.ABSORB).athletePaysCents)
        assertEquals(2128, quote.side(FeePolicy.PASS).athletePaysCents)
        assertEquals(2000, quote.side(FeePolicy.PASS).receivesCardCents)
        assertEquals("R$ 0,49 + 1%", quote.pixFeeLabel)
    }

    @Test fun reviewFlowUsesTheContractPaths() = runTest {
        val paths = mutableListOf<String>()
        val gateway = KtorGroupPaymentsGateway(client(MockEngine { request ->
            val path = request.url.encodedPath
            paths += "${request.method.value} $path"
            when {
                path.endsWith("/reviews") -> respond(envelope("""{"reviews":[{"id":"r1","payerName":"João P. Silva",
                    "amountCents":2000,"receivedAt":"2026-07-28T23:04:00Z"}]}"""), HttpStatusCode.OK, headers)
                path.endsWith("/suggestion") -> {
                    assertEquals("felipe", request.url.parameters["memberUserId"])
                    respond(envelope("""{"charges":[{"chargeId":"c1","label":"Diária · 21/07","dueDate":"2026-07-21",
                        "payableCents":2000,"overdue":true}],"coveredCents":2000}"""), HttpStatusCode.OK, headers)
                }
                path.endsWith("/assign") -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals(listOf("c1"), body.getValue("chargeIds").jsonArray.map { it.jsonPrimitive.content })
                    respond(envelope("""{"settledChargeIds":["c1"],"leftoverCents":0}""", "req"), HttpStatusCode.OK, headers)
                }
                else -> respond(envelope("""{"expenseId":"e1"}""", "req"), HttpStatusCode.OK, headers)
            }
        }))
        assertEquals("João P. Silva", assertIs<SaqzResult.Success<List<PaymentReview>>>(gateway.reviews("ceret", "acc")).value.single().payerName)
        assertEquals(2000, assertIs<SaqzResult.Success<ReviewSuggestion>>(gateway.suggestion("r1", "felipe")).value.coveredCents)
        assertEquals(SaqzResult.Success(ReviewAssignment(listOf("c1"), 0)), gateway.assign("r1", "req", "felipe", listOf("c1")))
        assertEquals(SaqzResult.Success("e1"), gateway.notCharge("r1", "req"))
        assertEquals(listOf(
            "GET /api/receivables/groups/ceret/reviews",
            "GET /api/receivables/reviews/r1/suggestion",
            "POST /api/receivables/reviews/r1/assign",
            "POST /api/receivables/reviews/r1/not-charge",
        ), paths)
    }

    @Test fun septemberStateWithoutV1FieldsStillReads() = runTest {
        val gateway = KtorGroupReceivablesGateway(client(MockEngine {
            respond(envelope("""{"state":{"accountId":"acc","groupId":"ceret","enabled":true,"pixEnabled":true,
                "cardEnabled":false},"permissions":{}}"""), HttpStatusCode.OK, headers)
        }))
        val state = assertIs<SaqzResult.Success<ReceiptStatus>>(gateway.status("ceret", "acc")).value.state
        assertEquals(FeePolicy.ABSORB, state.feePolicy)
        assertFalse(state.pixReady)
        assertEquals(0, state.openReviews)
    }

    @Test fun debtorsSumOpenChargesPerMemberWithThePayableValue() = runTest {
        val gateway = KtorGroupDebtorsGateway(client(MockEngine { request ->
            if (request.url.encodedPath.endsWith("memberships")) respond("""[{"userId":"felipe","displayName":"Felipe Nunes"},
                {"userId":"carla","displayName":"Carla Mendes"}]""", HttpStatusCode.OK, headers)
            else respond("""{"charges":[{"memberId":"felipe","status":"PENDING","amountCents":2000,"payableCents":2128},
                {"memberId":"felipe","status":"PENDING","amountCents":2000},{"memberId":"carla","status":"PAID","amountCents":2000}]}""",
                HttpStatusCode.OK, headers)
        }))
        val debtors = assertIs<SaqzResult.Success<List<GroupDebtor>>>(gateway.debtors("ceret")).value
        assertEquals(GroupDebtor("felipe", "Felipe Nunes", 2, 4128), debtors.first())
        assertEquals(GroupDebtor("carla", "Carla Mendes", 0, 0), debtors.last())
    }

    private fun state(policy: String) = """{"accountId":"acc","groupId":"ceret","enabled":true,"pixEnabled":true,
        "cardEnabled":true,"feePolicy":"$policy","pixReady":true,"openReviews":1}"""
    private val headers = headersOf(HttpHeaders.ContentType, "application/json")
    private fun envelope(value: String, request: String = "server") = """{"value":$value,"requestId":"$request"}"""
}
