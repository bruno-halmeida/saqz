package br.com.saqz.groups.data

import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RolesInvitesApiTest {
    @Test
    fun `list gets exact memberships route`() = runTest {
        val api = fixture { request ->
            assertEquals("GET", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/memberships", request.url.encodedPath)
            memberships()
        }

        api.listMemberships(GROUP_ID)
    }

    @Test
    fun `list decodes minimal memberships and roles`() = runTest {
        val result = fixture { memberships() }.listMemberships(GROUP_ID)

        val values = assertIs<NetworkResult.Success<List<MembershipDto>>>(result).value
        assertEquals(2, values.size)
        assertEquals(USER_ID, values[1].userId)
        assertEquals("Athlete Person", values[1].displayName)
        assertEquals(GroupRoleDto.ATHLETE, values[1].role)
    }

    @Test
    fun `change role puts exact member route`() = runTest {
        val api = fixture { request ->
            assertEquals("PUT", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/memberships/$USER_ID/role", request.url.encodedPath)
            membership("ADMIN")
        }

        api.changeRole(GROUP_ID, USER_ID, PersistedRoleDto.ADMIN)
    }

    @Test
    fun `change role sends only persisted role`() = runTest {
        val api = fixture { request ->
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals(setOf("role"), body.keys)
            assertEquals("ATHLETE", body.getValue("role").jsonPrimitive.content)
            membership("ATHLETE")
        }

        api.changeRole(GROUP_ID, USER_ID, PersistedRoleDto.ATHLETE)
    }

    @Test
    fun `change role input cannot represent owner`() {
        assertEquals(setOf("ADMIN", "ATHLETE"), PersistedRoleDto.entries.map { it.name }.toSet())
    }

    @Test
    fun `change role decodes authoritative membership`() = runTest {
        val result = fixture { membership("ADMIN") }
            .changeRole(GROUP_ID, USER_ID, PersistedRoleDto.ADMIN)

        val member = assertIs<NetworkResult.Success<MembershipDto>>(result).value
        assertEquals(USER_ID, member.userId)
        assertEquals("Athlete Person", member.displayName)
        assertEquals(GroupRoleDto.ADMIN, member.role)
    }

    @Test
    fun `rotate posts exact invite route without body`() = runTest {
        val api = fixture { request ->
            assertEquals("POST", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/invite", request.url.encodedPath)
            assertEquals(0, request.body.contentLength ?: 0)
            inviteUrl()
        }

        api.rotateInvite(GROUP_ID)
    }

    @Test
    fun `rotate returns only complete invite URL`() = runTest {
        val result = fixture { inviteUrl() }.rotateInvite(GROUP_ID)

        assertEquals(INVITE_URL, assertIs<NetworkResult.Success<InviteUrlDto>>(result).value.inviteUrl)
    }

    @Test
    fun `rotate URL carries no group user role or email data`() = runTest {
        val result = assertIs<NetworkResult.Success<InviteUrlDto>>(fixture { inviteUrl() }.rotateInvite(GROUP_ID)).value.inviteUrl

        assertFalse(result.contains(GROUP_ID))
        assertFalse(result.contains(USER_ID))
        assertFalse(result.contains("OWNER"))
        assertFalse(result.contains("email"))
    }

    @Test
    fun `expire deletes exact invite route`() = runTest {
        val api = fixture { request ->
            assertEquals("DELETE", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/invite", request.url.encodedPath)
            noContent()
        }

        api.expireInvite(GROUP_ID)
    }

    @Test
    fun `expire accepts empty 204`() = runTest {
        val result = fixture { noContent() }.expireInvite(GROUP_ID)

        assertEquals(Unit, assertIs<NetworkResult.Success<Unit>>(result).value)
    }

    @Test
    fun `redeem posts exact global route`() = runTest {
        val api = fixture { request ->
            assertEquals("POST", request.method.value)
            assertEquals("/api/invites/redeem", request.url.encodedPath)
            redeemed("ATHLETE")
        }

        api.redeem(INVITE_CODE)
    }

    @Test
    fun `redeem sends code as its only body field`() = runTest {
        val api = fixture { request ->
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals(setOf("code"), body.keys)
            assertEquals(INVITE_CODE, body.getValue("code").jsonPrimitive.content)
            redeemed("ATHLETE")
        }

        api.redeem(INVITE_CODE)
    }

    @Test
    fun `redeem decodes preserved authoritative role`() = runTest {
        val result = fixture { redeemed("ADMIN") }.redeem(INVITE_CODE)

        val redeemed = assertIs<NetworkResult.Success<RedeemedInviteDto>>(result).value
        assertEquals(GROUP_ID, redeemed.groupId)
        assertEquals(GroupRoleDto.ADMIN, redeemed.role)
    }

    @Test
    fun `list maps owner-only forbidden problem`() = runTest {
        assertProblem(fixture { problem(403, "ACCESS_FORBIDDEN") }.listMemberships(GROUP_ID), 403, "ACCESS_FORBIDDEN")
    }

    @Test
    fun `change maps hidden group as not found`() = runTest {
        val result = fixture { problem(404, "GROUP_NOT_FOUND") }
            .changeRole(GROUP_ID, USER_ID, PersistedRoleDto.ADMIN)

        assertProblem(result, 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `rotate maps insufficient role without URL`() = runTest {
        val result = fixture { problem(403, "ACCESS_FORBIDDEN") }.rotateInvite(GROUP_ID)

        assertProblem(result, 403, "ACCESS_FORBIDDEN")
        assertFalse(result.toString().contains(INVITE_URL))
    }

    @Test
    fun `expire maps hidden group as not found`() = runTest {
        assertProblem(fixture { problem(404, "GROUP_NOT_FOUND") }.expireInvite(GROUP_ID), 404, "GROUP_NOT_FOUND")
    }

    @Test
    fun `invalid redeem error retains neither raw code nor group`() = runTest {
        val result = fixture { problem(404, "INVITE_INVALID_OR_EXPIRED") }.redeem(INVITE_CODE)

        assertProblem(result, 404, "INVITE_INVALID_OR_EXPIRED")
        assertFalse(result.toString().contains(INVITE_CODE))
        assertFalse(result.toString().contains(GROUP_ID))
    }

    @Test
    fun `limited redeem preserves retry seconds without raw code`() = runTest {
        val result = fixture {
            respond(
                """{"status":429,"code":"INVITE_ATTEMPT_LIMIT","correlationId":"corr-429","retryAfterSeconds":37}""",
                HttpStatusCode.TooManyRequests,
                jsonHeaders(),
            )
        }.redeem(INVITE_CODE)

        val problem = assertIs<NetworkError.ApiProblemError>(assertIs<NetworkResult.Failure>(result).error).problem
        assertEquals(429, problem.status)
        assertEquals("INVITE_ATTEMPT_LIMIT", problem.code)
        assertEquals(37, problem.retryAfterSeconds)
        assertFalse(result.toString().contains(INVITE_CODE))
    }

    private fun fixture(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RolesInvitesApi {
        val network = NetworkClient(MockEngine { request -> response(request) }, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"))
        return RolesInvitesApi(AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator()))
    }

    private fun MockRequestHandleScope.memberships() = respond(
        """[{"userId":"owner-id","displayName":"Owner Person","role":"OWNER"},{"userId":"$USER_ID","displayName":"Athlete Person","role":"ATHLETE"}]""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.membership(role: String) = respond(
        """{"userId":"$USER_ID","displayName":"Athlete Person","role":"$role"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.inviteUrl() = respond(
        """{"inviteUrl":"$INVITE_URL"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.redeemed(role: String) = respond(
        """{"groupId":"$GROUP_ID","role":"$role"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.noContent() = respond("", HttpStatusCode.NoContent)

    private fun MockRequestHandleScope.problem(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"corr-$status"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun assertProblem(result: NetworkResult<*>, status: Int, code: String) {
        val problem = assertIs<NetworkError.ApiProblemError>(assertIs<NetworkResult.Failure>(result).error).problem
        assertEquals(status, problem.status)
        assertEquals(code, problem.code)
    }

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available(if (forceRefresh) "fresh-token" else "old-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        const val GROUP_ID = "018f4f4d-6634-7be1-a018-abcdef012345"
        const val USER_ID = "018f4f4d-6634-7be1-a018-fedcba543210"
        const val INVITE_CODE = "A234567890123456789012345678901234567890123"
        const val INVITE_URL = "https://join.example.test/a/invite?saqz_invite=A234567890123456789012345678901234567890123"
    }
}
