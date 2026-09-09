package br.com.saqz.groups.data.communication

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.NotificationPreferences
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorCommunicationGatewayTest {
    @Test fun threadUsesAuthenticatedGroupChannelAndCursorAndMapsPayload() = runTest {
        var captured: HttpRequestData? = null
        val result = gateway { request ->
            captured = request
            respond("""{"items":[$message],"nextCursor":80}""", headers = jsonHeaders)
        }.messages(GroupId("group-1"), CommunicationChannel.NOTICE, 90).success()
        val request = checkNotNull(captured)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/groups/group-1/messages", request.url.encodedPath)
        assertEquals("NOTICE", request.url.parameters["channel"])
        assertEquals("90", request.url.parameters["before"])
        assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
        assertEquals(80L, result.nextCursor)
        assertEquals("message-1", result.items.single().id)
        assertEquals("Ana", result.items.single().authorName)
        assertEquals("Treino amanhã", result.items.single().body)
        assertEquals(GroupId("group-1"), result.items.single().groupId)
    }
    @Test fun publishCarriesOnlyStableRequestKeyAndBody() = runTest {
        var captured: HttpRequestData? = null
        val result = gateway { request ->
            captured = request
            respond(message, headers = jsonHeaders)
        }.publish(GroupId("group-1"), CommunicationChannel.CHAT, "request-1", "Treino amanhã").success()
        val request = checkNotNull(captured)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("CHAT", request.url.parameters["channel"])
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertEquals(setOf("requestId", "body"), body.keys)
        assertEquals("request-1", body["requestId"]?.jsonPrimitive?.content)
        assertEquals("Treino amanhã", body["body"]?.jsonPrimitive?.content)
        assertEquals("message-1", result.id)
    }
    @Test fun reminderUsesGameRouteAndDoesNotSendUserIdentifiers() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/group-1/games/game-1/notify-pending", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("requestId"), body.keys)
            assertEquals("retry-key", body["requestId"]?.jsonPrimitive?.content)
            respond(message, headers = jsonHeaders)
        }.remind(GroupId("group-1"), "game-1", "retry-key").success()
    }
    @Test fun inboxAndReadAreScopedToAuthenticatedUser() = runTest {
        val inbox = gateway { request ->
            assertEquals("/api/me/notifications", request.url.encodedPath)
            respond("""{"items":[{"sequence":7,"message":$message,"read":false}],"nextCursor":null}""", headers = jsonHeaders)
        }.inbox().success()
        assertEquals(7L, inbox.items.single().sequence)
        assertEquals(false, inbox.items.single().read)
        assertEquals(SaqzResult.Success(Unit), gateway { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/me/notifications/7/read", request.url.encodedPath)
            respond("", HttpStatusCode.NoContent)
        }.markRead(7))
    }
    @Test fun preferencesRoundTripAllFlagsAndErrorsDoNotBecomeSuccess() = runTest {
        val value = NotificationPreferences(false, true, false)
        val result = gateway { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/me/notification-preferences", request.url.encodedPath)
            assertEquals("""{"notices":false,"messages":true,"reminders":false}""", (request.body as TextContent).text)
            respond("""{"notices":false,"messages":true,"reminders":false}""", headers = jsonHeaders)
        }.savePreferences(value)
        assertEquals(SaqzResult.Success(value), result)
        for ((status, expected) in listOf(403 to DataError.Forbidden, 404 to DataError.NotFound, 409 to DataError.Conflict, 500 to DataError.Server)) {
            assertEquals(SaqzResult.Failure(CommunicationError(expected)), gateway { respond("", HttpStatusCode.fromValue(status)) }.publish(GroupId("group-1"), CommunicationChannel.NOTICE, "request", "body"))
        }
        assertTrue(gateway { respond("{}", headers = jsonHeaders) }.preferences() is SaqzResult.Failure)
    }
    private fun gateway(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorCommunicationGateway {
        val network = NetworkClient(MockEngine { response(it) }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        return KtorCommunicationGateway(AuthenticatedNetworkClient(network, object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("fake-token"))
        }, object : SessionInvalidator { override fun invalidate() = Unit }))
    }
    private fun <T> SaqzResult<T, CommunicationError>.success() = assertIs<SaqzResult.Success<T>>(this).value
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val message = """{"id":"message-1","sequence":89,"groupId":"group-1","authorId":"me","authorName":"Ana","channel":"NOTICE","body":"Treino amanhã","createdAt":"2026-09-09T12:00:00Z","gameId":null,"recipientCount":1}"""
}
