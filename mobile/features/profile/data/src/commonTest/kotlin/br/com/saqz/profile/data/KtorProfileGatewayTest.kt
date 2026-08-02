package br.com.saqz.profile.data

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.domain.UpdateField
import br.com.saqz.profile.domain.UpdateSessionProfileRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorProfileGatewayTest {
    @Test
    fun `bootstrap uses exact authenticated put route and maps the complete profile`() = runTest {
        val result = fixture { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/session", request.url.encodedPath)
            assertEquals("Bearer session-token", request.headers[HttpHeaders.Authorization])
            assertEquals(0, request.body.contentLength ?: 0)
            profileResponse()
        }.gateway.bootstrap().success()

        assertEquals("user-1", result.user.id)
        assertEquals("person@example.test", result.user.email)
        assertEquals("Person", result.user.displayName)
        assertEquals("Rafa", result.user.nickname)
        assertEquals("+5511988765432", result.user.phone)
        assertFalse(result.user.phoneRequired)
        assertEquals(PhoneVisibility.ADMINS, result.user.phoneVisibility)
        assertEquals("São Paulo, SP", result.user.city)
        assertTrue(result.user.emailVerified)
        assertEquals("/api/session/photo?v=digest", result.user.photoUrl)
        assertEquals("group-1", result.memberships.single().groupId.value)
        assertEquals("Group", result.memberships.single().groupName)
        assertEquals("ADMIN", result.memberships.single().role)
    }

    @Test
    fun `patch sends every present field and keeps explicit null`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/api/session/profile", request.url.encodedPath)
            val body = request.bodyJson()
            assertEquals(setOf("displayName", "nickname", "phone", "phoneVisibility"), body.keys)
            assertEquals("New Person", body.getValue("displayName").jsonPrimitive.content)
            assertEquals(JsonNull, body.getValue("nickname"))
            assertEquals("+5511999999999", body.getValue("phone").jsonPrimitive.content)
            assertEquals("EVERYONE", body.getValue("phoneVisibility").jsonPrimitive.content)
            assertFalse(body.containsKey("city"))
            profileResponse()
        }.gateway.updateProfile(
            UpdateSessionProfileRequest(
                displayName = UpdateField.Set("New Person"),
                nickname = UpdateField.Set(null),
                phone = UpdateField.Set("+5511999999999"),
                phoneVisibility = UpdateField.Set(PhoneVisibility.EVERYONE),
            ),
        )
    }

    @Test
    fun `stats preserve null attendance rate from the contract`() = runTest {
        val stats = fixture {
            respond("""{"games":0,"attendanceRate":null,"groups":2}""", headers = jsonHeaders())
        }.gateway.stats().success()

        assertEquals(0, stats.games)
        assertNull(stats.attendanceRate)
        assertEquals(2, stats.groups)
    }

    @Test
    fun `stats map the attendance percentage when games are eligible`() = runTest {
        val stats = fixture {
            respond("""{"games":42,"attendanceRate":89,"groups":3}""", headers = jsonHeaders())
        }.gateway.stats().success()

        assertEquals(42, stats.games)
        assertEquals(89, stats.attendanceRate)
        assertEquals(3, stats.groups)
    }

    @Test
    fun `out of range attendance rate is an invalid response`() = runTest {
        assertEquals(
            DataError.InvalidResponse,
            fixture {
                respond("""{"games":42,"attendanceRate":101,"groups":3}""", headers = jsonHeaders())
            }.gateway.stats().dataError(),
        )
    }

    @Test
    fun `athlete profile maps all membership fields`() = runTest {
        val athlete = fixture {
            respond(
                """{"userId":"user-1","displayName":"Person","phone":"+5511","memberships":[{"groupId":"group-1","groupName":"Group","role":"ADMIN","position":"PONTA","membershipType":"MENSALISTA","active":true}]}""",
                headers = jsonHeaders(),
            )
        }.gateway.athleteProfile().success()

        assertEquals("user-1", athlete.userId)
        assertEquals("Person", athlete.displayName)
        assertEquals("+5511", athlete.phone)
        assertEquals(
            listOf("group-1", "Group", "ADMIN", "PONTA", "MENSALISTA", "true"),
            athlete.memberships.single().let {
                listOf(it.groupId.value, it.groupName, it.role, it.position!!, it.membershipType, it.active.toString())
            },
        )
    }

    @Test
    fun `delete session is bodyless and can be repeated`() = runTest {
        var calls = 0
        val fixture = fixture {
            calls += 1
            assertEquals(HttpMethod.Delete, it.method)
            assertEquals("/api/session", it.url.encodedPath)
            assertEquals(0, it.body.contentLength ?: 0)
            respond("", HttpStatusCode.NoContent)
        }

        assertEquals(Unit, fixture.gateway.deleteSession().success())
        assertEquals(Unit, fixture.gateway.deleteSession().success())
        assertEquals(2, calls)
    }

    @Test
    fun `photo upload uses the shared user photo path and multipart put`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/session/photo", request.url.encodedPath)
            assertTrue(request.body.contentType?.match(ContentType.MultiPart.FormData) == true)
            respond("", HttpStatusCode.NoContent)
        }.gateway.uploadPhoto(byteArrayOf(1, 2, 3), "image/jpeg")
    }

    @Test
    fun `photo deletion uses the shared user photo path`() = runTest {
        fixture { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/session/photo", request.url.encodedPath)
            respond("", HttpStatusCode.NoContent)
        }.gateway.deletePhoto()
    }

    @Test
    fun `validation problem keeps field errors as a shared data error`() = runTest {
        val result = fixture {
            respond(
                """{"status":400,"code":"VALIDATION_FAILED","correlationId":"private","fieldErrors":{"nickname":["invalid"]}}""",
                HttpStatusCode.BadRequest,
                jsonHeaders(),
            )
        }.gateway.updateProfile(UpdateSessionProfileRequest())

        val error = assertIs<ProfileError.Validation>(result.failure())
        assertEquals(listOf("invalid"), error.details.fieldMessages["nickname"])
        assertFalse(error.toString().contains("private"))
    }

    @Test
    fun `stats retry server failure and preserve the backoff sequence`() = runTest {
        var calls = 0
        val fixture = fixture { if (++calls == 1) problem(503, "TEMPORARY") else statsResponse() }

        fixture.gateway.stats().success()

        assertEquals(2, calls)
        assertEquals(listOf(500L), fixture.delays)
    }

    @Test
    fun `transport failures map to typed data errors`() = runTest {
        assertEquals(DataError.Timeout, fixture(MockEngine { throw HttpRequestTimeoutException(it) })
            .gateway.stats().dataError())
        assertEquals(DataError.Connectivity, fixture(MockEngine { throw UnresolvedAddressException() })
            .gateway.stats().dataError())
    }

    @Test
    fun `cancellation propagates from a gateway call`() = runTest {
        assertFailsWith<CancellationException> {
            fixture(MockEngine { throw CancellationException("cancel") }).gateway.stats()
        }
    }

    private fun fixture(
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = fixture(MockEngine { response(it) })

    private fun fixture(engine: MockEngine): Fixture {
        val delays = mutableListOf<Long>()
        val network = NetworkClient(engine, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"))
        val authenticated = AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())
        return Fixture(KtorProfileGateway(authenticated, retryDelay = { delays += it }), delays)
    }

    private fun MockRequestHandleScope.profileResponse() = respond(
        """{"user":{"id":"user-1","email":"person@example.test","displayName":"Person","nickname":"Rafa","phone":"+5511988765432","phoneRequired":false,"phoneVisibility":"ADMINS","city":"São Paulo, SP","emailVerified":true,"photoUrl":"/api/session/photo?v=digest"},"memberships":[{"groupId":"group-1","groupName":"Group","role":"ADMIN"}]}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.statsResponse() = respond(
        """{"games":42,"attendanceRate":89,"groups":3}""",
        headers = jsonHeaders(),
    )

    private fun MockRequestHandleScope.problem(status: Int, code: String) = respond(
        """{"status":$status,"code":"$code","correlationId":"private"}""",
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun HttpRequestData.bodyJson() = Json.parseToJsonElement((body as TextContent).text).jsonObject

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private inline fun <reified T> SaqzResult<T, ProfileError>.success() =
        assertIs<SaqzResult.Success<T>>(this).value

    private fun SaqzResult<*, ProfileError>.failure() =
        assertIs<SaqzResult.Failure<ProfileError>>(this).error

    private fun SaqzResult<*, ProfileError>.dataError() =
        assertIs<ProfileError.DataFailure>(failure()).error

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
            completion(TokenResult.Available("session-token"))
        }
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private data class Fixture(
        val gateway: KtorProfileGateway,
        val delays: MutableList<Long>,
    )
}
