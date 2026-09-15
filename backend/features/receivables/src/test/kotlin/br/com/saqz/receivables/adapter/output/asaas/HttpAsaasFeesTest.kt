package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.FinancialFeesUnavailable
import br.com.saqz.receivables.application.ProviderPaymentFee
import br.com.saqz.receivables.domain.PaymentMethod
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class HttpAsaasFeesTest {
    private val server = MockWebServer().also { it.start() }
    private val now = Instant.parse("2026-09-14T15:00:00Z")
    private val account = UUID.randomUUID()
    private val adapter = HttpAsaasFees(server.url("/v3").toUri(), { id ->
        if (id == null) "platform-test-key" else if (id == account) "receiving-account-test-key" else null
    })

    @AfterEach fun stop() = server.shutdown()

    @Test fun `reads actual account fees in one authenticated GET and supports fixed pix and credit card`() {
        enqueue(""""pix":{"fixedFeeValue":1.23},"creditCard":{"operationValue":0.49,"oneInstallmentPercentage":2.99}""")
        val fees = adapter.current(PaymentMethod.entries.toSet(), now, account)
        assertEquals(ProviderPaymentFee(BigDecimal.ZERO.setScale(2), 123), fees[PaymentMethod.PIX])
        assertEquals(ProviderPaymentFee(BigDecimal("0.0299"), 49), fees[PaymentMethod.CARD])
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v3/myAccount/fees/", request.path)
        assertEquals("receiving-account-test-key", request.getHeader("access_token"))
        assertEquals(0, request.bodySize)
    }

    @Test fun `percentage pix honors minimum maximum and remaining monthly allowance`() {
        val pix = """"pix":{"fixedFeeValue":null,"percentageFee":0.99,"minimumFeeValue":0.29,"maximumFeeValue":1.99,"monthlyCreditsWithoutFee":30,"creditsReceivedOfCurrentMonth":30}"""
        enqueue(pix)
        assertEquals(ProviderPaymentFee(BigDecimal("0.0099"), 0, 29, 199), adapter.current(setOf(PaymentMethod.PIX), now, account)[PaymentMethod.PIX])
        enqueue(pix.replace("\"creditsReceivedOfCurrentMonth\":30", "\"creditsReceivedOfCurrentMonth\":29"))
        assertEquals(ProviderPaymentFee(BigDecimal.ZERO, 0), adapter.current(setOf(PaymentMethod.PIX), now, account)[PaymentMethod.PIX])
    }

    @Test fun `discounts are applied only before expiration in provider local time`() {
        val response = """"pix":{"fixedFeeValue":1.99,"fixedFeeValueWithDiscount":0.99,"discountExpiration":"2026-09-14 12:00:00"},"creditCard":{"operationValue":0.49,"oneInstallmentPercentage":2.99,"discountOneInstallmentPercentage":1.99,"discountExpiration":"2026-09-14T15:00:00Z"}"""
        enqueue(response)
        val discounted = adapter.current(PaymentMethod.entries.toSet(), now.minusSeconds(1), null)
        assertEquals(99, discounted.getValue(PaymentMethod.PIX).fixedCents)
        assertEquals(BigDecimal("0.0199"), discounted.getValue(PaymentMethod.CARD).rate)
        assertEquals("platform-test-key", server.takeRequest().getHeader("access_token"))
        enqueue(response)
        val expired = adapter.current(PaymentMethod.entries.toSet(), now, null)
        assertEquals(199, expired.getValue(PaymentMethod.PIX).fixedCents)
        assertEquals(BigDecimal("0.0299"), expired.getValue(PaymentMethod.CARD).rate)
    }

    @Test fun `provider errors malformed data and missing credentials never imply a zero tariff`() {
        for (body in listOf("{}", "not-json", """{"payment":{"pix":{}}}""", """{"payment":{"pix":{"fixedFeeValue":"1.99"}}}""",
            """{"payment":{"pix":{"fixedFeeValue":0.001}}}""", """{"payment":{"pix":{"percentageFee":100,"minimumFeeValue":0,"maximumFeeValue":1}}}""",
            """{"payment":{"pix":{"percentageFee":1,"minimumFeeValue":2,"maximumFeeValue":1}}}""")) {
            server.enqueue(MockResponse().setBody(body))
            assertFailsWith<FinancialFeesUnavailable> { adapter.current(setOf(PaymentMethod.PIX), now, account) }
        }
        server.enqueue(MockResponse().setResponseCode(503))
        assertFailsWith<FinancialFeesUnavailable> { adapter.current(setOf(PaymentMethod.PIX), now, account) }
        val before = server.requestCount
        assertFailsWith<FinancialFeesUnavailable> { adapter.current(setOf(PaymentMethod.PIX), now, UUID.randomUUID()) }
        assertEquals(before, server.requestCount)
    }

    @Test fun `decimal tariff precision never passes through floating point`() {
        enqueue(""""creditCard":{"operationValue":0.01,"oneInstallmentPercentage":1.23456789123456789}""")
        assertEquals(BigDecimal("0.0123456789123456789"), adapter.current(setOf(PaymentMethod.CARD), now, account).getValue(PaymentMethod.CARD).rate)
    }

    private fun enqueue(payment: String) = server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
        .setBody("{\"payment\":{$payment}}"))
}
