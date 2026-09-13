package br.com.saqz.subscriptions.data.trial

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.NetworkError
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorTrialGatewayTest {
    @Test
    fun `owner trial maps all server fields without starting a trial`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/subscriptions/trial", request.url.encodedPath)
            json(AVAILABLE)
        }.ownerTrial()

        val value = success(result)
        assertEquals(TrialStatus.Available, value.status)
        assertNull(value.startedAt)
        assertNull(value.endsAt)
        assertEquals("2026-09-12T16:00:00Z", value.serverTime)
        assertTrue(value.canCreateGroup)
        assertEquals(1, value.maxGroups)
        assertEquals(25, value.maxAthletes)
        assertTrue(value.isOwner)
        assertEquals("https://branch.example.test/?%24ios_nativelink=true", value.appUrl)
    }

    @Test
    fun `group trial uses group owner access and preserves active deadline`() = runTest {
        val result = gateway { request ->
            assertEquals("/api/groups/group-1/trial", request.url.encodedPath)
            json(ACTIVE_GROUP)
        }.groupTrial(GroupId("group-1"))

        val value = success(result)
        assertEquals(TrialStatus.Active, value.status)
        assertEquals("2026-09-12T10:00:00Z", value.startedAt)
        assertEquals("2026-09-26T10:00:00Z", value.endsAt)
        assertEquals(false, value.isOwner)
        assertEquals(false, value.canCreateGroup)
    }

    @Test
    fun `paid status is mapped as subscribed and remains writable`() = runTest {
        val value = success(gateway { json(SUBSCRIBED) }.ownerTrial())

        assertEquals(TrialStatus.Subscribed, value.status)
        assertEquals(false, value.readOnly)
        assertEquals(true, value.canCreateGroup)
    }

    @Test
    fun `expired status retains readonly response and exact deadline`() = runTest {
        val value = success(gateway { json(EXPIRED) }.groupTrial(GroupId("group-1")))

        assertEquals(TrialStatus.Expired, value.status)
        assertEquals(true, value.readOnly)
        assertEquals("2026-09-12T10:00:00Z", value.endsAt)
    }

    @Test
    fun `not found does not expose group trial data`() = runTest {
        val result = gateway { problemResponse(HttpStatusCode.NotFound, "GROUP_NOT_FOUND") }
            .groupTrial(GroupId("missing"))
        assertEquals(TrialError.NotFound, assertIs<SaqzResult.Failure<TrialError>>(result).error)
    }

    @Test
    fun `malformed payload fails closed as invalid response`() = runTest {
        val result = gateway { json("{\"status\":\"ACTIVE\"}") }.ownerTrial()
        assertEquals(
            TrialError.Data(DataError.InvalidResponse),
            assertIs<SaqzResult.Failure<TrialError>>(result).error,
        )
    }

    @Test
    fun `server reads retry with exact schedule`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = gateway(delays) {
            calls++
            if (calls < 4) unavailable() else json(ACTIVE_OWNER)
        }.ownerTrial()

        assertIs<SaqzResult.Success<TrialAccess>>(result)
        assertEquals(4, calls)
        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
    }

    private fun gateway(
        delays: MutableList<Long>? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorTrialGateway(auth(handler)) { delay -> delays?.add(delay) }

    private fun success(result: SaqzResult<TrialAccess, TrialError>): TrialAccess =
        assertIs<SaqzResult.Success<TrialAccess>>(result).value

    private fun auth(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthenticatedNetworkClient {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = jsonHeaders())

    private fun MockRequestHandleScope.unavailable() = respond(
        "{\"status\":503,\"code\":\"TEMPORARY\",\"correlationId\":\"safe\"}",
        HttpStatusCode.ServiceUnavailable,
        jsonHeaders(),
    )

    private fun MockRequestHandleScope.problemResponse(status: HttpStatusCode, code: String) = respond(
        "{\"status\":${status.value},\"code\":\"$code\",\"correlationId\":\"safe\"}",
        status,
        jsonHeaders(),
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    @Test
    fun couponApplicationPostsCodeAndMapsCustomDuration() = runTest {
        val gateway = gateway { request ->
            assertEquals(io.ktor.http.HttpMethod.Post, request.method)
            assertEquals("/subscriptions/trial/coupon", request.url.encodedPath)
            assertEquals("{\"code\":\"ARENA\"}", (request.body as io.ktor.http.content.TextContent).text)
            json(AVAILABLE.trim().dropLast(1) + ",\"offerMode\":\"COUPON_ONLY\",\"canRedeemCoupon\":true,\"trialDays\":45,\"selectedCouponCode\":\"ARENA\"}")
        }
        val result = success(gateway.applyCoupon("ARENA"))
        assertEquals(45, result.trialDays)
        assertEquals("COUPON_ONLY", result.offerMode)
        assertEquals("ARENA", result.selectedCouponCode)
        assertEquals(true, result.canRedeemCoupon)
    }

    @Test
    fun couponRejectionAndChangedOfferHaveTypedErrors() = runTest {
        val invalid = gateway { problemResponse(HttpStatusCode.BadRequest, "INVALID") }.applyCoupon("INVALID")
        assertEquals(SaqzResult.Failure(TrialError.CouponUnavailable), invalid)
        val changed = gateway { problemResponse(HttpStatusCode.Conflict, "UNAVAILABLE") }.applyCoupon("ARENA")
        assertEquals(SaqzResult.Failure(TrialError.OfferUnavailable), changed)
    }

    private companion object {
        const val AVAILABLE = """
            {"status":"AVAILABLE","startedAt":null,"endsAt":null,"serverTime":"2026-09-12T16:00:00Z","readOnly":false,"canCreateGroup":true,"maxGroups":1,"maxAthletes":25,"isOwner":true,"appUrl":"https://branch.example.test/?%24ios_nativelink=true"}
        """
        const val ACTIVE_OWNER = """
            {"status":"ACTIVE","startedAt":"2026-09-12T10:00:00Z","endsAt":"2026-09-26T10:00:00Z","serverTime":"2026-09-12T16:00:00Z","readOnly":false,"canCreateGroup":false,"maxGroups":1,"maxAthletes":25,"isOwner":true,"appUrl":null}
        """
        const val ACTIVE_GROUP = """
            {"status":"ACTIVE","startedAt":"2026-09-12T10:00:00Z","endsAt":"2026-09-26T10:00:00Z","serverTime":"2026-09-12T16:00:00Z","readOnly":false,"canCreateGroup":false,"maxGroups":1,"maxAthletes":25,"isOwner":false,"appUrl":null}
        """
        const val EXPIRED = """
            {"status":"EXPIRED","startedAt":"2026-09-12T10:00:00Z","endsAt":"2026-09-12T10:00:00Z","serverTime":"2026-09-12T16:00:00Z","readOnly":true,"canCreateGroup":false,"maxGroups":1,"maxAthletes":25,"isOwner":false,"appUrl":null}
        """
        const val SUBSCRIBED = """
            {"status":"SUBSCRIBED","startedAt":null,"endsAt":null,"serverTime":"2026-09-12T16:00:00Z","readOnly":false,"canCreateGroup":true,"maxGroups":1,"maxAthletes":25,"isOwner":true,"appUrl":null}
        """
    }
}
