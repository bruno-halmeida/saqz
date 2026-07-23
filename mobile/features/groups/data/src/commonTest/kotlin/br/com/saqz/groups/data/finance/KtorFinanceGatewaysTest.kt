package br.com.saqz.groups.data.finance

import br.com.saqz.domain.DataError
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorFinanceGatewaysTest {
    @Test
    fun `organizer charges map identities cents status audit and totals`() = runTest {
        val result = organizer { json(CHARGES) }.charges(GROUP)
        val value = assertIs<SaqzResult.Success<ChargeList>>(result).value
        val game = value.charges.first()

        assertEquals(2_500L, game.amountCents)
        assertEquals(ChargeKind.Game, game.kind)
        assertEquals(GAME, game.gameId)
        assertNull(game.month)
        assertEquals(ChargeStatus.Pending, game.status)
        assertEquals(ChargeStatus.Paid, game.audit.single().newStatus)
        assertEquals(2_500L, value.totals?.pendingCents)
        assertEquals(5_000L, value.totals?.paidCents)
    }

    @Test
    fun `monthly charge maps month member and cancelled status`() = runTest {
        val value = success(organizer { json(CHARGES) }.charges(GROUP)).charges.last()

        assertEquals(ChargeKind.Monthly, value.kind)
        assertEquals("2026-08", value.month)
        assertEquals(MEMBER, value.memberId)
        assertEquals(ChargeStatus.Cancelled, value.status)
    }

    @Test
    fun `athlete uses own charge route without organizer totals`() = runTest {
        val result = athlete { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/${GROUP.value}/charges/me", request.url.encodedPath)
            json(OWN_CHARGES)
        }.ownCharges(GROUP)

        val value = success(result)
        assertEquals(MEMBER, value.charges.single().memberId)
        assertNull(value.totals)
    }

    @Test
    fun `monthly request contains only stable business fields`() = runTest {
        organizer { request ->
            val body = request.json()
            assertEquals(setOf("requestId", "month", "amountCents", "dueDate", "memberIds"), body.keys)
            assertEquals(KEY, body.getValue("requestId").jsonPrimitive.content)
            assertEquals(7_000L, body.getValue("amountCents").jsonPrimitive.long)
            assertEquals(setOf(MEMBER, MEMBER2), body.getValue("memberIds").jsonArray.map { it.jsonPrimitive.content }.toSet())
            json(OWN_CHARGES)
        }.generateMonthly(GROUP, monthly())
    }

    @Test
    fun `monthly request contains no settlement refund balance or credential fields`() = runTest {
        organizer { request ->
            val keys = request.json().keys.map(String::lowercase)
            assertTrue(keys.none { it in setOf("settlement", "partial", "refund", "balance", "credential", "token") })
            json(OWN_CHARGES)
        }.generateMonthly(GROUP, monthly())
    }

    @Test
    fun `charge status sends quoted version exact status and note`() = runTest {
        organizer { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/${GROUP.value}/charges/$CHARGE/status", request.url.encodedPath)
            assertEquals("\"4\"", request.headers[HttpHeaders.IfMatch])
            assertEquals("WAIVED", request.json().getValue("status").jsonPrimitive.content)
            assertEquals("Ajuste manual", request.json().getValue("note").jsonPrimitive.content)
            versioned(CHARGE_JSON, "\"5\"")
        }.updateChargeStatus(
            GROUP,
            CHARGE,
            FinanceVersionToken("\"4\""),
            ChargeStatusCommand(ChargeStatus.Waived, "Ajuste manual"),
        )
    }

    @Test
    fun `charge status maps returned version and appended audit`() = runTest {
        val result = organizer { versioned(CHARGE_JSON, "\"5\"") }.updateChargeStatus(
            GROUP,
            CHARGE,
            FinanceVersionToken("\"4\""),
            ChargeStatusCommand(ChargeStatus.Paid),
        )
        val value = success(result)

        assertEquals("\"5\"", value.version.value)
        assertEquals("actor-1", value.charge.audit.single().actorId)
    }

    @Test
    fun `expense list maps category notes audit status and active total`() = runTest {
        val value = success(organizer { json(EXPENSES) }.expenses(GROUP))
        val expense = value.expenses.single()

        assertEquals(12_345L, expense.amountCents)
        assertEquals(ExpenseCategory.Other, expense.category)
        assertEquals("Água", expense.customCategory)
        assertEquals("Compra manual", expense.notes)
        assertEquals(ExpenseStatus.Active, expense.status)
        assertEquals(ExpenseAction.Created, expense.audit.single().action)
        assertEquals(12_345L, value.activeTotalCents)
    }

    @Test
    fun `expense create sends idempotency key and maps version`() = runTest {
        val result = organizer { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/${GROUP.value}/expenses", request.url.encodedPath)
            assertEquals(KEY, request.json().getValue("requestId").jsonPrimitive.content)
            versioned(EXPENSE_JSON, "\"1\"")
        }.createExpense(GROUP, expense(KEY))

        assertEquals("\"1\"", success(result).version.value)
    }

    @Test
    fun `expense edit sends cents vocabulary and quoted version`() = runTest {
        organizer { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/${GROUP.value}/expenses/$EXPENSE", request.url.encodedPath)
            assertEquals("\"1\"", request.headers[HttpHeaders.IfMatch])
            assertEquals(12_345L, request.json().getValue("amountCents").jsonPrimitive.long)
            assertEquals("OTHER", request.json().getValue("category").jsonPrimitive.content)
            versioned(EXPENSE_JSON, "\"2\"")
        }.editExpense(GROUP, EXPENSE, FinanceVersionToken("\"1\""), expense())
    }

    @Test
    fun `expense void sends no body and maps voided result`() = runTest {
        val result = organizer { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/${GROUP.value}/expenses/$EXPENSE/void", request.url.encodedPath)
            assertEquals("\"2\"", request.headers[HttpHeaders.IfMatch])
            assertTrue(request.body !is TextContent)
            versioned(VOID_EXPENSE_JSON, "\"3\"")
        }.voidExpense(GROUP, EXPENSE, FinanceVersionToken("\"2\""))

        assertEquals(ExpenseStatus.Voided, success(result).expense.status)
        assertEquals("\"3\"", success(result).version.value)
    }

    @Test
    fun `finance totals map every bucket`() = runTest {
        val value = success(organizer { json(TOTALS) }.totals(GROUP))
        assertEquals(FinanceTotals(2_500L, 5_000L, 1_000L, 7_000L, 12_345L), value)
    }

    @Test
    fun `missing charge version is invalid response`() = runTest {
        val result = organizer { json(CHARGE_JSON) }.updateChargeStatus(
            GROUP, CHARGE, FinanceVersionToken("\"4\""), ChargeStatusCommand(ChargeStatus.Paid),
        )
        assertInvalidResponse(result)
    }

    @Test
    fun `missing expense version is invalid response`() = runTest {
        assertInvalidResponse(organizer { json(EXPENSE_JSON) }.createExpense(GROUP, expense(KEY)))
    }

    @Test
    fun `validation maps exact finance field errors`() {
        val result = problem(400, "VALIDATION_FAILED", mapOf("amountCents" to listOf("is invalid"))).toFinanceError()
        val validation = assertIs<FinanceError.Validation>(result)
        assertEquals(mapOf("amountCents" to listOf("is invalid")), validation.error.details.fieldMessages)
    }

    @Test fun `group not found is hidden`() = assertEquals(FinanceError.HiddenResource, problem(404, "GROUP_NOT_FOUND").toFinanceError())
    @Test fun `game not found is hidden`() = assertEquals(FinanceError.HiddenResource, problem(404, "GAME_NOT_FOUND").toFinanceError())
    @Test fun `access forbidden maps distinctly`() = assertEquals(FinanceError.Forbidden, problem(403, "ACCESS_FORBIDDEN").toFinanceError())
    @Test fun `version conflict maps distinctly`() = assertEquals(FinanceError.Conflict, problem(409, "VERSION_CONFLICT").toFinanceError())
    @Test fun `precondition maps distinctly`() = assertEquals(FinanceError.PreconditionRequired, problem(428, "PRECONDITION_REQUIRED").toFinanceError())
    @Test fun `invalid lifecycle maps distinctly`() = assertEquals(FinanceError.InvalidLifecycle, problem(409, "INVALID_GAME_TRANSITION").toFinanceError())
    @Test fun `authentication maps distinctly`() = assertEquals(FinanceError.Authentication, problem(401, "AUTHENTICATION_REQUIRED").toFinanceError())
    @Test fun `timeout maps shared error`() = assertDataError(DataError.Timeout, NetworkError.Timeout.toFinanceError())
    @Test fun `connectivity maps shared error`() = assertDataError(DataError.Connectivity, NetworkError.Connectivity.toFinanceError())
    @Test fun `payload too large maps shared error`() = assertDataError(DataError.PayloadTooLarge, NetworkError.PayloadTooLarge.toFinanceError())
    @Test fun `unavailable maps server error`() = assertDataError(DataError.Server, NetworkError.Unavailable.toFinanceError())
    @Test fun `unknown maps unknown error`() = assertDataError(DataError.Unknown, NetworkError.Unknown.toFinanceError())
    @Test fun `invalid response maps shared error`() = assertDataError(DataError.InvalidResponse, NetworkError.InvalidResponse.toFinanceError())
    @Test fun `unknown 5xx problem maps server error`() = assertDataError(DataError.Server, problem(503, "UNKNOWN").toFinanceError())
    @Test fun `unknown 4xx problem maps unknown error`() = assertDataError(DataError.Unknown, problem(400, "UNKNOWN").toFinanceError())

    @Test
    fun `read retries three times with exact schedule`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val gateway = organizer(delays = delays) {
            calls++
            if (calls < 4) unavailable() else json(TOTALS)
        }

        assertIs<SaqzResult.Success<FinanceTotals>>(gateway.totals(GROUP))
        assertEquals(4, calls)
        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
    }

    @Test
    fun `idempotent monthly write retries`() = runTest {
        var calls = 0
        val result = organizer {
            calls++
            if (calls == 1) unavailable() else json(OWN_CHARGES)
        }.generateMonthly(GROUP, monthly())

        assertIs<SaqzResult.Success<ChargeList>>(result)
        assertEquals(2, calls)
    }

    @Test
    fun `blank monthly key disables retry`() = runTest {
        var calls = 0
        val result = organizer {
            calls++
            unavailable()
        }.generateMonthly(GROUP, monthly().copy(requestId = ""))

        assertIs<SaqzResult.Failure<FinanceError>>(result)
        assertEquals(1, calls)
    }

    @Test
    fun `idempotent expense create retries`() = runTest {
        var calls = 0
        val result = organizer {
            calls++
            if (calls == 1) unavailable() else versioned(EXPENSE_JSON, "\"1\"")
        }.createExpense(GROUP, expense(KEY))

        assertIs<SaqzResult.Success<VersionedExpense>>(result)
        assertEquals(2, calls)
    }

    @Test
    fun `expense create without key does not retry`() = runTest {
        var calls = 0
        organizer { calls++; unavailable() }.createExpense(GROUP, expense())
        assertEquals(1, calls)
    }

    @Test
    fun `status mutation does not retry`() = runTest {
        var calls = 0
        organizer { calls++; unavailable() }.updateChargeStatus(
            GROUP, CHARGE, FinanceVersionToken("\"4\""), ChargeStatusCommand(ChargeStatus.Paid),
        )
        assertEquals(1, calls)
    }

    @Test
    fun `void mutation does not retry`() = runTest {
        var calls = 0
        organizer { calls++; unavailable() }.voidExpense(GROUP, EXPENSE, FinanceVersionToken("\"2\""))
        assertEquals(1, calls)
    }

    @Test
    fun `retry exhaustion returns final typed error`() = runTest {
        var calls = 0
        val result = organizer { calls++; unavailable() }.totals(GROUP)
        assertDataError(DataError.Server, assertIs<SaqzResult.Failure<FinanceError>>(result).error)
        assertEquals(4, calls)
    }

    @Test
    fun `transport cancellation propagates`() = runTest {
        val gateway = organizer { throw CancellationException("cancel") }
        assertFailsWith<CancellationException> { gateway.charges(GROUP) }
    }

    @Test
    fun `retry delay cancellation propagates`() = runTest {
        val gateway = KtorOrganizerFinanceGateway(auth(Tokens()) { unavailable() }) {
            throw CancellationException("cancel delay")
        }
        assertFailsWith<CancellationException> { gateway.totals(GROUP) }
    }

    private fun monthly() = MonthlyChargeCommand(KEY, "2026-08", 7_000L, "2026-08-10", linkedSetOf(MEMBER, MEMBER2))

    private fun expense(key: String? = null) = ExpenseWriteCommand(
        requestId = key,
        description = "Água do jogo",
        amountCents = 12_345L,
        expenseDate = "2026-08-12",
        category = ExpenseCategory.Other,
        customCategory = "Água",
        notes = "Compra manual",
    )

    private fun athlete(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorAthleteFinanceGateway(auth(Tokens(), handler), retryDelay = {})

    private fun organizer(
        delays: MutableList<Long>? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorOrganizerFinanceGateway(auth(Tokens(), handler)) { delay -> delays?.add(delay) }

    private fun auth(
        tokens: IdTokenProvider,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthenticatedNetworkClient {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return AuthenticatedNetworkClient(network, tokens, NoopInvalidator())
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = jsonHeaders())

    private fun MockRequestHandleScope.versioned(body: String, etag: String) = respond(
        body,
        headers = headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            HttpHeaders.ETag to listOf(etag),
        ),
    )

    private fun MockRequestHandleScope.unavailable() = respond(
        """{"status":503,"code":"TEMPORARY","correlationId":"safe"}""",
        HttpStatusCode.ServiceUnavailable,
        jsonHeaders(),
    )

    private fun HttpRequestData.json() = Json.parseToJsonElement((body as TextContent).text).jsonObject
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun problem(status: Int, code: String, fields: Map<String, List<String>>? = null) =
        NetworkError.ApiProblemError(ApiProblem(status, code, "safe-correlation", fields))

    private fun <T> success(result: SaqzResult<T, FinanceError>) = assertIs<SaqzResult.Success<T>>(result).value

    private fun assertInvalidResponse(result: SaqzResult<*, FinanceError>) =
        assertDataError(DataError.InvalidResponse, assertIs<SaqzResult.Failure<FinanceError>>(result).error)

    private fun assertDataError(expected: DataError, actual: FinanceError) =
        assertEquals(expected, assertIs<FinanceError.Data>(actual).error)

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("obviously-fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        val GROUP = GroupId("group-1")
        const val GAME = "game-1"
        const val MEMBER = "member-1"
        const val MEMBER2 = "member-2"
        const val CHARGE = "charge-1"
        const val EXPENSE = "expense-1"
        const val KEY = "finance-key"
        const val CHARGE_JSON = """{"id":"charge-1","groupId":"group-1","memberId":"member-1","kind":"GAME","gameId":"game-1","month":null,"amountCents":2500,"dueDate":"2026-08-12","status":"PENDING","reviewRequired":true,"version":5,"events":[{"actorId":"actor-1","oldStatus":"PENDING","newStatus":"PAID","note":"Recebido","occurredAt":"2026-08-12T10:00:00Z"}]}"""
        const val CHARGES = """{"charges":[$CHARGE_JSON,{"id":"charge-2","groupId":"group-1","memberId":"member-1","kind":"MONTHLY","gameId":null,"month":"2026-08","amountCents":7000,"dueDate":"2026-08-10","status":"CANCELLED","version":2,"events":[]}],"pendingTotalCents":2500,"paidTotalCents":5000,"waivedTotalCents":1000,"cancelledTotalCents":7000}"""
        const val OWN_CHARGES = """{"charges":[$CHARGE_JSON]}"""
        const val EXPENSE_JSON = """{"id":"expense-1","groupId":"group-1","description":"Água do jogo","amountCents":12345,"expenseDate":"2026-08-12","category":"OTHER","customCategory":"Água","notes":"Compra manual","status":"ACTIVE","version":1,"events":[{"actorId":"actor-1","action":"CREATED","occurredAt":"2026-08-12T10:00:00Z"}]}"""
        const val VOID_EXPENSE_JSON = """{"id":"expense-1","groupId":"group-1","description":"Água do jogo","amountCents":12345,"expenseDate":"2026-08-12","category":"OTHER","customCategory":"Água","notes":"Compra manual","status":"VOIDED","version":3,"events":[{"actorId":"actor-1","action":"VOIDED","occurredAt":"2026-08-13T10:00:00Z"}]}"""
        const val EXPENSES = """{"expenses":[$EXPENSE_JSON],"activeExpenseTotalCents":12345}"""
        const val TOTALS = """{"pendingChargeCents":2500,"paidChargeCents":5000,"waivedChargeCents":1000,"cancelledChargeCents":7000,"activeExpenseCents":12345}"""
    }
}
