package br.com.saqz.groups.data.moderation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.moderation.BlockedPerson
import br.com.saqz.groups.domain.moderation.ContentReport
import br.com.saqz.groups.domain.moderation.ModerationError
import br.com.saqz.groups.domain.moderation.ReportReason
import br.com.saqz.groups.domain.moderation.ReportTargetType
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class KtorModerationGatewayTest {
    @Test
    fun `report posts the exact contract body and accepts no content`() = runTest {
        var captured: HttpRequestData? = null
        val result = gateway { request ->
            captured = request
            respond("", HttpStatusCode.NoContent)
        }.fileContentReport(
            ContentReport(GroupId(GROUP_ID), ReportTargetType.MESSAGE, "message-1", ReportReason.HARASSMENT, "  ofensa  "),
        )

        assertEquals(SaqzResult.Success(Unit), result)
        val request = checkNotNull(captured)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/reports", request.url.encodedPath)
        assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
        val body = request.bodyJson()
        assertEquals(setOf("groupId", "targetType", "targetId", "reason", "details"), body.keys)
        assertEquals(GROUP_ID, body.getValue("groupId").jsonPrimitive.content)
        assertEquals("MESSAGE", body.getValue("targetType").jsonPrimitive.content)
        assertEquals("message-1", body.getValue("targetId").jsonPrimitive.content)
        assertEquals("HARASSMENT", body.getValue("reason").jsonPrimitive.content)
        assertEquals("ofensa", body.getValue("details").jsonPrimitive.content)
    }

    @Test
    fun `blank details are omitted and long details are capped`() = runTest {
        var blank: HttpRequestData? = null
        gateway { blank = it; respond("", HttpStatusCode.NoContent) }.fileContentReport(
            ContentReport(GroupId(GROUP_ID), ReportTargetType.GROUP, GROUP_ID, ReportReason.SPAM, "   "),
        )
        assertFalse("details" in checkNotNull(blank).bodyJson().keys)

        var long: HttpRequestData? = null
        gateway { long = it; respond("", HttpStatusCode.NoContent) }.fileContentReport(
            ContentReport(GroupId(GROUP_ID), ReportTargetType.USER, USER_ID, ReportReason.OTHER, "a".repeat(1500)),
        )
        assertEquals(1000, checkNotNull(long).bodyJson().getValue("details").jsonPrimitive.content.length)
    }

    @Test
    fun `report maps validation and hidden target`() = runTest {
        val validation = gateway {
            respond(
                """{"status":422,"code":"VALIDATION_FAILED","fieldErrors":{"details":["too_long"]}}""",
                HttpStatusCode.UnprocessableEntity,
                jsonHeaders,
            )
        }.fileContentReport(report())
        val error = assertIs<ModerationError.Validation>(assertIs<SaqzResult.Failure<ModerationError>>(validation).error)
        assertEquals(mapOf("details" to listOf("too_long")), error.details.fieldMessages)

        val hidden = gateway { respond("", HttpStatusCode.NotFound) }.fileContentReport(report())
        assertEquals(SaqzResult.Failure(ModerationError.DataFailure(DataError.NotFound)), hidden)
    }

    @Test
    fun `report is never retried`() = runTest {
        var calls = 0
        gateway { calls++; respond("", HttpStatusCode.ServiceUnavailable) }.fileContentReport(report())
        assertEquals(1, calls)
    }

    @Test
    fun `blocked list maps people and rejects incomplete rows`() = runTest {
        val people = gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/me/blocks", request.url.encodedPath)
            respond(
                """[{"userId":"$USER_ID","displayName":"Bia Souza","blockedAt":"2026-10-01T12:00:00Z"}]""",
                headers = jsonHeaders,
            )
        }.listBlockedPeople()
        assertEquals(SaqzResult.Success(listOf(BlockedPerson(USER_ID, "Bia Souza", "2026-10-01T12:00:00Z"))), people)

        val broken = gateway { respond("""[{"displayName":"Sem id"}]""", headers = jsonHeaders) }.listBlockedPeople()
        assertEquals(SaqzResult.Failure(ModerationError.DataFailure(DataError.InvalidResponse)), broken)
    }

    @Test
    fun `block puts the group of the shared membership`() = runTest {
        var captured: HttpRequestData? = null
        val result = gateway { request ->
            captured = request
            respond("", HttpStatusCode.NoContent)
        }.blockPerson(USER_ID, GroupId(GROUP_ID))

        assertEquals(SaqzResult.Success(Unit), result)
        val request = checkNotNull(captured)
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/me/blocks/$USER_ID", request.url.encodedPath)
        assertEquals(setOf("groupId"), request.bodyJson().keys)
        assertEquals(GROUP_ID, request.bodyJson().getValue("groupId").jsonPrimitive.content)
    }

    @Test
    fun `block maps self block and stranger`() = runTest {
        val self = gateway {
            respond("""{"status":422,"code":"VALIDATION_FAILED"}""", HttpStatusCode.UnprocessableEntity, jsonHeaders)
        }.blockPerson(USER_ID, GroupId(GROUP_ID))
        assertIs<ModerationError.Validation>(assertIs<SaqzResult.Failure<ModerationError>>(self).error)

        val stranger = gateway { respond("", HttpStatusCode.NotFound) }.blockPerson(USER_ID, GroupId(GROUP_ID))
        assertEquals(SaqzResult.Failure(ModerationError.DataFailure(DataError.NotFound)), stranger)
    }

    @Test
    fun `unblock deletes the block without a body`() = runTest {
        var captured: HttpRequestData? = null
        val result = gateway { request ->
            captured = request
            respond("", HttpStatusCode.NoContent)
        }.unblockPerson(USER_ID)

        assertEquals(SaqzResult.Success(Unit), result)
        val request = checkNotNull(captured)
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/me/blocks/$USER_ID", request.url.encodedPath)
        assertEquals(0, request.body.contentLength ?: 0)
    }

    @Test
    fun `idempotent block retries a server failure`() = runTest {
        var calls = 0
        val result = gateway {
            calls++
            if (calls == 1) respond("", HttpStatusCode.ServiceUnavailable) else respond("", HttpStatusCode.NoContent)
        }.blockPerson(USER_ID, GroupId(GROUP_ID))
        assertEquals(SaqzResult.Success(Unit), result)
        assertEquals(2, calls)
    }

    private fun report() = ContentReport(GroupId(GROUP_ID), ReportTargetType.USER, USER_ID, ReportReason.OFFENSIVE)

    private fun gateway(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorModerationGateway {
        val network = NetworkClient(MockEngine { response(it) }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        val authenticated = AuthenticatedNetworkClient(
            network,
            object : IdTokenProvider {
                override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
                    completion(TokenResult.Available("fake-token"))
            },
            object : SessionInvalidator {
                override fun invalidate() = Unit
            },
        )
        return KtorModerationGateway(authenticated, retryDelay = {})
    }

    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private companion object {
        const val GROUP_ID = "group-1"
        const val USER_ID = "user-2"
    }
}
