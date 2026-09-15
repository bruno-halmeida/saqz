package br.com.saqz.groups.data.communication

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.GroupWhatsAppBinding
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
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

class KtorGroupWhatsAppGatewayTest {
    @Test fun bindingReadsAuthenticatedBoundGroupAndEachStatus() = runTest {
        for (status in listOf("ACTIVE", "DISABLED", "BROKEN")) {
            var captured: HttpRequestData? = null
            val result = gateway { request ->
                captured = request
                respond(
                    """{"bound":true,"groupJid":"123@g.us","groupName":"Vôlei do CERET","status":"$status"}""",
                    headers = jsonHeaders,
                )
            }.binding(GroupId("group-1")).success()
            val request = checkNotNull(captured)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/group-1/whatsapp-binding", request.url.encodedPath)
            assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
            assertEquals(
                GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.valueOf(status)),
                result,
            )
        }
    }

    @Test fun unboundGroupHasNoIdentityAndStatusNone() = runTest {
        val result = gateway { respond("""{"bound":false}""", headers = jsonHeaders) }
            .binding(GroupId("group-1")).success()
        assertEquals(GroupWhatsAppBinding(false, null, null, GroupWhatsAppStatus.NONE), result)
    }

    @Test fun linkPutsInviteLinkAndMapsReturnedGroupName() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/groups/group-1/whatsapp-binding", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("inviteLink"), body.keys)
            assertEquals("https://chat.whatsapp.com/abc", body["inviteLink"]?.jsonPrimitive?.content)
            respond(
                """{"groupJid":"123@g.us","groupName":"Vôlei do CERET","enabled":true,"status":"ACTIVE"}""",
                headers = jsonHeaders,
            )
        }.link(GroupId("group-1"), "https://chat.whatsapp.com/abc").success()
        assertEquals(
            GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.ACTIVE),
            result,
        )
    }

    @Test fun setEnabledPatchesFlagAndMapsDisabledStatus() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/api/groups/group-1/whatsapp-binding", request.url.encodedPath)
            assertEquals("""{"enabled":false}""", (request.body as TextContent).text)
            respond(
                """{"bound":true,"groupJid":"123@g.us","groupName":"Vôlei do CERET","status":"DISABLED"}""",
                headers = jsonHeaders,
            )
        }.setEnabled(GroupId("group-1"), false).success()
        assertEquals(GroupWhatsAppStatus.DISABLED, result.status)
    }

    @Test fun httpFailuresMapToTypedCommunicationErrors() = runTest {
        for ((status, expected) in listOf(
            403 to DataError.Forbidden,
            404 to DataError.NotFound,
            409 to DataError.Conflict,
            502 to DataError.Server,
        )) {
            assertEquals(
                SaqzResult.Failure(CommunicationError(expected)),
                gateway { respond("", HttpStatusCode.fromValue(status)) }.binding(GroupId("group-1")),
            )
        }
        assertEquals(
            SaqzResult.Failure(CommunicationError(DataError.Validation(ValidationDetails(emptyList(), emptyMap())))),
            gateway { respond("", HttpStatusCode.UnprocessableEntity) }.link(GroupId("group-1"), "link"),
        )
    }

    @Test fun unprocessableProblemKeepsFieldErrors() = runTest {
        val expected = CommunicationError(
            DataError.Validation(ValidationDetails(emptyList(), mapOf("inviteLink" to listOf("invalid")))),
        )
        assertEquals(
            SaqzResult.Failure(expected),
            gateway {
                respond(
                    """{"status":422,"code":"whatsapp.invalid_invite","fieldErrors":{"inviteLink":["invalid"]}}""",
                    HttpStatusCode.UnprocessableEntity,
                    headers = jsonHeaders,
                )
            }.link(GroupId("group-1"), "link"),
        )
    }

    @Test fun malformedBoundPayloadIsInvalidResponseNotSuccess() = runTest {
        assertEquals(
            SaqzResult.Failure(CommunicationError(DataError.InvalidResponse)),
            gateway { respond("""{"bound":true,"groupName":"sem jid"}""", headers = jsonHeaders) }
                .binding(GroupId("group-1")),
        )
        assertEquals(
            SaqzResult.Failure(CommunicationError(DataError.InvalidResponse)),
            gateway {
                respond(
                    """{"bound":true,"groupJid":"123@g.us","groupName":"x","status":"NONE"}""",
                    headers = jsonHeaders,
                )
            }.binding(GroupId("group-1")),
        )
    }

    private fun gateway(response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): KtorGroupWhatsAppGateway {
        val network = NetworkClient(MockEngine { response(it) }, NetworkConfig(NetworkEnvironment.Test, "https://api.test/"))
        return KtorGroupWhatsAppGateway(
            AuthenticatedNetworkClient(
                network,
                object : IdTokenProvider {
                    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
                        completion(TokenResult.Available("fake-token"))
                },
                object : SessionInvalidator { override fun invalidate() = Unit },
            ),
        )
    }

    private fun <T> SaqzResult<T, CommunicationError>.success() = assertIs<SaqzResult.Success<T>>(this).value

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
}
