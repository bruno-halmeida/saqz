package br.com.saqz.groups.data.attendance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorAttendanceGatewayTest {
    @Test
    fun `read uses attendance route`() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/group-1/games/game-1/attendance", request.url.encodedPath)
            detailResponse()
        }.read(GROUP, GAME)
    }

    @Test
    fun `read maps nullable own attendance`() = runTest {
        val result = gateway { detailResponse(DETAIL_WITHOUT_OWN) }.read(GROUP, GAME)

        assertNull(assertIs<SaqzResult.Success<*>>(result).value.let {
            it as br.com.saqz.groups.domain.attendance.AttendanceDetail
        }.ownAttendance)
    }

    @Test
    fun `read maps authoritative counts`() = runTest {
        val detail = successDetail()

        assertEquals(listOf(3, 21, 2, 24), listOf(
            detail.confirmedCount,
            detail.availableSpots,
            detail.waitlistCount,
            detail.capacity,
        ))
    }

    @Test fun `read maps confirmed status`() = statusCase("CONFIRMED", AttendanceStatus.Confirmed)
    @Test fun `read maps declined status`() = statusCase("DECLINED", AttendanceStatus.Declined)
    @Test fun `read maps waitlisted status`() = statusCase("WAITLISTED", AttendanceStatus.Waitlisted)

    @Test
    fun `read preserves waitlist position`() = runTest {
        assertEquals(4, successDetail().ownAttendance?.waitlistPosition)
    }

    @Test
    fun `respond uses put route and confirm intent`() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/group-1/games/game-1/attendance", request.url.encodedPath)
            assertEquals(KEY, request.bodyJson()["requestId"]?.jsonPrimitive?.content)
            assertEquals("CONFIRM", request.bodyJson()["intent"]?.jsonPrimitive?.content)
            mutationResponse()
        }.respond(GROUP, GAME, SelfAttendanceCommand(KEY, AttendanceIntent.Confirm))
    }

    @Test
    fun `respond maps decline intent`() = runTest {
        gateway { request ->
            assertEquals("DECLINE", request.bodyJson()["intent"]?.jsonPrimitive?.content)
            mutationResponse()
        }.respond(GROUP, GAME, SelfAttendanceCommand(KEY, AttendanceIntent.Decline))
    }

    @Test
    fun `respond preserves attendance etag`() = runTest {
        val result = gateway { mutationResponse(etag = "\"9\"") }
            .respond(GROUP, GAME, SelfAttendanceCommand(KEY, AttendanceIntent.Confirm))

        assertEquals(
            "\"9\"",
            assertIs<SaqzResult.Success<VersionedAttendanceMutation>>(result).value.version.value,
        )
    }

    @Test
    fun `respond maps audit and promotion`() = runTest {
        val result = gateway { mutationResponse() }
            .respond(GROUP, GAME, SelfAttendanceCommand(KEY, AttendanceIntent.Confirm))
        val mutation = assertIs<SaqzResult.Success<VersionedAttendanceMutation>>(result).value.value

        assertEquals("organizer-1", mutation.audit?.actorId)
        assertEquals(2, mutation.promotedCount)
    }

    @Test
    fun `override uses explicit route target reason and intent`() = runTest {
        gateway { request ->
            val body = request.bodyJson()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/group-1/games/game-1/attendance/override", request.url.encodedPath)
            assertEquals("member-2", body["memberId"]?.jsonPrimitive?.content)
            assertEquals("DECLINE", body["intent"]?.jsonPrimitive?.content)
            assertEquals("Correção", body["reason"]?.jsonPrimitive?.content)
            mutationResponse()
        }.override(
            GROUP,
            GAME,
            OverrideAttendanceCommand(KEY, "member-2", AttendanceIntent.Decline, "Correção"),
        )
    }

    @Test
    fun `capacity uses route request id and exact if match`() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/group-1/games/game-1/capacity", request.url.encodedPath)
            assertEquals("\"7\"", request.headers[HttpHeaders.IfMatch])
            assertEquals(KEY, request.bodyJson()["requestId"]?.jsonPrimitive?.content)
            assertEquals(30, request.bodyJson()["capacity"]?.jsonPrimitive?.content?.toInt())
            capacityResponse()
        }.capacity(GROUP, GAME, AttendanceVersionToken("\"7\""), AttendanceCapacityCommand(KEY, 30))
    }

    @Test
    fun `capacity preserves returned game etag`() = runTest {
        val result = gateway { capacityResponse(etag = "\"8\"") }
            .capacity(GROUP, GAME, AttendanceVersionToken("\"7\""), AttendanceCapacityCommand(KEY, 30))

        assertEquals(
            "\"8\"",
            assertIs<SaqzResult.Success<VersionedAttendanceCapacity>>(result).value.version.value,
        )
    }

    @Test
    fun `capacity maps version promotions and detail`() = runTest {
        val result = gateway { capacityResponse() }
            .capacity(GROUP, GAME, AttendanceVersionToken("\"7\""), AttendanceCapacityCommand(KEY, 30))
        val capacity = assertIs<SaqzResult.Success<VersionedAttendanceCapacity>>(result).value.value

        assertEquals(8L, capacity.version)
        assertEquals(2, capacity.promotedCount)
        assertEquals(30, capacity.detail.capacity)
    }

    @Test fun `validation maps safely`() = errorCase(400, "VALIDATION_FAILED", AttendanceError.Validation::class.simpleName)
    @Test fun `hidden game maps safely`() = errorCase(404, "GAME_NOT_FOUND", AttendanceError.HiddenResource::class.simpleName)
    @Test fun `hidden group maps safely`() = errorCase(404, "GROUP_NOT_FOUND", AttendanceError.HiddenResource::class.simpleName)
    @Test fun `deadline maps safely`() = errorCase(409, "ATTENDANCE_DEADLINE_PASSED", AttendanceError.DeadlinePassed::class.simpleName)
    @Test fun `frozen attendance maps safely`() = errorCase(409, "ATTENDANCE_FROZEN", AttendanceError.Frozen::class.simpleName)
    @Test fun `invalid transition maps frozen`() = errorCase(409, "INVALID_GAME_TRANSITION", AttendanceError.Frozen::class.simpleName)
    @Test fun `conflict maps safely`() = errorCase(409, "VERSION_CONFLICT", AttendanceError.Conflict::class.simpleName)
    @Test fun `authentication maps safely`() = errorCase(401, "AUTHENTICATION_REQUIRED", AttendanceError.Authentication::class.simpleName)
    @Test fun `forbidden maps safely`() = dataErrorCase(403, "FORBIDDEN", DataError.Forbidden)
    @Test fun `not found maps safely`() = dataErrorCase(404, "OTHER_NOT_FOUND", DataError.NotFound)
    @Test fun `payload limit maps safely`() = dataErrorCase(413, "PAYLOAD_TOO_LARGE", DataError.PayloadTooLarge)
    @Test fun `server maps safely after retry exhaustion`() = dataErrorCase(503, "SERVER", DataError.Server)

    @Test
    fun `malformed payload maps invalid response`() = runTest {
        val result = gateway { respond("{", headers = jsonHeaders()) }.read(GROUP, GAME)

        assertEquals(DataError.InvalidResponse, result.dataError())
    }

    @Test
    fun `missing mutation etag maps invalid response`() = runTest {
        val result = gateway { respond(MUTATION_JSON, headers = jsonHeaders()) }
            .respond(GROUP, GAME, SelfAttendanceCommand(KEY, AttendanceIntent.Confirm))

        assertEquals(DataError.InvalidResponse, result.dataError())
    }

    @Test
    fun `timeout retries exact schedule then succeeds`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = gateway(delay = delays::add) { request ->
            calls++
            if (calls < 4) throw HttpRequestTimeoutException(request) else detailResponse()
        }.read(GROUP, GAME)

        assertIs<SaqzResult.Success<*>>(result)
        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
        assertEquals(4, calls)
    }

    @Test
    fun `connectivity retries then exhausts`() = runTest {
        var calls = 0
        val result = gateway(delay = {}) {
            calls++
            throw UnresolvedAddressException()
        }.read(GROUP, GAME)

        assertEquals(DataError.Connectivity, result.dataError())
        assertEquals(4, calls)
    }

    @Test
    fun `validation never retries`() = runTest {
        var calls = 0
        gateway(delay = {}) {
            calls++
            problemResponse(400, "VALIDATION_FAILED")
        }.read(GROUP, GAME)

        assertEquals(1, calls)
    }

    @Test
    fun `idempotent response mutation retries transient failure`() = runTest {
        var calls = 0
        gateway(delay = {}) {
            calls++
            if (calls < 4) respond("", HttpStatusCode.ServiceUnavailable) else mutationResponse()
        }.respond(GROUP, GAME, SelfAttendanceCommand(KEY, AttendanceIntent.Confirm))

        assertEquals(4, calls)
    }

    @Test
    fun `blank response key disables retry`() = runTest {
        var calls = 0
        gateway(delay = {}) {
            calls++
            respond("", HttpStatusCode.ServiceUnavailable)
        }.respond(GROUP, GAME, SelfAttendanceCommand("", AttendanceIntent.Confirm))

        assertEquals(1, calls)
    }

    @Test
    fun `idempotent override retries transient failure`() = runTest {
        var calls = 0
        gateway(delay = {}) {
            calls++
            respond("", HttpStatusCode.ServiceUnavailable)
        }.override(GROUP, GAME, OverrideAttendanceCommand(KEY, "member", AttendanceIntent.Confirm, "reason"))

        assertEquals(4, calls)
    }

    @Test
    fun `idempotent capacity retries transient failure`() = runTest {
        var calls = 0
        gateway(delay = {}) {
            calls++
            respond("", HttpStatusCode.ServiceUnavailable)
        }.capacity(GROUP, GAME, AttendanceVersionToken("\"7\""), AttendanceCapacityCommand(KEY, 30))

        assertEquals(4, calls)
    }

    @Test
    fun `cancellation propagates`() = runTest {
        val gateway = gateway { throw CancellationException("cancel") }

        assertFailsWith<CancellationException> { gateway.read(GROUP, GAME) }
    }

    @Test
    fun `request body never contains bearer token`() = runTest {
        gateway { request ->
            assertTrue((request.body as TextContent).text.contains(KEY))
            assertTrue(!(request.body as TextContent).text.contains("test-token"))
            mutationResponse()
        }.respond(GROUP, GAME, SelfAttendanceCommand(KEY, AttendanceIntent.Confirm))
    }

    private fun statusCase(raw: String, expected: AttendanceStatus) = runTest {
        val result = gateway { detailResponse(DETAIL_JSON.replace("WAITLISTED", raw)) }.read(GROUP, GAME)
        val detail = assertIs<SaqzResult.Success<*>>(result).value as br.com.saqz.groups.domain.attendance.AttendanceDetail
        assertEquals(expected, detail.ownAttendance?.status)
    }

    private suspend fun successDetail() =
        (assertIs<SaqzResult.Success<*>>(gateway { detailResponse() }.read(GROUP, GAME)).value
            as br.com.saqz.groups.domain.attendance.AttendanceDetail)

    private fun errorCase(status: Int, code: String, expectedType: String?) = runTest {
        val result = gateway(delay = {}) { problemResponse(status, code) }.read(GROUP, GAME)
        assertEquals(expectedType, assertIs<SaqzResult.Failure<*>>(result).error::class.simpleName)
    }

    private fun dataErrorCase(status: Int, code: String, expected: DataError) = runTest {
        val result = gateway(delay = {}) { problemResponse(status, code) }.read(GROUP, GAME)
        assertEquals(expected, result.dataError())
    }

    private fun SaqzResult<*, AttendanceError>.dataError() =
        assertIs<AttendanceError.Data>(assertIs<SaqzResult.Failure<*>>(this).error).error

    private fun gateway(
        delay: suspend (Long) -> Unit = {},
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorAttendanceGateway {
        val client = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return KtorAttendanceGateway(
            AuthenticatedNetworkClient(client, Tokens(), NoopInvalidator()),
            delay,
        )
    }

    private fun MockRequestHandleScope.detailResponse(body: String = DETAIL_JSON) =
        respond(body, headers = jsonHeaders())

    private fun MockRequestHandleScope.mutationResponse(etag: String = "\"2\"") =
        respond(MUTATION_JSON, headers = versionedHeaders(etag))

    private fun MockRequestHandleScope.capacityResponse(etag: String = "\"8\"") =
        respond(CAPACITY_JSON, headers = versionedHeaders(etag))

    private fun MockRequestHandleScope.problemResponse(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"safe"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun HttpRequestData.bodyJson() =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun versionedHeaders(etag: String) = headersOf(
        HttpHeaders.ContentType to listOf("application/json"),
        HttpHeaders.ETag to listOf(etag),
    )

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("test-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        val GROUP = GroupId("group-1")
        const val GAME = "game-1"
        const val KEY = "request-key"
        const val DETAIL_JSON = """{"ownAttendance":{"memberId":"member-1","status":"WAITLISTED","waitlistPosition":4,"version":7},"confirmedCount":3,"availableSpots":21,"waitlistCount":2,"capacity":24}"""
        const val DETAIL_WITHOUT_OWN = """{"confirmedCount":3,"availableSpots":21,"waitlistCount":2,"capacity":24}"""
        const val MUTATION_JSON = """{"attendance":{"memberId":"member-1","status":"CONFIRMED","version":8},"audit":{"actorId":"organizer-1","source":"ORGANIZER_OVERRIDE","oldStatus":"WAITLISTED","newStatus":"CONFIRMED","reason":"Correção","occurredAt":"2026-08-12T22:30:00Z"},"promotedCount":2,"detail":{"ownAttendance":{"memberId":"member-1","status":"CONFIRMED","version":8},"confirmedCount":4,"availableSpots":20,"waitlistCount":1,"capacity":24}}"""
        const val CAPACITY_JSON = """{"capacity":30,"version":8,"promotedCount":2,"detail":{"confirmedCount":4,"availableSpots":26,"waitlistCount":0,"capacity":30}}"""
    }
}
