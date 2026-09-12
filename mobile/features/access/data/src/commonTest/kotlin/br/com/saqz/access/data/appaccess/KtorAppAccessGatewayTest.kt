package br.com.saqz.access.data.appaccess

import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.domain.appaccess.AppAccessSession
import br.com.saqz.access.domain.appaccess.OnboardingError
import br.com.saqz.access.domain.appaccess.OnboardingStatus
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
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
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorAppAccessGatewayTest {
    @Test
    fun `redeem sends one anonymous POST and maps custom token response`() = runTest {
        var calls = 0
        val value = success(appGateway { request ->
            calls++
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/session/app-link/redeem", request.url.encodedPath)
            assertFalse(request.headers.contains(HttpHeaders.Authorization))
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("opaque-code", body.getValue("code").jsonPrimitive.content)
            json(REDEEMED)
        }.redeem("opaque-code"))

        assertEquals(1, calls)
        assertEquals("custom-token", value.customToken)
        assertEquals("backend-owner", value.ownerUserId)
        assertFalse(value.onboardingCompleted)
        assertTrue(value.toString().contains("<redacted>"))
        assertFalse(value.toString().contains("custom-token"))
    }

    @Test
    fun `redeem never retries consumed code after transport failure`() = runTest {
        var calls = 0
        val result = appGateway {
            calls++
            throw IllegalStateException("network failure")
        }.redeem("opaque-code")
        assertEquals(1, calls)
        assertIs<SaqzResult.Failure<AppAccessError>>(result)
    }

    @Test
    fun `invalid code and unusable success fail closed`() = runTest {
        assertEquals(AppAccessError.CodeInvalid, assertIs<SaqzResult.Failure<AppAccessError>>(
            appGateway { error(HttpStatusCode.BadRequest, "APP_ONBOARDING_CODE_INVALID") }.redeem(""),
        ).error)
        val malformed = appGateway { json("{\"customToken\":\"\",\"ownerUserId\":\"\",\"displayName\":\"\",\"onboardingCompleted\":false}") }
            .redeem("opaque-code")
        assertEquals(AppAccessError.Data(DataError.InvalidResponse), assertIs<SaqzResult.Failure<AppAccessError>>(malformed).error)
    }

    @Test
    fun `onboarding gateway uses authenticated GET and idempotent PUT`() = runTest {
        val paths = mutableListOf<Pair<HttpMethod, String>>()
        val gateway = onboardingGateway { request ->
            paths += request.method to request.url.encodedPath
            json("{\"onboardingCompleted\":true}")
        }
        assertEquals(OnboardingStatus(true), successOnboarding(gateway.status()))
        assertEquals(OnboardingStatus(true), successOnboarding(gateway.complete()))
        assertEquals(listOf(HttpMethod.Get to "/api/session/onboarding", HttpMethod.Put to "/api/session/onboarding"), paths)
    }

    @Test
    fun `suspended onboarding account maps to typed failure`() = runTest {
        val result = onboardingGateway { error(HttpStatusCode.Forbidden, "ACCOUNT_SUSPENDED") }.status()
        assertEquals(OnboardingError.AccountSuspended, assertIs<SaqzResult.Failure<OnboardingError>>(result).error)
    }

    private fun appGateway(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorAppAccessGateway(network(handler))

    private fun onboardingGateway(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorOnboardingGateway(authenticated(handler))

    private fun network(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = NetworkClient(MockEngine { request -> handler(request) }, NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"))

    private fun authenticated(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = AuthenticatedNetworkClient(network(handler), Tokens(), NoopInvalidator())

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = jsonHeaders())

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String) = respond(
        "{\"status\":${status.value},\"code\":\"$code\",\"correlationId\":\"safe\"}",
        status,
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun success(result: SaqzResult<AppAccessSession, AppAccessError>) =
        assertIs<SaqzResult.Success<AppAccessSession>>(result).value

    private fun successOnboarding(result: SaqzResult<OnboardingStatus, OnboardingError>) =
        assertIs<SaqzResult.Success<OnboardingStatus>>(result).value

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        const val REDEEMED = """
            {"customToken":"custom-token","ownerUserId":"backend-owner","displayName":"Ana","onboardingCompleted":false}
        """
    }
}
