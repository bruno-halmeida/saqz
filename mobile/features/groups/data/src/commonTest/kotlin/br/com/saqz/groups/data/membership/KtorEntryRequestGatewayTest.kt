package br.com.saqz.groups.data.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupMembership
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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorEntryRequestGatewayTest {
    @Test
    fun `list uses exact route and maps requests`() = runTest {
        val requests = assertIs<SaqzResult.Success<List<GroupEntryRequest>>>(fixture { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/$GROUP_ID/entry-requests", request.url.encodedPath)
            requests()
        }.gateway.list(GroupId(GROUP_ID))).value

        assertEquals("user-1", requests.single().userId)
        assertEquals("Ana Souza", requests.single().displayName)
        assertEquals("2026-08-02T10:00:00Z", requests.single().requestedAt)
    }

    @Test
    fun `approve uses exact route and maps membership`() = runTest {
        val membership = assertIs<SaqzResult.Success<GroupMembership>>(fixture { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/$GROUP_ID/entry-requests/$USER_ID/approve", request.url.encodedPath)
            membership()
        }.gateway.approve(GroupId(GROUP_ID), USER_ID)).value

        assertEquals(USER_ID, membership.userId)
        assertEquals("Ana Souza", membership.displayName)
        assertEquals("ATHLETE", membership.role.name)
    }

    @Test
    fun `reject uses exact delete route and maps empty success`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/groups/$GROUP_ID/entry-requests/$USER_ID", request.url.encodedPath)
            respond("", HttpStatusCode.NoContent)
        }.gateway.reject(GroupId(GROUP_ID), USER_ID)
            .let { assertIs<SaqzResult.Success<Unit>>(it).value }
    }

    @Test
    fun `malformed request maps invalid response`() = runTest {
        val error = assertIs<SaqzResult.Failure<EntryRequestError>>(fixture {
            respond("[{\"userId\":\"\",\"displayName\":\"Ana\",\"requestedAt\":\"now\"}]", headers = jsonHeaders())
        }.gateway.list(GroupId(GROUP_ID))).error

        assertEquals(DataError.InvalidResponse, assertIs<EntryRequestError.DataFailure>(error).error)
    }

    @Test
    fun `forbidden maps shared data error`() = runTest {
        val error = assertIs<SaqzResult.Failure<EntryRequestError>>(fixture { problem(403) }
            .gateway.list(GroupId(GROUP_ID))).error

        assertEquals(DataError.Forbidden, assertIs<EntryRequestError.DataFailure>(error).error)
    }

    @Test
    fun `connectivity maps shared data error`() = runTest {
        val error = assertIs<SaqzResult.Failure<EntryRequestError>>(fixture(MockEngine { throw UnresolvedAddressException() })
            .gateway.list(GroupId(GROUP_ID))).error

        assertEquals(DataError.Connectivity, assertIs<EntryRequestError.DataFailure>(error).error)
    }

    private fun fixture(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        fixture(MockEngine { response(it) })

    private fun fixture(engine: MockEngine): Fixture {
        val network = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        val auth = AuthenticatedNetworkClient(
            network,
            Tokens(),
            object : SessionInvalidator {
                override fun invalidate() = Unit
            },
        )
        return Fixture(KtorEntryRequestGateway(auth),)
    }

    private fun MockRequestHandleScope.requests() = respond(
        """[{"userId":"$USER_ID","displayName":"Ana Souza","requestedAt":"2026-08-02T10:00:00Z"}]""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.membership() = respond(
        """{"userId":"$USER_ID","displayName":"Ana Souza","role":"ATHLETE"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.problem(status: Int) = respond(
        """{"status":$status,"code":"ERROR","correlationId":"private"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf("Content-Type", "application/json")

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("token"))
    }

    private data class Fixture(val gateway: KtorEntryRequestGateway)

    companion object {
        const val GROUP_ID = "group-1"
        const val USER_ID = "user-1"
    }
}
