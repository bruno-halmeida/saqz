package br.com.saqz.subscriptions.data.purchase

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class KtorPurchaseInformationGatewayTest {
    @Test
    fun `request posts empty body to the authenticated endpoint`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/subscriptions/me/purchase-information", request.url.encodedPath)
            assertNull(request.body.contentType)
            assertEquals(0L, request.body.contentLength)
            respond("", HttpStatusCode.NoContent)
        }.request()

        assertIs<SaqzResult.Success<Unit>>(result)
    }

    @Test
    fun `request maps delivery failure to a typed data error`() = runTest {
        val result = gateway {
            respond(
                "{\"status\":503,\"code\":\"PURCHASE_INFORMATION_UNAVAILABLE\",\"correlationId\":\"safe\"}",
                HttpStatusCode.ServiceUnavailable,
                jsonHeaders(),
            )
        }.request()

        val error = assertIs<SaqzResult.Failure<PurchaseInformationError>>(result).error
        assertEquals(PurchaseInformationError.Data(DataError.Server), error)
    }

    @Test
    fun `request maps missing authoritative e-mail to a typed error`() = runTest {
        val result = gateway {
            respond(
                "{\"status\":404,\"code\":\"EMAIL_NOT_FOUND\",\"correlationId\":\"safe\"}",
                HttpStatusCode.NotFound,
                jsonHeaders(),
            )
        }.request()

        assertEquals(
            PurchaseInformationError.EmailNotFound,
            assertIs<SaqzResult.Failure<PurchaseInformationError>>(result).error,
        )
    }

    @Test
    fun `request maps validation failure with status 422 to missing e-mail`() = runTest {
        val result = gateway {
            respond(
                "{\"status\":422,\"code\":\"VALIDATION_FAILED\",\"correlationId\":\"safe\"}",
                HttpStatusCode.UnprocessableEntity,
                jsonHeaders(),
            )
        }.request()

        assertEquals(
            PurchaseInformationError.EmailNotFound,
            assertIs<SaqzResult.Failure<PurchaseInformationError>>(result).error,
        )
    }

    @Test
    fun `request preserves in-progress retryAfterSeconds`() = runTest {
        val result = gateway {
            respond(
                "{\"status\":409,\"code\":\"SUBSCRIPTION_PURCHASE_IN_PROGRESS\",\"retryAfterSeconds\":37}",
                HttpStatusCode.Conflict,
                jsonHeaders(),
            )
        }.request()

        assertEquals(
            PurchaseInformationError.InProgress(retryAfterSeconds = 37),
            assertIs<SaqzResult.Failure<PurchaseInformationError>>(result).error,
        )
    }

    @Test
    fun `request preserves rate limited retryAfterSeconds`() = runTest {
        val result = gateway {
            respond(
                "{\"status\":429,\"code\":\"SUBSCRIPTION_PURCHASE_RATE_LIMITED\",\"retryAfterSeconds\":43}",
                HttpStatusCode.TooManyRequests,
                jsonHeaders(),
            )
        }.request()

        assertEquals(
            PurchaseInformationError.RateLimited(retryAfterSeconds = 43),
            assertIs<SaqzResult.Failure<PurchaseInformationError>>(result).error,
        )
    }

    @Test
    fun `request does not retry the side effect`() = runTest {
        var calls = 0
        val result = gateway {
            calls++
            respond("", HttpStatusCode.ServiceUnavailable)
        }.request()

        assertIs<SaqzResult.Failure<PurchaseInformationError>>(result)
        assertEquals(1, calls)
    }

    private fun gateway(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorPurchaseInformationGateway(auth(handler))

    private fun auth(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthenticatedNetworkClient {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }
}
