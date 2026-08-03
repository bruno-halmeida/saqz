package br.com.saqz.groups.data.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupMembershipError
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
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorGroupMembershipGatewayTest {
    @Test
    fun `list uses exact route and method`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/$GROUP_ID/memberships", request.url.encodedPath)
            memberships()
        }.gateway.listMemberships(GroupId(GROUP_ID))
    }

    @Test
    fun `list maps complete memberships and roles`() = runTest {
        val members = fixture { memberships() }.gateway.listMemberships(GroupId(GROUP_ID)).success()

        assertEquals(2, members.size)
        assertEquals(
            Triple(USER_ID, "Athlete Person", GroupRole.ATHLETE),
            members[1].let { Triple(it.userId, it.displayName, it.role) },
        )
    }

    @Test
    fun `change role uses exact member route`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/$GROUP_ID/memberships/$USER_ID/role", request.url.encodedPath)
            membership("ADMIN")
        }.gateway.changeRole(change(AssignableGroupRole.ADMIN))
    }

    @Test
    fun `change role sends only assignable role`() = runTest {
        fixture { request ->
            val body = request.bodyJson()
            assertEquals(setOf("role"), body.keys)
            assertEquals("ATHLETE", body.getValue("role").jsonPrimitive.content)
            membership("ATHLETE")
        }.gateway.changeRole(change(AssignableGroupRole.ATHLETE))
    }

    @Test
    fun `change role maps authoritative membership`() = runTest {
        val member = fixture { membership("ADMIN") }.gateway.changeRole(change(AssignableGroupRole.ADMIN)).success()

        assertEquals(
            Triple(USER_ID, "Athlete Person", GroupRole.ADMIN),
            member.let { Triple(it.userId, it.displayName, it.role) },
        )
    }

    @Test
    fun `rotate uses exact bodyless invite route`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/$GROUP_ID/invite", request.url.encodedPath)
            assertEquals(0, request.body.contentLength ?: 0)
            inviteUrl()
        }.gateway.rotateInvite(GroupId(GROUP_ID))
    }

    @Test
    fun `rotate maps invite URL and expiration`() = runTest {
        val invite = fixture { inviteUrl() }.gateway.rotateInvite(GroupId(GROUP_ID)).success()

        assertEquals(INVITE_URL, invite.value)
        assertEquals(EXPIRES_AT, invite.expiresAt)
    }

    @Test
    fun `metadata uses exact get route`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/$GROUP_ID/invite", request.url.encodedPath)
            inviteMetadata()
        }.gateway.readInviteMetadata(GroupId(GROUP_ID))
    }

    @Test
    fun `metadata maps active fields without exposing a URL`() = runTest {
        val metadata = fixture { inviteMetadata() }.gateway.readInviteMetadata(GroupId(GROUP_ID)).success()

        assertTrue(metadata.active)
        assertEquals("2026-08-09T12:00:00Z", metadata.expiresAt)
        assertEquals("2026-08-02T12:00:00Z", metadata.createdAt)
        assertEquals("Owner Person", metadata.createdByName)
    }

    @Test
    fun `inactive metadata accepts omitted optional fields`() = runTest {
        val metadata = fixture { respond("{\"active\":false}", headers = jsonHeaders()) }
            .gateway.readInviteMetadata(GroupId(GROUP_ID)).success()

        assertFalse(metadata.active)
        assertEquals(null, metadata.expiresAt)
        assertEquals(null, metadata.createdByName)
    }

    @Test
    fun `invite URL exposes no unrelated membership data`() = runTest {
        val url = fixture { inviteUrl() }.gateway.rotateInvite(GroupId(GROUP_ID)).success().value

        assertFalse(url.contains(GROUP_ID))
        assertFalse(url.contains(USER_ID))
        assertFalse(url.contains("OWNER"))
    }

    @Test
    fun `expire uses exact delete route`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/groups/$GROUP_ID/invite", request.url.encodedPath)
            noContent()
        }.gateway.expireInvite(GroupId(GROUP_ID))
    }

    @Test
    fun `expire maps empty success`() = runTest {
        assertEquals(Unit, fixture { noContent() }.gateway.expireInvite(GroupId(GROUP_ID)).success())
    }

    @Test
    fun `forbidden maps shared forbidden`() = runTest {
        assertEquals(
            DataError.Forbidden,
            fixture { problem(403, "ACCESS_FORBIDDEN") }
                .gateway.listMemberships(GroupId(GROUP_ID)).dataError(),
        )
    }

    @Test
    fun `not found maps shared not found`() = runTest {
        assertEquals(
            DataError.NotFound,
            fixture { problem(404, "GROUP_NOT_FOUND") }.gateway.changeRole(change()).dataError(),
        )
    }

    @Test
    fun `missing required membership field maps invalid response`() = runTest {
        assertEquals(
            DataError.InvalidResponse,
            fixture { respond("""[{"userId":"","displayName":"Athlete","role":"ATHLETE"}]""", headers = jsonHeaders()) }
                .gateway.listMemberships(GroupId(GROUP_ID)).dataError(),
        )
    }

    @Test
    fun `missing invite URL maps invalid response`() = runTest {
        assertEquals(
            DataError.InvalidResponse,
            fixture { respond("{}", headers = jsonHeaders()) }
                .gateway.rotateInvite(GroupId(GROUP_ID)).dataError(),
        )
    }

    @Test
    fun `read retries server failure and exhausts four calls`() = runTest {
        var calls = 0
        val fixture = fixture { calls++; problem(503, "TEMPORARY") }

        assertEquals(DataError.Server, fixture.gateway.listMemberships(GroupId(GROUP_ID)).dataError())
        assertEquals(4, calls)
        assertEquals(listOf(500L, 1000L, 2000L), fixture.delays)
    }

    @Test
    fun `read stops retrying immediately after success`() = runTest {
        var calls = 0
        val fixture = fixture { if (++calls == 1) problem(503, "TEMPORARY") else memberships() }

        assertEquals(2, fixture.gateway.listMemberships(GroupId(GROUP_ID)).success().size)
        assertEquals(2, calls)
        assertEquals(listOf(500L), fixture.delays)
    }

    @Test
    fun `unsafe writes never retry server failure`() = runTest {
        suspend fun assertSingleCall(operation: suspend (KtorGroupMembershipGateway) -> Unit) {
            var calls = 0
            val fixture = fixture { calls++; problem(503, "TEMPORARY") }

            operation(fixture.gateway)
            assertEquals(1, calls)
            assertTrue(fixture.delays.isEmpty())
        }

        assertSingleCall { it.changeRole(change()) }
        assertSingleCall { it.rotateInvite(GroupId(GROUP_ID)) }
        assertSingleCall { it.expireInvite(GroupId(GROUP_ID)) }
    }

    @Test
    fun `shared transport failures map exhaustively`() = runTest {
        assertEquals(
            DataError.Unauthenticated,
            fixture { problem(401, "AUTH") }.gateway.listMemberships(GroupId(GROUP_ID)).dataError(),
        )
        assertEquals(DataError.Conflict, fixture { problem(409, "CONFLICT") }.gateway.changeRole(change()).dataError())
        assertEquals(
            DataError.PayloadTooLarge,
            fixture { problem(413, "LARGE") }.gateway.rotateInvite(GroupId(GROUP_ID)).dataError(),
        )
        assertEquals(
            DataError.Timeout,
            fixture(MockEngine { throw HttpRequestTimeoutException(it) })
                .gateway.listMemberships(GroupId(GROUP_ID)).dataError(),
        )
        assertEquals(
            DataError.Connectivity,
            fixture(MockEngine { throw UnresolvedAddressException() })
                .gateway.listMemberships(GroupId(GROUP_ID)).dataError(),
        )
    }

    @Test
    fun `cancellation propagates`() = runTest {
        assertFailsWith<CancellationException> {
            fixture(MockEngine { throw CancellationException("cancel") }).gateway.listMemberships(GroupId(GROUP_ID))
        }
    }

    @Test
    fun `unknown failure remains credential safe`() = runTest {
        val result = fixture(MockEngine { throw IllegalStateException("secret-token") })
            .gateway.listMemberships(GroupId(GROUP_ID))

        assertEquals(DataError.Unknown, result.dataError())
        assertFalse(result.toString().contains("secret-token"))
    }

    private fun fixture(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = fixture(MockEngine { response(it) })

    private fun fixture(engine: MockEngine): Fixture {
        val delays = mutableListOf<Long>()
        val network = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        val authenticated = AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())
        return Fixture(KtorGroupMembershipGateway(authenticated, retryDelay = { delays += it }), delays)
    }

    private fun MockRequestHandleScope.memberships() = respond(
        """[
            {"userId":"owner-id","displayName":"Owner Person","role":"OWNER"},
            {"userId":"$USER_ID","displayName":"Athlete Person","role":"ATHLETE"}
        ]""".trimIndent(),
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.membership(role: String) = respond(
        """{"userId":"$USER_ID","displayName":"Athlete Person","role":"$role"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.inviteUrl() = respond(
        """{"inviteUrl":"$INVITE_URL","expiresAt":"$EXPIRES_AT"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.inviteMetadata() = respond(
        """{"active":true,"expiresAt":"2026-08-09T12:00:00Z","createdAt":"2026-08-02T12:00:00Z","createdByName":"Owner Person"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.noContent() = respond("", HttpStatusCode.NoContent)

    private fun MockRequestHandleScope.problem(
        status: Int,
        code: String,
        fields: String? = null,
        retryAfterSeconds: Int? = null,
    ) = respond(
        buildString {
            append("{\"status\":$status,\"code\":\"$code\",\"correlationId\":\"private\"")
            fields?.let { append(",\"fieldErrors\":{$it}") }
            retryAfterSeconds?.let { append(",\"retryAfterSeconds\":$it") }
            append("}")
        },
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun HttpRequestData.bodyText() = (body as TextContent).text
    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement(bodyText()).jsonObject
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun change(role: AssignableGroupRole = AssignableGroupRole.ADMIN) = ChangeMembershipRoleCommand(
        GroupId(GROUP_ID),
        USER_ID,
        role,
    )

    private inline fun <reified T> SaqzResult<T, GroupMembershipError>.success(): T =
        assertIs<SaqzResult.Success<T>>(this).value

    private fun SaqzResult<*, GroupMembershipError>.failure() =
        assertIs<SaqzResult.Failure<GroupMembershipError>>(this).error

    private fun SaqzResult<*, GroupMembershipError>.dataError() =
        assertIs<GroupMembershipError.DataFailure>(failure()).error

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private data class Fixture(
        val gateway: KtorGroupMembershipGateway,
        val delays: MutableList<Long>,
    )

    private companion object {
        const val GROUP_ID = "group-1"
        const val USER_ID = "user-1"
        const val INVITE_URL = "https://join.example.test/invite/fake"
        const val EXPIRES_AT = "2026-08-09T12:00:00Z"
    }
}
