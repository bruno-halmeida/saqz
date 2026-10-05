package br.com.saqz.subscriptions.data.appstore

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.ApiProblem
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubmissionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
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

class KtorAppStoreSubscriptionGatewayTest {
    @Test
    fun `account token reads the app account token`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/subscriptions/app-store/account-token", request.url.encodedPath)
            json("""{"appAccountToken":"8f0c2c1e-0000-4000-8000-000000000001"}""")
        }.appAccountToken()

        assertEquals("8f0c2c1e-0000-4000-8000-000000000001", assertIs<SaqzResult.Success<String>>(result).value)
    }

    @Test
    fun `submit posts the signed transaction and maps the subscription`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/subscriptions/app-store/transactions", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("jws.payload.signature", body.getValue("signedTransaction").jsonPrimitive.content)
            json(APP_STORE_SUBSCRIPTION)
        }.submitTransaction("jws.payload.signature")

        val subscription = assertIs<SaqzResult.Success<*>>(result).value as br.com.saqz.subscriptions.domain.subscription.MySubscription
        assertEquals(SubscriptionProvider.AppStore, subscription.provider)
        assertEquals(true, subscription.autoRenew)
        assertTrue(subscription.entitled)
    }

    @Test
    fun `transaction owned by another account maps distinctly`() = runTest {
        val result = gateway { problemResponse(409, "APP_STORE_TRANSACTION_OWNED_BY_ANOTHER_ACCOUNT") }
            .submitTransaction("jws")

        assertEquals(AppStoreSubmissionError.OwnedByAnotherAccount, failure(result))
    }

    @Test
    fun `invalid transaction maps distinctly`() = runTest {
        val result = gateway { problemResponse(422, "APP_STORE_TRANSACTION_INVALID") }.submitTransaction("jws")

        assertEquals(AppStoreSubmissionError.Invalid, failure(result))
    }

    @Test
    fun `server failure is retried and stays a data error`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = gateway(delays) {
            calls++
            respond("""{"status":503,"code":"TEMPORARY"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders())
        }.submitTransaction("jws")

        assertEquals(4, calls)
        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
        assertEquals(AppStoreSubmissionError.Data(DataError.Server), failure(result))
    }

    @Test
    fun `unauthenticated is a data error and never invalid`() = runTest {
        val result = gateway { problemResponse(401, "UNAUTHENTICATED") }.submitTransaction("jws")

        assertEquals(AppStoreSubmissionError.Data(DataError.Unauthenticated), failure(result))
    }

    private fun failure(result: SaqzResult<*, AppStoreSubmissionError>) =
        assertIs<SaqzResult.Failure<AppStoreSubmissionError>>(result).error

    private fun gateway(
        delays: MutableList<Long>? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorAppStoreSubscriptionGateway {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return KtorAppStoreSubscriptionGateway(
            AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator()),
        ) { delay -> delays?.add(delay) }
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = jsonHeaders())

    private fun MockRequestHandleScope.problemResponse(status: Int, code: String) = respond(
        Json.encodeToString(ApiProblem.serializer(), ApiProblem(status, code, "safe-correlation")),
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("obviously-fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        const val APP_STORE_SUBSCRIPTION = """{"status":"ACTIVE","entitled":true,"plan":"ORGANIZADOR","cycle":"MONTHLY","currentPeriodEnd":"2026-11-05T00:00:00Z","usage":{"groupsUsed":0,"groupsLimit":3},"readOnly":false,"canceledAt":null,"provider":"APP_STORE","autoRenew":true}"""
    }
}
