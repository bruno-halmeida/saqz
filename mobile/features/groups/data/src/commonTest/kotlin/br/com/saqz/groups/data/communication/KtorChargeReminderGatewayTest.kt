package br.com.saqz.groups.data.communication

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
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
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorChargeReminderGatewayTest {
    @Test fun sendsOnlySelectedIdsAndRequestIdToAuthenticatedGroupEndpoint() = runTest {
        val receipt = gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/groups/group-1/charges/notify", request.url.encodedPath)
            assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("requestId", "chargeIds"), body.keys)
            assertEquals("retry-key", body["requestId"]?.jsonPrimitive?.content)
            assertEquals(listOf("a", "b"), body.getValue("chargeIds").jsonArray.map { it.jsonPrimitive.content })
            respond("""{"notificationCount":2}""", headers = jsonHeaders)
        }.send(GroupId("group-1"), "retry-key", listOf("a", "b")).success()
        assertEquals(2, receipt.notificationCount)
    }
    @Test fun conflictAndMalformedCountsDoNotBecomeSuccess() = runTest {
        assertEquals(SaqzResult.Failure(CommunicationError(DataError.Conflict)),
            gateway { respond("", HttpStatusCode.Conflict) }.send(GroupId("g"), "r", listOf("a")))
        assertEquals(SaqzResult.Failure(CommunicationError(DataError.InvalidResponse)),
            gateway { respond("""{"notificationCount":0}""", headers = jsonHeaders) }.send(GroupId("g"), "r", listOf("a")))
    }
    private fun gateway(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorChargeReminderGateway {
        val network = NetworkClient(MockEngine { response(it) }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        return KtorChargeReminderGateway(AuthenticatedNetworkClient(network, object : IdTokenProvider {
            override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) = completion(TokenResult.Available("fake-token"))
        }, object : SessionInvalidator { override fun invalidate() = Unit }))
    }
    private fun <T> SaqzResult<T, CommunicationError>.success() = assertIs<SaqzResult.Success<T>>(this).value
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
}
