package br.com.saqz.subscriptions.data.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.*
import br.com.saqz.network.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KtorSubscriptionGatewayTest {
    @Test
    fun `plans map every field including nullable limits`() = runTest {
        val result = gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/plans", request.url.encodedPath)
            json(PLANS)
        }.plans()
        val value = success(result)

        assertEquals(2, value.size)
        assertEquals(Plan.Titular, value.first().id)
        assertEquals(1_990L, value.first().monthlyPriceCents)
        assertEquals(1, value.first().maxGroups)
        assertNull(value.last().maxGroups)
        assertNull(value.last().maxAthletes)
        assertTrue(value.last().multiAdmin)
    }

    @Test
    fun `validate coupon sends only code planId and cycle`() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/coupons/validate", request.url.encodedPath)
            val body = request.json()
            assertEquals(setOf("code", "planId", "cycle"), body.keys)
            assertEquals("BEMVINDO10", body.getValue("code").jsonPrimitive.content)
            assertEquals("TITULAR", body.getValue("planId").jsonPrimitive.content)
            assertEquals("MONTHLY", body.getValue("cycle").jsonPrimitive.content)
            json(COUPON_APPLIED)
        }.validateCoupon("BEMVINDO10", Plan.Titular, SubscriptionCycle.Monthly)
    }

    @Test
    fun `validate coupon applied maps percent and both prices`() = runTest {
        val value = success(gateway { json(COUPON_APPLIED) }.validateCoupon("BEMVINDO10", Plan.Titular))
        val applied = assertIs<CouponValidation.Applied>(value)

        assertEquals(10, applied.discountPercent)
        assertEquals(1_990L, applied.listPriceCents)
        assertEquals(1_791L, applied.finalPriceCents)
        assertEquals(SubscriptionCycle.Monthly, applied.cycle)
    }

    @Test
    fun `validate coupon not found maps to NotFound`() = runTest {
        val value = success(gateway { json(COUPON_NOT_FOUND) }.validateCoupon("X", Plan.Titular))
        assertEquals(CouponValidation.NotFound, value)
    }

    @Test
    fun `validate coupon expired maps to Expired`() = runTest {
        val value = success(gateway { json(COUPON_EXPIRED) }.validateCoupon("X", Plan.Titular))
        assertEquals(CouponValidation.Expired, value)
    }

    @Test
    fun `validate coupon applied missing discount is invalid response`() = runTest {
        val result = gateway { json(COUPON_APPLIED_MISSING_DISCOUNT) }.validateCoupon("X", Plan.Titular)
        assertInvalidResponse(result)
    }

    @Test
    fun `validate coupon applied missing code is invalid response not empty success`() = runTest {
        val result = gateway { json(COUPON_APPLIED_MISSING_CODE) }.validateCoupon("X", Plan.Titular)
        assertInvalidResponse(result)
    }

    @Test
    fun `validate coupon unknown status is invalid response`() = runTest {
        val result = gateway { json("""{"status":"WEIRD"}""") }.validateCoupon("X", Plan.Titular)
        assertInvalidResponse(result)
    }

    @Test
    fun `my subscription maps status usage and pending plan`() = runTest {
        val value = success(gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/subscriptions/me", request.url.encodedPath)
            json(MY_SUBSCRIPTION)
        }.mySubscription())

        assertEquals(SubscriptionStatus.Active, value.status)
        assertEquals(Plan.Organizador, value.plan)
        assertEquals(Plan.Ilimitado, value.pendingPlan)
        assertEquals(2, value.usage.groupsUsed)
        assertEquals(3, value.usage.groupsLimit)
        assertEquals(BillingType.Pix, value.paymentMethod)
        assertFalse(value.readOnly)
    }

    @Test
    fun `my subscription not found maps distinctly`() = runTest {
        val result = gateway { problemResponse(404, "SUBSCRIPTION_NOT_FOUND") }.mySubscription()
        assertEquals(SubscriptionError.NotFound, assertIs<SaqzResult.Failure<SubscriptionError>>(result).error)
    }

    @Test
    fun `create sends full payload including coupon code`() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/subscriptions", request.url.encodedPath)
            val body = request.json()
            assertEquals(
                setOf(
                    "requestId", "planId", "cycle", "billingType", "name", "email", "cpfCnpj", "couponCode",
                ),
                body.keys,
            )
            assertEquals("PIX", body.getValue("billingType").jsonPrimitive.content)
            assertEquals("BEMVINDO10", body.getValue("couponCode").jsonPrimitive.content)
            json(CREATED_PIX)
        }.create(createCommand())
    }

    @Test
    fun `create maps pix fields on success`() = runTest {
        val value = success(gateway { json(CREATED_PIX) }.create(createCommand()))
        assertEquals(Plan.Titular, value.planId)
        assertEquals(BillingType.Pix, value.billingType)
        assertNotNull(value.pixCopyPaste)
        assertNull(value.invoiceUrl)
    }

    @Test
    fun `create retries with request id on transient failure`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = gateway(delays = delays) {
            calls++
            if (calls == 1) unavailable() else json(CREATED_PIX)
        }.create(createCommand())

        assertIs<SaqzResult.Success<CreatedSubscription>>(result)
        assertEquals(2, calls)
    }

    @Test
    fun `create without request id does not retry`() = runTest {
        var calls = 0
        gateway { calls++; unavailable() }.create(createCommand().copy(requestId = ""))
        assertEquals(1, calls)
    }

    @Test
    fun `create conflict maps distinctly`() = runTest {
        assertSubscriptionError(SubscriptionError.Conflict, gateway { problemResponse(409, "SUBSCRIPTION_CONFLICT") }.create(createCommand()))
    }

    @Test
    fun `create coupon not found maps distinctly`() = runTest {
        assertSubscriptionError(SubscriptionError.CouponNotFound, gateway { problemResponse(404, "COUPON_NOT_FOUND") }.create(createCommand()))
    }

    @Test
    fun `create coupon expired maps distinctly`() = runTest {
        assertSubscriptionError(SubscriptionError.CouponExpired, gateway { problemResponse(410, "COUPON_EXPIRED") }.create(createCommand()))
    }

    @Test
    fun `create coupon already redeemed maps distinctly`() = runTest {
        assertSubscriptionError(
            SubscriptionError.CouponAlreadyRedeemed,
            gateway { problemResponse(409, "COUPON_ALREADY_REDEEMED") }.create(createCommand()),
        )
    }

    @Test
    fun `create invalid customer data maps exact field errors`() = runTest {
        val result = gateway {
            problemResponse(400, "VALIDATION_FAILED", mapOf("cpfCnpj" to listOf("is invalid")))
        }.create(createCommand())
        val validation = assertIs<SubscriptionError.Validation>(assertIs<SaqzResult.Failure<SubscriptionError>>(result).error)
        assertEquals(mapOf("cpfCnpj" to listOf("is invalid")), validation.error.details.fieldMessages)
    }

    @Test
    fun `change plan sends request id and target plan`() = runTest {
        gateway { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/subscriptions/me/change-plan", request.url.encodedPath)
            val body = request.json()
            assertEquals(setOf("requestId", "targetPlanId"), body.keys)
            assertEquals("ILIMITADO", body.getValue("targetPlanId").jsonPrimitive.content)
            json(CHANGE_PLAN)
        }.changePlan(ChangePlanCommand(KEY, Plan.Ilimitado))
    }

    @Test
    fun `change plan maps pending upgrade and charged cents`() = runTest {
        val value = success(gateway { json(CHANGE_PLAN) }.changePlan(ChangePlanCommand(KEY, Plan.Ilimitado)))
        assertEquals(Plan.Ilimitado, value.pendingUpgradePlanId)
        assertEquals(3_000L, value.chargedCents)
        assertNotNull(value.pixCopyPaste)
    }

    @Test
    fun `change plan downgrade blocked maps distinctly`() = runTest {
        assertSubscriptionError(
            SubscriptionError.DowngradeBlocked,
            gateway { problemResponse(409, "DOWNGRADE_BLOCKED") }.changePlan(ChangePlanCommand(KEY, Plan.Titular)),
        )
    }

    @Test
    fun `change plan without request id does not retry`() = runTest {
        var calls = 0
        gateway { calls++; unavailable() }.changePlan(ChangePlanCommand("", Plan.Titular))
        assertEquals(1, calls)
    }

    @Test
    fun `cancel sends no body and does not retry on failure`() = runTest {
        var calls = 0
        val result = gateway { request ->
            calls++
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/subscriptions/me/cancel", request.url.encodedPath)
            assertTrue(request.body !is TextContent)
            unavailable()
        }.cancel()

        assertEquals(1, calls)
        assertIs<SaqzResult.Failure<SubscriptionError>>(result)
    }

    @Test
    fun `cancel maps canceled status and dates`() = runTest {
        val value = success(gateway { json(CANCELED) }.cancel())
        assertEquals(SubscriptionStatus.Canceled, value.status)
        assertEquals("2026-08-01T00:00:00Z", value.canceledAt)
    }

    @Test
    fun `receipts maps list`() = runTest {
        val value = success(gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/subscriptions/me/receipts", request.url.encodedPath)
            json(RECEIPTS)
        }.receipts())

        assertEquals(1, value.size)
        assertEquals("pay-1", value.single().asaasPaymentId)
        assertEquals(4_990L, value.single().valueCents)
    }

    @Test
    fun `receipts empty list maps to empty`() = runTest {
        val value = success(gateway { json("""{"receipts":[]}""") }.receipts())
        assertTrue(value.isEmpty())
    }

    @Test fun `timeout maps shared error`() = assertDataError(DataError.Timeout, NetworkError.Timeout.toSubscriptionError())
    @Test fun `connectivity maps shared error`() = assertDataError(DataError.Connectivity, NetworkError.Connectivity.toSubscriptionError())
    @Test fun `payload too large maps shared error`() = assertDataError(DataError.PayloadTooLarge, NetworkError.PayloadTooLarge.toSubscriptionError())
    @Test fun `unavailable maps server error`() = assertDataError(DataError.Server, NetworkError.Unavailable.toSubscriptionError())
    @Test fun `unknown maps unknown error`() = assertDataError(DataError.Unknown, NetworkError.Unknown.toSubscriptionError())
    @Test fun `invalid response maps shared error`() = assertDataError(DataError.InvalidResponse, NetworkError.InvalidResponse.toSubscriptionError())
    @Test fun `unknown 5xx problem maps server error`() = assertDataError(DataError.Server, problem(503, "UNKNOWN").toSubscriptionError())
    @Test fun `unknown 4xx problem maps unknown error`() = assertDataError(DataError.Unknown, problem(400, "UNKNOWN").toSubscriptionError())

    @Test
    fun `read retries three times with exact schedule`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = gateway(delays = delays) {
            calls++
            if (calls < 4) unavailable() else json(PLANS)
        }.plans()

        assertIs<SaqzResult.Success<List<PlanDetails>>>(result)
        assertEquals(4, calls)
        assertEquals(listOf(500L, 1_000L, 2_000L), delays)
    }

    @Test
    fun `transport cancellation propagates`() = runTest {
        val gateway = gateway { throw CancellationException("cancel") }
        assertFailsWith<CancellationException> { gateway.plans() }
    }

    @Test
    fun `retry delay cancellation propagates`() = runTest {
        val gateway = KtorSubscriptionGateway(auth(Tokens()) { unavailable() }) {
            throw CancellationException("cancel delay")
        }
        assertFailsWith<CancellationException> { gateway.plans() }
    }

    private fun createCommand() = CreateSubscriptionCommand(
        requestId = KEY,
        planId = Plan.Titular,
        cycle = SubscriptionCycle.Monthly,
        billingType = BillingType.Pix,
        name = "Ana Silva",
        email = "ana@exemplo.com",
        cpfCnpj = "12345678900",
        couponCode = "BEMVINDO10",
    )

    private fun gateway(
        delays: MutableList<Long>? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = KtorSubscriptionGateway(auth(Tokens(), handler)) { delay -> delays?.add(delay) }

    private fun auth(
        tokens: IdTokenProvider,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthenticatedNetworkClient {
        val network = NetworkClient(
            MockEngine { request -> handler(request) },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        return AuthenticatedNetworkClient(network, tokens, NoopInvalidator())
    }

    private fun MockRequestHandleScope.json(body: String) = respond(body, headers = jsonHeaders())

    private fun MockRequestHandleScope.unavailable() = respond(
        """{"status":503,"code":"TEMPORARY","correlationId":"safe"}""",
        HttpStatusCode.ServiceUnavailable,
        jsonHeaders(),
    )

    private fun MockRequestHandleScope.problemResponse(
        status: Int,
        code: String,
        fields: Map<String, List<String>>? = null,
    ) = respond(
        Json.encodeToString(ApiProblem.serializer(), ApiProblem(status, code, "safe-correlation", fields)),
        HttpStatusCode.fromValue(status),
        jsonHeaders(),
    )

    private fun HttpRequestData.json() = Json.parseToJsonElement((body as TextContent).text).jsonObject
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun problem(status: Int, code: String, fields: Map<String, List<String>>? = null) =
        NetworkError.ApiProblemError(ApiProblem(status, code, "safe-correlation", fields))

    private fun <T> success(result: SaqzResult<T, SubscriptionError>) =
        assertIs<SaqzResult.Success<T>>(result).value

    private fun assertInvalidResponse(result: SaqzResult<*, SubscriptionError>) =
        assertDataError(DataError.InvalidResponse, assertIs<SaqzResult.Failure<SubscriptionError>>(result).error)

    private fun assertDataError(expected: DataError, actual: SubscriptionError) =
        assertEquals(expected, assertIs<SubscriptionError.Data>(actual).error)

    private fun assertSubscriptionError(expected: SubscriptionError, result: SaqzResult<*, SubscriptionError>) =
        assertEquals(expected, assertIs<SaqzResult.Failure<SubscriptionError>>(result).error)

    private class Tokens : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("obviously-fake-token"))
    }

    private class NoopInvalidator : SessionInvalidator {
        override fun invalidate() = Unit
    }

    private companion object {
        const val KEY = "subscription-key"
        const val PLAN_TITULAR = """{"id":"TITULAR","name":"Titular","monthlyPriceCents":1990,"annualPriceCents":19900,"maxGroups":1,"maxAthletes":20,"multiAdmin":false,"reports":false,"whatsappSla":false}"""
        const val PLAN_ORGANIZADOR = """{"id":"ORGANIZADOR","name":"Organizador","monthlyPriceCents":4990,"annualPriceCents":49900,"maxGroups":null,"maxAthletes":null,"multiAdmin":true,"reports":true,"whatsappSla":true}"""
        const val PLANS = """[$PLAN_TITULAR,$PLAN_ORGANIZADOR]"""
        const val COUPON_APPLIED = """{"status":"APPLIED","code":"BEMVINDO10","planId":"TITULAR","cycle":"MONTHLY","discountPercent":10,"listPriceCents":1990,"finalPriceCents":1791,"validUntil":"2026-08-30T00:00:00Z"}"""
        const val COUPON_APPLIED_MISSING_DISCOUNT = """{"status":"APPLIED","code":"BEMVINDO10","planId":"TITULAR","cycle":"MONTHLY"}"""
        const val COUPON_APPLIED_MISSING_CODE = """{"status":"APPLIED","planId":"TITULAR","cycle":"MONTHLY","discountPercent":10,"listPriceCents":1990,"finalPriceCents":1791}"""
        const val COUPON_NOT_FOUND = """{"status":"NOT_FOUND"}"""
        const val COUPON_EXPIRED = """{"status":"EXPIRED"}"""
        const val MY_SUBSCRIPTION = """{"status":"ACTIVE","plan":"ORGANIZADOR","cycle":"MONTHLY","pendingPlan":"ILIMITADO","pendingPlanEffectiveAt":"2026-09-01T00:00:00Z","currentPeriodEnd":"2026-08-30T00:00:00Z","paymentMethod":"PIX","usage":{"groupsUsed":2,"groupsLimit":3},"readOnly":false,"pastDueSince":null,"canceledAt":null}"""
        const val CREATED_PIX = """{"ownerUserId":"owner-1","planId":"TITULAR","cycle":"MONTHLY","status":"ACTIVE","asaasSubscriptionId":"sub-1","currentPeriodEnd":"2026-08-30T00:00:00Z","billingType":"PIX","pixCopyPaste":"00020126chavepix","invoiceUrl":null}"""
        const val CHANGE_PLAN = """{"planId":"ILIMITADO","pendingPlanId":null,"pendingPlanEffectiveAt":null,"pendingUpgradePlanId":"ILIMITADO","status":"ACTIVE","chargedCents":3000,"pixCopyPaste":"00020126chavepix","invoiceUrl":null}"""
        const val CANCELED = """{"status":"CANCELED","canceledAt":"2026-08-01T00:00:00Z","currentPeriodEnd":"2026-08-30T00:00:00Z"}"""
        const val RECEIPTS = """{"receipts":[{"asaasEventId":"evt-1","asaasPaymentId":"pay-1","valueCents":4990,"confirmedAt":"2026-07-01T00:00:00Z","processedAt":"2026-07-01T00:05:00Z"}]}"""
    }
}
