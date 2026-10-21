package br.com.saqz.subscriptions.data.coupon

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.subscriptions.domain.coupon.CouponPlatform
import br.com.saqz.subscriptions.domain.coupon.CouponRejection
import br.com.saqz.subscriptions.domain.coupon.CouponValidation
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorCampaignCouponGatewayTest {
    @Test
    fun appliedCouponPostsPlanCycleAndPlatformAndMapsTheGooglePlayOffer() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/coupons/validate", request.url.encodedPath)
            assertEquals(
                "{\"code\":\"VERAO30\",\"planId\":\"ORGANIZADOR\",\"cycle\":\"MONTHLY\",\"platform\":\"ANDROID\"}",
                (request.body as TextContent).text,
            )
            json(
                """{"status":"APPLIED","code":"VERAO30","planId":"ORGANIZADOR","cycle":"MONTHLY","discountPercent":30,
                |"durationCycles":3,"campaign":"Verão","listPriceCents":5990,"storePriceCents":4193,
                |"validUntil":"2026-12-31T23:59:59Z","googlePlayOfferId":"cupom-verao30"}""".trimMargin(),
            )
        }.validate("VERAO30", Plan.Organizador, SubscriptionCycle.Monthly, CouponPlatform.Android)

        val coupon = assertIs<CouponValidation.Applied>(assertIs<SaqzResult.Success<CouponValidation>>(result).value).coupon
        assertEquals(Plan.Organizador, coupon.plan)
        assertEquals(SubscriptionCycle.Monthly, coupon.cycle)
        assertEquals(30, coupon.discountPercent)
        assertEquals(3, coupon.durationCycles)
        assertEquals("cupom-verao30", coupon.googlePlayOfferId)
        assertEquals(4193L, coupon.storePriceCents)
    }

    @Test
    fun appliedCouponOnTheIphoneCarriesTheRedeemUrl() = runTest {
        val result = gateway { request ->
            assertEquals(
                "{\"code\":\"VERAO30\",\"planId\":\"TITULAR\",\"cycle\":\"ANNUAL\",\"platform\":\"IOS\"}",
                (request.body as TextContent).text,
            )
            json(
                """{"status":"APPLIED","code":"VERAO30","planId":"TITULAR","cycle":"ANNUAL","discountPercent":20,"durationCycles":1,
                |"appStoreOfferCode":"VERAO30X","appStoreRedeemUrl":"https://apps.apple.com/redeem?ctx=offercodes&id=1&code=VERAO30X"}""".trimMargin(),
            )
        }.validate("VERAO30", Plan.Titular, SubscriptionCycle.Annual, CouponPlatform.Ios)

        val coupon = assertIs<CouponValidation.Applied>(assertIs<SaqzResult.Success<CouponValidation>>(result).value).coupon
        assertEquals(SubscriptionCycle.Annual, coupon.cycle)
        assertEquals("VERAO30X", coupon.appStoreOfferCode)
        assertEquals("https://apps.apple.com/redeem?ctx=offercodes&id=1&code=VERAO30X", coupon.appStoreRedeemUrl)
    }

    @Test
    fun everyRejectionStatusMapsAndUnknownOnesReadAsNotFound() = runTest {
        val statuses = mapOf(
            "NOT_FOUND" to CouponRejection.NotFound,
            "EXPIRED" to CouponRejection.Expired,
            "INVALID_PLAN" to CouponRejection.InvalidPlan,
            "INVALID_CYCLE" to CouponRejection.InvalidCycle,
            "NOT_ELIGIBLE" to CouponRejection.NotEligible,
            "EXHAUSTED" to CouponRejection.Exhausted,
            "NOT_AVAILABLE_ON_PLATFORM" to CouponRejection.NotAvailableOnPlatform,
            "PENDING_ON_PLATFORM" to CouponRejection.PendingOnPlatform,
            "SOMETHING_NEW" to CouponRejection.NotFound,
        )
        statuses.forEach { (status, rejection) ->
            val result = gateway { json("{\"status\":\"$status\"}") }
                .validate("X", Plan.Titular, SubscriptionCycle.Monthly, CouponPlatform.Android)
            assertEquals(CouponValidation.Rejected(rejection), assertIs<SaqzResult.Success<CouponValidation>>(result).value, status)
        }
    }

    @Test
    fun serverFailureRetriesAndThenSurfacesAsDataError() = runTest {
        var calls = 0
        val result = gateway {
            calls++
            respond("{\"status\":503,\"code\":\"TEMPORARY\",\"correlationId\":\"safe\"}", HttpStatusCode.ServiceUnavailable, jsonHeaders())
        }.validate("X", Plan.Titular, SubscriptionCycle.Monthly, CouponPlatform.Android)

        assertEquals(DataError.Server, assertIs<SaqzResult.Failure<DataError>>(result).error)
        assertEquals(4, calls)
    }

    private fun gateway(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorCampaignCouponGateway(auth(handler)) { }

    private fun auth(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): AuthenticatedNetworkClient {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return AuthenticatedNetworkClient(network, Tokens(), NoopInvalidator())
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = jsonHeaders())

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }
}
