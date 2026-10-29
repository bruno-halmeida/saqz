package br.com.saqz.groups.data.finance

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.*
import br.com.saqz.network.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorCashboxGatewayTest {
    @Test
    fun `opening balance reads amount and quotes the body version when there is no etag`() = runTest {
        val value = success(gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/${GROUP.value}/finance/opening-balance", request.url.encodedPath)
            json("""{"amountCents":25000,"version":2}""")
        }.openingBalance(GROUP))

        assertEquals(25_000L, value.amountCents)
        assertEquals("\"2\"", value.version.value)
    }

    @Test
    fun `opening balance never answered comes as null`() = runTest {
        val value = success(gateway { json("""{"amountCents":null,"version":0}""") }.openingBalance(GROUP))

        assertNull(value.amountCents)
    }

    @Test
    fun `saving the opening balance sends put with if match and reads the new etag`() = runTest {
        val value = success(gateway { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/${GROUP.value}/finance/opening-balance", request.url.encodedPath)
            assertEquals("\"2\"", request.headers[HttpHeaders.IfMatch])
            assertEquals(0L, request.json()["amountCents"]?.jsonPrimitive?.long)
            respond(
                """{"amountCents":0,"version":3}""",
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ETag to listOf("\"3\""),
                ),
            )
        }.saveOpeningBalance(GROUP, FinanceVersionToken("\"2\""), 0L))

        assertEquals(0L, value.amountCents)
        assertEquals("\"3\"", value.version.value)
    }

    @Test
    fun `opening balance version conflict becomes conflict`() = runTest {
        val result = gateway {
            respond(
                """{"status":409,"code":"VERSION_CONFLICT","correlationId":"safe"}""",
                HttpStatusCode.Conflict,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }.saveOpeningBalance(GROUP, FinanceVersionToken("\"2\""), 100L)

        assertEquals(FinanceError.Conflict, assertIs<SaqzResult.Failure<FinanceError>>(result).error)
    }

    @Test
    fun `uncharged summary maps people count and games`() = runTest {
        val value = success(gateway { request ->
            assertEquals("/api/groups/${GROUP.value}/finance/uncharged", request.url.encodedPath)
            json(SUMMARY)
        }.unchargedSummary(GROUP))

        assertEquals(6, value.peopleCount)
        val game = value.games.single()
        assertEquals("game-1", game.gameId)
        assertEquals("Vôlei de terça", game.title)
        assertEquals("2026-10-20", game.localDate)
        assertEquals("19:30", game.localTime)
        assertEquals("CERET", game.venueName)
        assertEquals(4, game.peopleCount)
    }

    @Test
    fun `uncharged list maps kind host no show left group and skip decision`() = runTest {
        val value = success(gateway { request ->
            assertEquals("/api/groups/${GROUP.value}/games/game-1/uncharged", request.url.encodedPath)
            json(LIST)
        }.uncharged(GROUP, "game-1"))

        assertEquals(2_000L, value.suggestedAmountCents)
        val (avulso, guest) = value.people
        assertEquals(UnchargedKind.Avulso, avulso.kind)
        assertNull(avulso.decision)
        assertFalse(avulso.noShow)
        assertEquals(UnchargedKind.Guest, guest.kind)
        assertEquals(1, guest.guestSeq)
        assertEquals("Bia Souza", guest.hostDisplayName)
        assertEquals(UnchargedDecision.Skip, guest.decision)
        assertTrue(guest.leftGroup)
    }

    @Test
    fun `uncharged list without suggested amount comes as null`() = runTest {
        val value = success(gateway { json("""{"gameId":"game-1","suggestedAmountCents":null,"people":[]}""") }
            .uncharged(GROUP, "game-1"))

        assertNull(value.suggestedAmountCents)
    }

    @Test
    fun `review sends request id amount and one decision per person with paid method only on paid`() = runTest {
        val value = success(gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/${GROUP.value}/games/game-1/uncharged/review", request.url.encodedPath)
            val body = request.json()
            assertEquals("review-1", body["requestId"]?.jsonPrimitive?.content)
            assertEquals(1_000L, body["amountCents"]?.jsonPrimitive?.long)
            val people = body["people"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf("CHARGE", "PAID", "SKIP"), people.map { it["decision"]!!.jsonPrimitive.content })
            assertNull(people[0]["paidMethod"])
            assertEquals("CASH", people[1]["paidMethod"]?.jsonPrimitive?.content)
            assertNull(people[2]["paidMethod"])
            assertEquals(1, people[2]["guestSeq"]?.jsonPrimitive?.int)
            json("""{"charged":1,"paid":1,"skipped":1,"ignored":0,"charges":[]}""")
        }.reviewUncharged(GROUP, "game-1", command()))

        assertEquals(UnchargedReviewResult(1, 1, 1, 0, emptyList()), value)
    }

    @Test
    fun `review is retried with the same request id after a temporary failure`() = runTest {
        val ids = mutableListOf<String>()
        val result = gateway { request ->
            ids += request.json()["requestId"]!!.jsonPrimitive.content
            if (ids.size == 1) {
                respond(
                    """{"status":503,"code":"TEMPORARY","correlationId":"safe"}""",
                    HttpStatusCode.ServiceUnavailable,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                json("""{"charged":1,"paid":1,"skipped":1,"ignored":0,"charges":[]}""")
            }
        }.reviewUncharged(GROUP, "game-1", command())

        success(result)
        assertEquals(listOf("review-1", "review-1"), ids)
    }

    @Test
    fun `charge waitlisted mark and statement opening balance are read and default off`() = runTest {
        val organizer = KtorOrganizerFinanceGateway(auth {
            json("""{"charges":[{"id":"c","groupId":"group-1","memberId":"m","kind":"GAME","gameId":"g","amountCents":2000,"dueDate":"2026-10-29","status":"PENDING","version":1,"events":[],"waitlisted":true},{"id":"d","groupId":"group-1","memberId":"m","kind":"GAME","gameId":"g","amountCents":2000,"dueDate":"2026-10-29","status":"PENDING","version":1,"events":[]}]}""")
        }) {}
        val charges = success(organizer.charges(GROUP)).charges
        assertTrue(charges[0].waitlisted)
        assertFalse(charges[1].waitlisted)

        val statement = KtorFinanceStatementGateway(auth {
            json("""{"month":"2026-10","items":[],"summary":{"totalInCents":0,"totalOutCents":0,"periodBalanceCents":0,"accumulatedBalanceCents":25000,"openingBalanceCents":25000},"limit":20,"offset":0,"hasMore":false}""")
        }, retryDelay = {})
        assertEquals(25_000L, success(statement.statement(GROUP)).summary.openingBalanceCents)
    }

    @Test
    fun `new income categories are sent and read back`() = runTest {
        val organizer = KtorOrganizerFinanceGateway(auth { request ->
            assertEquals("SPONSORSHIP", request.json()["category"]?.jsonPrimitive?.content)
            assertEquals("IN", request.json()["direction"]?.jsonPrimitive?.content)
            respond(
                """{"id":"e","groupId":"group-1","description":"Patrocínio","amountCents":5000,"expenseDate":"2026-10-26","category":"SALE","status":"ACTIVE","version":1,"events":[],"direction":"IN"}""",
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ETag to listOf("\"1\""),
                ),
            )
        }) {}
        val created = success(organizer.createExpense(GROUP, ExpenseWriteCommand(
            requestId = "k",
            description = "Patrocínio",
            amountCents = 5_000L,
            expenseDate = "2026-10-26",
            category = ExpenseCategory.Sponsorship,
            direction = FinanceDirection.In,
        )))
        assertEquals(ExpenseCategory.Sale, created.expense.category)
    }

    private fun command() = UnchargedReviewCommand(
        requestId = "review-1",
        amountCents = 1_000L,
        people = listOf(
            UnchargedReviewEntry("m1", 0, ReviewDecision.Charge, PaidMethod.Cash),
            UnchargedReviewEntry("m2", 0, ReviewDecision.Paid, PaidMethod.Cash),
            UnchargedReviewEntry("m3", 1, ReviewDecision.Skip),
        ),
    )

    private fun gateway(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorCashboxGateway(auth(handler), retryDelay = {})

    private fun auth(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): AuthenticatedNetworkClient {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.json() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    private fun <T> success(result: SaqzResult<T, FinanceError>) = assertIs<SaqzResult.Success<T>>(result).value

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("obviously-fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        val GROUP = GroupId("group-1")
        const val SUMMARY = """{"peopleCount":6,"games":[{"gameId":"game-1","title":"Vôlei de terça","localDate":"2026-10-20","localTime":"19:30","venueName":"CERET","peopleCount":4}]}"""
        const val LIST = """{"gameId":"game-1","suggestedAmountCents":2000,"people":[{"memberId":"m1","guestSeq":0,"displayName":"Caio Lima","hostDisplayName":null,"kind":"AVULSO","noShow":false,"leftGroup":false,"decision":null},{"memberId":"m2","guestSeq":1,"displayName":"Júlia Rocha","hostDisplayName":"Bia Souza","kind":"GUEST","noShow":false,"leftGroup":true,"decision":"SKIP"}]}"""
    }
}
