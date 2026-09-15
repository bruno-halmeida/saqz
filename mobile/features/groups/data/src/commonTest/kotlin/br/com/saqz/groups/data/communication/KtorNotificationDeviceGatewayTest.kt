package br.com.saqz.groups.data.communication

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.NotificationDevice
import br.com.saqz.groups.domain.communication.CommunicationError
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorNotificationDeviceGatewayTest {
    @Test fun registersAuthenticatedInstallationWithoutClientSelectedUser() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/me/notification-devices/install-1", request.url.encodedPath)
            assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("token", "platform"), body.keys)
            assertEquals("fcm-token", body["token"]?.jsonPrimitive?.content)
            assertEquals("ANDROID", body["platform"]?.jsonPrimitive?.content)
            respond("", HttpStatusCode.NoContent)
        }.register(NotificationDevice("install-1", "fcm-token", "ANDROID"))
        assertIs<SaqzResult.Success<Unit>>(result)
    }
    @Test fun unregisterUsesAuthenticatedDeleteAndPreservesFailure() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/me/notification-devices/install-1", request.url.encodedPath)
            assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
            respond("", HttpStatusCode.Conflict)
        }.unregister("install-1")
        assertEquals(SaqzResult.Failure(CommunicationError(DataError.Conflict)), result)
    }
    private fun gateway(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorNotificationDeviceGateway {
        val network = NetworkClient(MockEngine { response(it) }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        return KtorNotificationDeviceGateway(AuthenticatedNetworkClient(network, object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("fake-token"))
        }, object : SessionInvalidator { override fun invalidate() = Unit }))
    }
}
