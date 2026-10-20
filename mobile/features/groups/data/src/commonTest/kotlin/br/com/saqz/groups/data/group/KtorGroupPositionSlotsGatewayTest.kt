package br.com.saqz.groups.data.group

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.GroupPositionSlots
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.PositionGameSystem
import br.com.saqz.groups.domain.group.PositionSlot
import br.com.saqz.groups.domain.group.VersionedGroupPositionSlots
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorGroupPositionSlotsGatewayTest {
    @Test fun `read maps positions and the system and the group version`() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/group-1/position-slots", request.url.encodedPath)
            respond(RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"4\"")))
        })
        val result = assertIs<SaqzResult.Success<VersionedGroupPositionSlots>>(gateway.read(GroupId("group-1"))).value
        assertEquals(CONFIG, result.value)
        assertEquals(10, result.value.total)
        assertEquals(GroupVersionToken("\"4\""), result.versionToken)
    }

    @Test fun `write sends the system and the slots and the loaded group version`() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("\"4\"", request.headers[HttpHeaders.IfMatch])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("gameSystem", "positionSlots", "total"), body.keys)
            assertEquals(10, body.getValue("total").jsonPrimitive.int)
            assertEquals(2, body.getValue("positionSlots").jsonArray.size)
            assertEquals("FIVE_ONE", body.getValue("gameSystem").jsonPrimitive.content)
            respond(RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"5\"")))
        })
        val result = assertIs<SaqzResult.Success<VersionedGroupPositionSlots>>(
            gateway.write(GroupId("group-1"), GroupVersionToken("\"4\""), CONFIG),
        ).value
        assertEquals(GroupVersionToken("\"5\""), result.versionToken)
    }

    @Test fun `turning positions off sends no total`() = runTest {
        val gateway = gateway(MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("gameSystem", "positionSlots"), body.keys)
            respond(RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ETag to listOf("\"5\"")))
        })
        gateway.write(GroupId("group-1"), GroupVersionToken("\"4\""), GroupPositionSlots(PositionGameSystem.SIX_ZERO, emptyList()))
    }

    @Test fun `empty list turns positions off and 6-0 comes back without slots`() = runTest {
        val gateway = gateway(MockEngine { request ->
            assertEquals("/api/position-slots/suggestion", request.url.encodedPath)
            assertEquals("SIX_ZERO", request.url.parameters["system"])
            assertEquals("18", request.url.parameters["total"])
            respond("""{"gameSystem":"SIX_ZERO","total":18,"positionSlots":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val suggestion = assertIs<SaqzResult.Success<GroupPositionSlots>>(gateway.suggestion(PositionGameSystem.SIX_ZERO, 18)).value
        assertEquals(false, suggestion.isEnabled)
        assertEquals(PositionGameSystem.SIX_ZERO, suggestion.gameSystem)
    }

    @Test fun `missing ETag fails the read and a stale version is a conflict`() = runTest {
        val noTag = gateway(MockEngine { respond(RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        assertIs<SaqzResult.Failure<GroupProfileError>>(noTag.read(GroupId("group-1")))
        val stale = gateway(MockEngine {
            respond("""{"status":409,"code":"VERSION_CONFLICT","correlationId":"c"}""", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        assertIs<GroupProfileError.Conflict>(assertIs<SaqzResult.Failure<GroupProfileError>>(stale.write(GroupId("group-1"), GroupVersionToken("\"1\""), CONFIG)).error)
    }

    private fun gateway(engine: MockEngine) = KtorGroupPositionSlotsGateway(AuthenticatedNetworkClient(
        NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.test/")),
        object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("token"))
        },
        object : SessionInvalidator { override fun invalidate() = Unit },
    ))

    private companion object {
        val CONFIG = GroupPositionSlots(
            PositionGameSystem.FIVE_ONE,
            listOf(PositionSlot(AthletePosition.PONTA, 4), PositionSlot(AthletePosition.CENTRAL, 6)),
        )
        const val RESPONSE = """{"gameSystem":"FIVE_ONE","positionSlots":[{"position":"PONTA","slots":4},{"position":"CENTRAL","slots":6}],"total":10,"version":4}"""
    }
}
