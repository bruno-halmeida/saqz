package br.com.saqz.groups.data.invite

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.MonthlyFeeDefault
import br.com.saqz.groups.domain.membership.MonthlyInviteError
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInvitePerson
import br.com.saqz.groups.domain.membership.MonthlyInvitePersonError
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheck
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheckResult
import br.com.saqz.groups.domain.membership.MonthlyInvitePlanLimit
import br.com.saqz.groups.domain.membership.MonthlyInviteRejection
import br.com.saqz.groups.domain.membership.MonthlyInviteSent
import br.com.saqz.groups.domain.membership.MonthlyInviteeStatus
import br.com.saqz.groups.domain.membership.MonthlyInvites
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorMonthlyInviteGatewayTest {
    private val group = GroupId("group-1")

    @Test fun `list maps link rows pause and the default fee`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/group-1/monthly-invites", request.url.encodedPath)
            ok(
                """{"link":{"state":"ACTIVE","url":"https://saqz.app/?saqz_invite=44DF8SA8","expiresAt":"2026-10-27T15:00:00Z"},
                "invitees":[{"id":"i1","name":"Ana","phone":"11988880001","status":"EXPIRED","createdAt":"2026-10-20T15:00:00Z"},
                {"id":"i2","name":"Bia","phone":"11988880002","status":"NOVO","createdAt":"2026-10-20T15:00:00Z"}],
                "paused":{"retryAt":"2026-10-20T15:10:00Z"},"defaultFee":{"monthlyFeeCents":8000,"monthlyDueDay":10}}""",
            )
        }.list(group)

        val value = assertIs<SaqzResult.Success<MonthlyInvites>>(result).value
        assertEquals(MonthlyInviteLinkState.ACTIVE, value.link.state)
        assertEquals(listOf(MonthlyInviteeStatus.EXPIRED, MonthlyInviteeStatus.UNKNOWN), value.invitees.map { it.status })
        assertEquals("2026-10-20T15:10:00Z", value.pausedRetryAt)
        assertEquals(MonthlyFeeDefault(8000, 10), value.defaultFee)
    }

    @Test fun `list without pause or fee keeps both null`() = runTest {
        val value = assertIs<SaqzResult.Success<MonthlyInvites>>(
            gateway { ok("""{"link":{"state":"NONE"},"invitees":[],"paused":null,"defaultFee":null}""") }.list(group),
        ).value
        assertEquals(null, value.pausedRetryAt)
        assertEquals(null, value.defaultFee)
        assertEquals(MonthlyInviteLinkState.NONE, value.link.state)
    }

    @Test fun `check sends the 11 digits as query`() = runTest {
        val result = gateway { request ->
            assertEquals("/api/groups/group-1/monthly-invites/check", request.url.encodedPath)
            assertEquals("11977770002", request.url.parameters["phone"])
            ok("""{"result":"ALREADY_AVULSO","name":"Nina","userId":"u2"}""")
        }.check(group, "11977770002")
        assertEquals(
            MonthlyInvitePhoneCheckResult(MonthlyInvitePhoneCheck.ALREADY_AVULSO, "Nina", "u2"),
            assertIs<SaqzResult.Success<MonthlyInvitePhoneCheckResult>>(result).value,
        )
    }

    @Test fun `send posts requestId and people and reads enqueued and link`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("req-1", body.getValue("requestId").jsonPrimitive.content)
            val people = body.getValue("people").jsonArray
            assertEquals("Ana", people[0].jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals("11988880001", people[0].jsonObject.getValue("phone").jsonPrimitive.content)
            ok("""{"enqueued":1,"link":{"url":"https://saqz.app/?saqz_invite=44DF8SA8","expiresAt":"2026-10-27T15:00:00Z"}}""")
        }.send(group, "req-1", listOf(MonthlyInvitePerson("Ana", "11988880001")))
        assertEquals(
            MonthlyInviteSent(1, "https://saqz.app/?saqz_invite=44DF8SA8", "2026-10-27T15:00:00Z"),
            assertIs<SaqzResult.Success<MonthlyInviteSent>>(result).value,
        )
    }

    @Test fun `send maps rejected people plan limit fee required and whatsapp unavailable`() = runTest {
        val people = listOf(MonthlyInvitePerson("Ana", "11988880001"))
        val invalid = gateway {
            problem(422, """"code":"INVALID_PEOPLE","people":[{"index":0,"reason":"ALREADY_MENSALISTA"},{"index":1,"reason":"X"}]""")
        }.send(group, "r", people)
        assertEquals(
            MonthlyInviteError.InvalidPeople(
                listOf(
                    MonthlyInvitePersonError(0, MonthlyInviteRejection.ALREADY_MENSALISTA),
                    MonthlyInvitePersonError(1, MonthlyInviteRejection.UNKNOWN),
                ),
            ),
            assertIs<SaqzResult.Failure<MonthlyInviteError>>(invalid).error,
        )
        val limit = gateway { problem(409, """"code":"PLAN_LIMIT","limit":5,"current":3,"requested":3,"free":2""") }.send(group, "r", people)
        assertEquals(
            MonthlyInviteError.PlanLimit(MonthlyInvitePlanLimit(5, 3, 3, 2)),
            assertIs<SaqzResult.Failure<MonthlyInviteError>>(limit).error,
        )
        assertEquals(
            MonthlyInviteError.MonthlyFeeRequired,
            assertIs<SaqzResult.Failure<MonthlyInviteError>>(gateway { problem(422, """"code":"MONTHLY_FEE_REQUIRED"""") }.copyLink(group)).error,
        )
        assertEquals(
            MonthlyInviteError.WhatsAppUnavailable,
            assertIs<SaqzResult.Failure<MonthlyInviteError>>(gateway { problem(409, """"code":"WHATSAPP_UNAVAILABLE"""") }.send(group, "r", people)).error,
        )
        assertEquals(
            MonthlyInviteError.Forbidden,
            assertIs<SaqzResult.Failure<MonthlyInviteError>>(gateway { problem(403, """"code":"ACCESS_FORBIDDEN"""") }.list(group)).error,
        )
    }

    @Test fun `row actions hit their routes`() = runTest {
        val calls = mutableListOf<String>()
        val gateway = gateway { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            when {
                request.url.encodedPath.endsWith("/link") -> ok("""{"url":"u","expiresAt":"e"}""")
                request.method == HttpMethod.Delete || request.url.encodedPath.endsWith("invalidate") ->
                    respond("", HttpStatusCode.NoContent)
                else -> ok("""{"enqueued":2}""")
            }
        }
        assertEquals(1, assertIs<SaqzResult.Success<Int>>(gateway.resend(group, "i1")).value.coerceAtMost(1))
        assertEquals(2, assertIs<SaqzResult.Success<Int>>(gateway.resendAll(group)).value)
        assertIs<SaqzResult.Success<Unit>>(gateway.remove(group, "i1"))
        assertIs<SaqzResult.Success<Unit>>(gateway.invalidateLink(group))
        assertIs<SaqzResult.Success<*>>(gateway.copyLink(group))
        assertEquals(
            listOf(
                "POST /api/groups/group-1/monthly-invites/i1/resend",
                "POST /api/groups/group-1/monthly-invites/resend-all",
                "DELETE /api/groups/group-1/monthly-invites/i1",
                "POST /api/groups/group-1/monthly-invites/link/invalidate",
                "POST /api/groups/group-1/monthly-invites/link",
            ),
            calls,
        )
    }

    private fun MockRequestHandleScope.ok(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.problem(status: Int, fields: String) =
        respond("""{"status":$status,$fields}""", HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/problem+json"))

    private fun gateway(handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        KtorMonthlyInviteGateway(
            AuthenticatedNetworkClient(
                NetworkClient(MockEngine { handler(it) }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/")),
                object : IdTokenProvider {
                    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token"))
                },
                object : SessionInvalidator { override fun invalidate() = Unit },
            ),
        )
}
