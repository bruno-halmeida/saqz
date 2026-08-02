package br.com.saqz.groups.data.invite

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
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
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorInviteGatewayTest {
    @Test
    fun `preview sends code and maps full card`() = runTest {
        val result = fixture { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/invites/preview", request.url.encodedPath)
            assertEquals(setOf("code"), request.bodyJson().keys)
            assertEquals(INVITE_CODE, request.bodyJson().getValue("code").jsonPrimitive.content)
            preview()
        }.gateway.preview(InviteCode(INVITE_CODE))

        val card = result.success()
        assertEquals("Vôlei do CERET", card.groupName)
        assertEquals("Ana", card.inviterName)
        assertTrue(card.entryRequiresApproval)
        assertEquals("Tatuapé", card.city)
        assertEquals("MIXED", card.composition)
        assertEquals(26, card.memberCount)
        assertEquals("TUESDAY", card.regularSlots.single().weekday)
        assertEquals("2026-08-31T23:59:00Z", card.expiresAt)
        assertEquals("CERET", card.nextGame?.venueName)
    }

    @Test
    fun `preview remains public when token is unavailable`() = runTest {
        var calls = 0
        val fixture = fixture(
            MockEngine {
                calls++
                preview()
            },
            tokenResult = TokenResult.Unavailable,
        )

        val card = fixture.gateway.preview(InviteCode(INVITE_CODE)).success()

        assertEquals("Vôlei do CERET", card.groupName)
        assertEquals(1, calls)
    }

    @Test
    fun `preview maps invalid invite`() = runTest {
        val error = fixture { problem(404, "INVITE_INVALID") }.gateway
            .preview(InviteCode(INVITE_CODE)).failure()

        assertEquals(InviteError.InvalidOrExpired, error)
    }

    @Test
    fun `preview preserves expired date`() = runTest {
        val error = fixture {
            problem(410, "INVITE_EXPIRED", expiredAt = EXPIRED_AT)
        }.gateway.preview(InviteCode(INVITE_CODE)).failure()

        assertEquals(InviteError.Expired(EXPIRED_AT), error)
    }

    @Test
    fun `preview maps rate limit and network failure`() = runTest {
        val rateLimit = fixture {
            problem(429, "INVITE_ATTEMPT_LIMIT", retryAfterSeconds = 37)
        }.gateway.preview(InviteCode(INVITE_CODE)).failure()
        assertEquals(InviteError.RateLimited(37), rateLimit)

        val networkError = fixture(MockEngine { throw UnresolvedAddressException() }).gateway
            .preview(InviteCode(INVITE_CODE)).failure()
        assertEquals(InviteError.DataFailure(DataError.Connectivity), networkError)
    }

    @Test
    fun `redeem maps joined and pending responses`() = runTest {
        val joined = fixture { redeem("ADMIN") }.gateway.redeem(InviteCode(INVITE_CODE)).success()
        assertEquals(InviteRedeemStatus.JOINED, joined.status)
        assertEquals(GROUP_ID, joined.groupId.value)
        assertEquals("ADMIN", joined.role)

        val pending = fixture { redeemPending() }.gateway.redeem(InviteCode(INVITE_CODE)).success()
        assertEquals(InviteRedeemStatus.PENDING, pending.status)
        assertEquals(GROUP_ID, pending.groupId.value)
        assertEquals(null, pending.role)
    }

    @Test
    fun `redeem maps every typed api error`() = runTest {
        assertEquals(
            InviteError.InvalidOrExpired,
            fixture { problem(404, "INVITE_INVALID_OR_EXPIRED") }.gateway.redeem(InviteCode(INVITE_CODE)).failure(),
        )
        assertEquals(
            InviteError.GroupDeleted,
            fixture { problem(410, "INVITE_GROUP_DELETED") }.gateway.redeem(InviteCode(INVITE_CODE)).failure(),
        )
        assertEquals(
            InviteError.RateLimited(19),
            fixture { problem(429, "INVITE_ATTEMPT_LIMIT", retryAfterSeconds = 19) }
                .gateway.redeem(InviteCode(INVITE_CODE)).failure(),
        )
        assertEquals(
            InviteError.PlanLimit,
            fixture { problem(422, "ATHLETE_LIMIT_EXCEEDED") }.gateway.redeem(InviteCode(INVITE_CODE)).failure(),
        )
    }

    @Test
    fun `redeem is never retried after server failure`() = runTest {
        var calls = 0
        val fixture = fixture { calls++; problem(503, "TEMPORARY") }

        assertEquals(
            InviteError.DataFailure(DataError.Server),
            fixture.gateway.redeem(InviteCode(INVITE_CODE)).failure(),
        )
        assertEquals(1, calls)
        assertTrue(fixture.delays.isEmpty())
    }

    private fun fixture(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = fixture(MockEngine { response(it) })

    private fun fixture(
        engine: MockEngine,
        tokenResult: TokenResult = TokenResult.Available("fake-token"),
    ): Fixture {
        val delays = mutableListOf<Long>()
        val network = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        return Fixture(
            gateway = KtorInviteGateway(
                network = network,
                authenticatedNetwork = AuthenticatedNetworkClient(network, Tokens(tokenResult), NoopInvalidator()),
                retryDelay = { delays += it },
            ),
            delays = delays,
        )
    }

    private fun MockRequestHandleScope.preview() = respond(
        """
        {
          "groupName":"Vôlei do CERET",
          "city":"Tatuapé",
          "composition":"MIXED",
          "level":"INTERMEDIATE",
          "memberCount":26,
          "regularSlots":[{"weekday":"TUESDAY","startTime":"19:30"}],
          "inviterName":"Ana",
          "entryRequiresApproval":true,
          "expiresAt":"$EXPIRED_AT",
          "nextGame":{"startsAt":"2026-08-04T22:30:00Z","venueName":"CERET","court":"Quadra 2"}
        }
        """.trimIndent(),
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.redeem(role: String) = respond(
        """{"status":"JOINED","groupId":"$GROUP_ID","role":"$role"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.redeemPending() = respond(
        """{"status":"PENDING","groupId":"$GROUP_ID","role":null}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.problem(
        status: Int,
        code: String,
        retryAfterSeconds: Int? = null,
        expiredAt: String? = null,
    ) = respond(
        buildString {
            append("{\"status\":$status,\"code\":\"$code\",\"correlationId\":\"private\"")
            retryAfterSeconds?.let { append(",\"retryAfterSeconds\":$it") }
            expiredAt?.let { append(",\"expiredAt\":\"$it\"") }
            append("}")
        },
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun HttpRequestData.bodyText() = (body as TextContent).text
    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement(bodyText()).jsonObject
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private inline fun <reified T> SaqzResult<T, InviteError>.success(): T =
        assertIs<SaqzResult.Success<T>>(this).value

    private fun SaqzResult<*, InviteError>.failure() =
        assertIs<SaqzResult.Failure<InviteError>>(this).error

    private class Tokens(private val tokenResult: TokenResult) : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(tokenResult)
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private data class Fixture(
        val gateway: KtorInviteGateway,
        val delays: MutableList<Long>,
    )

    private companion object {
        const val GROUP_ID = "group-1"
        const val INVITE_CODE = "fake-invite-code"
        const val EXPIRED_AT = "2026-08-31T23:59:00Z"
    }
}
