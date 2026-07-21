package br.com.saqz.groups.data.attendance.share

import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
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

class AttendanceShareApiTest {
    @Test
    fun `rotate posts exact attendance link route without body`() = runTest {
        val api = fixture { request ->
            assertEquals("POST", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/games/$GAME_ID/attendance-link", request.url.encodedPath)
            assertEquals(0, request.body.contentLength ?: 0)
            rotateLink()
        }

        api.rotateLink(GROUP_ID, GAME_ID)
    }

    @Test
    fun `rotate decodes opaque attendance link URL`() = runTest {
        val result = fixture { rotateLink() }.rotateLink(GROUP_ID, GAME_ID)

        assertEquals(LINK_URL, assertIs<NetworkResult.Success<AttendanceLinkUrlDto>>(result).value.url)
    }

    @Test
    fun `rotate URL carries no group game member or contact data`() = runTest {
        val result = assertIs<NetworkResult.Success<AttendanceLinkUrlDto>>(fixture { rotateLink() }.rotateLink(GROUP_ID, GAME_ID)).value.url

        assertFalse(result.contains(GROUP_ID))
        assertFalse(result.contains(GAME_ID))
        assertFalse(result.contains("member"))
        assertFalse(result.contains("phone"))
    }

    @Test
    fun `resolve posts exact global route and only code body`() = runTest {
        val api = fixture { request ->
            assertEquals("POST", request.method.value)
            assertEquals("/api/attendance-links/resolve", request.url.encodedPath)
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals(setOf("code"), body.keys)
            assertEquals(CODE, body.getValue("code").jsonPrimitive.content)
            resolved()
        }

        api.resolveLink(CODE)
    }

    @Test
    fun `resolve decodes exact destination ids`() = runTest {
        val result = fixture { resolved() }.resolveLink(CODE)

        val resolved = assertIs<NetworkResult.Success<ResolvedAttendanceLinkDto>>(result).value
        assertEquals(GROUP_ID, resolved.groupId)
        assertEquals(GAME_ID, resolved.gameId)
    }

    @Test
    fun `snapshot gets exact organizer route`() = runTest {
        val api = fixture { request ->
            assertEquals("GET", request.method.value)
            assertEquals("/api/groups/$GROUP_ID/games/$GAME_ID/attendance-share", request.url.encodedPath)
            snapshot()
        }

        api.readSnapshot(GROUP_ID, GAME_ID)
    }

    @Test
    fun `snapshot decodes three nominal sections without ids or timestamp`() = runTest {
        val result = fixture { snapshot() }.readSnapshot(GROUP_ID, GAME_ID)

        val snapshot = assertIs<NetworkResult.Success<AttendanceShareSnapshotDto>>(result).value
        assertEquals("Treino de quinta", snapshot.title)
        assertEquals(1, snapshot.confirmed.size)
        assertEquals(1, snapshot.waitlisted.size)
        assertEquals(1, snapshot.declined.size)
        assertEquals(null, snapshot.confirmed.first().waitlistPosition)
    }

    @Test
    fun `resolve terminal and retryable errors preserve backend problem codes`() = runTest {
        assertProblem(fixture { problem(404, "ATTENDANCE_LINK_INVALID_OR_EXPIRED") }.resolveLink(CODE), 404, "ATTENDANCE_LINK_INVALID_OR_EXPIRED")
        assertProblem(fixture { problem(429, "ATTENDANCE_LINK_ATTEMPT_LIMIT") }.resolveLink(CODE), 429, "ATTENDANCE_LINK_ATTEMPT_LIMIT")
        assertProblem(fixture { problem(503, "ATTENDANCE_LINK_UNAVAILABLE") }.resolveLink(CODE), 503, "ATTENDANCE_LINK_UNAVAILABLE")
    }

    private fun fixture(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): AttendanceShareApi {
        val network = NetworkClient(MockEngine { request -> response(request) }, NetworkConfig("test", "https://api.example.test/"))
        return AttendanceShareApi(AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator()))
    }

    private fun MockRequestHandleScope.rotateLink() = respond(
        """{"url":"$LINK_URL"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.resolved() = respond(
        """{"groupId":"$GROUP_ID","gameId":"$GAME_ID"}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.snapshot() = respond(
        """{"title":"Treino de quinta","startsAt":"2026-08-12T22:30:00Z","timeZone":"America/Sao_Paulo","venue":"Arena Central","capacity":12,"confirmed":[{"displayName":"Ana"}],"waitlisted":[{"displayName":"Bruno","waitlistPosition":1}],"declined":[{"displayName":"Carla"}]}""",
        headers = jsonHeaders(),
    )

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
        const val GAME_ID = "018f4f4d-6634-7be1-a018-fedcba543210"
        const val CODE = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
        const val LINK_URL = "https://join.example.test/?saqz_attendance=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
    }
}
