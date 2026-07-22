package br.com.saqz.groups.data.game

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryAction
import br.com.saqz.groups.domain.game.SeriesBoundaryCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.domain.game.WeeklySlot
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class KtorGameGatewayTest {
    @Test fun `list maps draft`() = statusCase("DRAFT", GameStatus.Draft)
    @Test fun `list maps published`() = statusCase("PUBLISHED", GameStatus.Published)
    @Test fun `list maps cancelled`() = statusCase("CANCELLED", GameStatus.Cancelled)
    @Test fun `list maps completed`() = statusCase("COMPLETED", GameStatus.Completed)

    @Test
    fun `list preserves aggregate counts`() = runTest {
        val game = successList().single()
        assertEquals(listOf(3, 21, 2), listOf(game.confirmedCount, game.availableSpots, game.waitlistCount))
    }

    @Test
    fun `list preserves null venue id`() = runTest {
        assertNull(successList().single().venue.venueId)
    }

    @Test
    fun `read preserves exact quoted etag`() = runTest {
        val result = gateway { gameResponse(etag = "\"23\"") }.read(GROUP, GAME)
        assertEquals("\"23\"", assertIs<SaqzResult.Success<*>>(result).value.let { it as br.com.saqz.groups.domain.game.VersionedGame }.version.value)
    }

    @Test
    fun `read without etag is invalid response`() = runTest {
        val result = gateway { respond(GAME_JSON, headers = jsonHeaders()) }.read(GROUP, GAME)
        assertEquals(GameError.Data(DataError.InvalidResponse), assertIs<SaqzResult.Failure<*>>(result).error)
    }

    @Test
    fun `create uses post route and stable key`() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/group-1/games", request.url.encodedPath)
            assertEquals(KEY, request.bodyJson()["requestId"]?.jsonPrimitive?.content)
            gameResponse(HttpStatusCode.Created)
        }.create(GROUP, gameCommand())
    }

    @Test
    fun `create omits server owned fields`() = runTest {
        gateway { request ->
            val body = request.bodyJson()
            assertFalse(body.containsKey("status"))
            assertFalse(body.containsKey("confirmedCount"))
            gameResponse(HttpStatusCode.Created)
        }.create(GROUP, gameCommand())
    }

    @Test
    fun `edit preserves if match`() = runTest {
        gateway { request ->
            assertEquals("\"7\"", request.headers[HttpHeaders.IfMatch])
            gameResponse()
        }.edit(GROUP, GAME, GameVersionToken("\"7\""), gameCommand())
    }

    @Test fun `publish uses explicit route`() = lifecycleRoute(GameLifecycleAction.Publish, "publish")
    @Test fun `cancel uses explicit route`() = lifecycleRoute(GameLifecycleAction.Cancel, "cancel")
    @Test fun `complete uses explicit route`() = lifecycleRoute(GameLifecycleAction.Complete, "complete")

    @Test
    fun `series create preserves local slots`() = runTest {
        gateway { request ->
            val slots = request.bodyJson()["slots"].toString()
            assertFalse(slots.contains("startsAt"))
            seriesResponse(HttpStatusCode.Created)
        }.createSeries(GROUP, seriesCommand())
    }

    @Test
    fun `series read maps weekday`() = runTest {
        val result = gateway { seriesResponse() }.readSeries(GROUP, SERIES)
        val series = (assertIs<SaqzResult.Success<*>>(result).value as br.com.saqz.groups.domain.game.VersionedSeries).series
        assertEquals(Weekday.Wednesday, series.slots.single().weekday)
    }

    @Test
    fun `series read preserves nullable fee`() = runTest {
        val result = gateway { seriesResponse(SERIES_JSON.replace(",\"gameFeeCents\":2500", "")) }.readSeries(GROUP, SERIES)
        val series = (assertIs<SaqzResult.Success<*>>(result).value as br.com.saqz.groups.domain.game.VersionedSeries).series
        assertNull(series.slots.single().gameFeeCents)
    }

    @Test
    fun `only this boundary preserves enum and key`() = runTest {
        gateway { request ->
            val body = request.bodyJson()
            assertEquals("ONLY_THIS", body["scope"]?.jsonPrimitive?.content)
            assertEquals(KEY, body["requestId"]?.jsonPrimitive?.content)
            seriesResponse()
        }.boundary(GROUP, SERIES, GameVersionToken("\"7\""), boundary())
    }

    @Test
    fun `future boundary preserves boundary date`() = runTest {
        gateway { request ->
            assertEquals("2026-08-19", request.bodyJson()["boundary"]?.jsonPrimitive?.content)
            seriesResponse()
        }.boundary(GROUP, SERIES, GameVersionToken("\"7\""), boundary().copy(scope = SeriesBoundaryScope.ThisAndFuture, boundary = "2026-08-19"))
    }

    @Test fun `validation maps safely`() = errorCase(400, "VALIDATION_FAILED", GameError.Validation::class.simpleName)
    @Test fun `hidden game maps safely`() = errorCase(404, "GAME_NOT_FOUND", GameError.HiddenResource::class.simpleName)
    @Test fun `hidden group maps safely`() = errorCase(404, "GROUP_NOT_FOUND", GameError.HiddenResource::class.simpleName)
    @Test fun `conflict maps safely`() = errorCase(409, "VERSION_CONFLICT", GameError.Conflict::class.simpleName)
    @Test fun `invalid lifecycle maps safely`() = errorCase(409, "INVALID_GAME_TRANSITION", GameError.InvalidLifecycle::class.simpleName)
    @Test fun `authentication maps safely`() = errorCase(401, "AUTHENTICATION_REQUIRED", GameError.Authentication::class.simpleName)
    @Test fun `forbidden maps safely`() = errorCase(403, "FORBIDDEN", GameError.Data(DataError.Forbidden)::class.simpleName)
    @Test fun `not found maps safely`() = errorCase(404, "OTHER_NOT_FOUND", GameError.Data(DataError.NotFound)::class.simpleName)
    @Test fun `payload limit maps safely`() = errorCase(413, "PAYLOAD_TOO_LARGE", GameError.Data(DataError.PayloadTooLarge)::class.simpleName)
    @Test fun `server maps safely`() = errorCase(503, "SERVER", GameError.Data(DataError.Server)::class.simpleName)

    @Test
    fun `read retries exact schedule then succeeds`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = gateway(delay = delays::add) {
            calls++
            if (calls < 4) respond("", HttpStatusCode.ServiceUnavailable) else gameResponse()
        }.read(GROUP, GAME)
        assertIs<SaqzResult.Success<*>>(result)
        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
        assertEquals(4, calls)
    }

    @Test
    fun `read stops retries on early success`() = runTest {
        var calls = 0
        val result = gateway { calls++; gameResponse() }.read(GROUP, GAME)
        assertIs<SaqzResult.Success<*>>(result)
        assertEquals(1, calls)
    }

    @Test
    fun `unsafe create does not retry`() = runTest {
        var calls = 0
        gateway { calls++; respond("", HttpStatusCode.ServiceUnavailable) }
            .create(GROUP, gameCommand().copy(requestId = null))
        assertEquals(1, calls)
    }

    @Test
    fun `idempotent create retries`() = runTest {
        var calls = 0
        gateway(delay = {}) { calls++; respond("", HttpStatusCode.ServiceUnavailable) }
            .create(GROUP, gameCommand())
        assertEquals(4, calls)
    }

    @Test
    fun `lifecycle never retries`() = runTest {
        var calls = 0
        gateway { calls++; respond("", HttpStatusCode.ServiceUnavailable) }
            .lifecycle(GROUP, GAME, GameVersionToken("\"7\""), GameLifecycleAction.Publish)
        assertEquals(1, calls)
    }

    @Test
    fun `cancellation propagates`() = runTest {
        val gateway = gateway { throw CancellationException("cancel") }
        kotlin.test.assertFailsWith<CancellationException> { gateway.read(GROUP, GAME) }
    }

    private fun statusCase(raw: String, expected: GameStatus) = runTest {
        val result = gateway { respond("[${GAME_JSON.replace("DRAFT", raw)}]", headers = jsonHeaders()) }.list(GROUP)
        assertEquals(expected, assertIs<SaqzResult.Success<List<br.com.saqz.groups.domain.game.Game>>>(result).value.single().status)
    }

    private suspend fun successList() = assertIs<SaqzResult.Success<List<br.com.saqz.groups.domain.game.Game>>>(
        gateway { respond("[$GAME_JSON]", headers = jsonHeaders()) }.list(GROUP),
    ).value

    private fun lifecycleRoute(action: GameLifecycleAction, route: String) = runTest {
        gateway { request ->
            assertEquals("/api/groups/group-1/games/game-1/$route", request.url.encodedPath)
            gameResponse()
        }.lifecycle(GROUP, GAME, GameVersionToken("\"7\""), action)
    }

    private fun errorCase(status: Int, code: String, expectedType: String?) = runTest {
        val result = gateway {
            respond("""{"status":$status,"code":"$code","correlationId":"safe"}""", HttpStatusCode.fromValue(status), jsonHeaders())
        }.read(GROUP, GAME)
        assertEquals(expectedType, assertIs<SaqzResult.Failure<*>>(result).error::class.simpleName)
    }

    private fun gateway(
        delay: suspend (Long) -> Unit = {},
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorGameGateway {
        val network = NetworkClient(MockEngine { request -> handler(request) }, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"))
        return KtorGameGateway(AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator()), delay)
    }

    private fun MockRequestHandleScope.gameResponse(status: HttpStatusCode = HttpStatusCode.OK, etag: String = "\"7\"") =
        respond(GAME_JSON, status, versionedHeaders(etag))

    private fun MockRequestHandleScope.seriesResponse(body: String = SERIES_JSON) = respond(body, headers = versionedHeaders("\"7\""))
    private fun MockRequestHandleScope.seriesResponse(status: HttpStatusCode) = respond(SERIES_JSON, status, versionedHeaders("\"7\""))
    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement((body as TextContent).text).jsonObject
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
    private fun versionedHeaders(etag: String) = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf(etag))
    private fun gameCommand() = GameWriteCommand(KEY, "Treino", GameVenue(null, "Arena", "Rua"), "2026-08-12", "19:30:00", "America/Sao_Paulo", "2026-08-12T22:30:00Z", 90, 24, "2026-08-12T19:30:00Z", 2500, false, "Notas")
    private fun seriesCommand() = WeeklySeriesWriteCommand(KEY, "revision-1", "America/Sao_Paulo", "2026-08-12", slots = listOf(WeeklySlot("slot-1", Weekday.Wednesday, "19:30:00", 90, GameVenue(null, "Arena", "Rua"), 24, 180, 2500, "Treino")))
    private fun boundary() = SeriesBoundaryCommand(KEY, SeriesBoundaryScope.OnlyThis, SeriesBoundaryAction.Cancel, gameId = GAME)

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        val GROUP = GroupId("group-1")
        const val GAME = "game-1"
        const val SERIES = "series-1"
        const val KEY = "request-key"
        const val GAME_JSON = """{"id":"game-1","groupId":"group-1","title":"Treino","venue":{"name":"Arena","address":"Rua"},"localDate":"2026-08-12","localTime":"19:30:00","zoneId":"America/Sao_Paulo","startsAt":"2026-08-12T22:30:00Z","durationMinutes":90,"capacity":24,"confirmationDeadline":"2026-08-12T19:30:00Z","gameFeeCents":2500,"notes":"Notas","status":"DRAFT","version":1,"confirmedCount":3,"availableSpots":21,"waitlistCount":2}"""
        const val SERIES_JSON = """{"id":"series-1","revisionId":"revision-1","revisionNumber":1,"zoneId":"America/Sao_Paulo","localStartDate":"2026-08-12","slots":[{"slotKey":"slot-1","weekday":"WEDNESDAY","localTime":"19:30:00","durationMinutes":90,"venue":{"name":"Arena","address":"Rua"},"capacity":24,"confirmationLeadMinutes":180,"gameFeeCents":2500,"title":"Treino"}],"occurrences":[{"id":"game-1","localDate":"2026-08-12","localTime":"19:30:00","startsAt":"2026-08-12T22:30:00Z","status":"DRAFT","version":1}],"version":1}"""
    }
}
