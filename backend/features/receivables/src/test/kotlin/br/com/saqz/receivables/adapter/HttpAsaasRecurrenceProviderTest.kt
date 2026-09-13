package br.com.saqz.receivables.adapter

import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasPayments
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasRecurrenceProvider
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FeeQuote
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class HttpAsaasRecurrenceProviderTest {
    private val server = MockWebServer().also { it.start() }
    private val mapper = jacksonObjectMapper()
    private val credentials = Credentials()
    private val provider = HttpAsaasRecurrenceProvider(server.url("/v3").toUri(), "wallet_platform",
        URI("https://saqz.example"), credentials)

    @AfterEach fun close() = server.shutdown()

    @Test fun `pix subscription sends exact monthly snapshot and fixed split`() {
        val context = recurrence(PaymentMethod.PIX)
        server.enqueue(json("""{"id":"sub_1","externalReference":"${context.recurrence.id}","billingType":"PIX","value":104.90}"""))
        val result = provider.create(context)
        val request = server.takeRequest(); val body = mapper.readTree(request.body.readUtf8())
        assertEquals("POST", request.method)
        assertEquals("/v3/subscriptions", request.path)
        assertEquals("MONTHLY", body.path("cycle").asText())
        assertEquals("PIX", body.path("billingType").asText())
        assertEquals("104.9", body.path("value").decimalValue().stripTrailingZeros().toPlainString())
        assertEquals(context.recurrence.id.toString(), body.path("externalReference").asText())
        assertEquals("3", body.path("split").single().path("fixedValue").decimalValue().stripTrailingZeros().toPlainString())
        assertEquals("sub_1", result.subscriptionId)
        assertEquals("ACTIVE", result.status)
    }

    @Test fun `card recurrence creates hosted checkout without card secrets`() {
        val context = recurrence(PaymentMethod.CARD)
        server.enqueue(json("""{"id":"checkout_1","link":"https://sandbox.asaas.com/checkoutSession/show/checkout_1"}"""))
        val result = provider.create(context)
        val bodyText = server.takeRequest().body.readUtf8(); val body = mapper.readTree(bodyText)
        assertEquals(listOf("CREDIT_CARD"), body.path("billingTypes").map { it.asText() })
        assertEquals(listOf("RECURRENT"), body.path("chargeTypes").map { it.asText() })
        assertEquals("MONTHLY", body.path("subscription").path("cycle").asText())
        assertFalse(bodyText.contains("creditCard"))
        assertFalse(bodyText.contains("number"))
        assertFalse(bodyText.contains("ccv"))
        assertEquals("AUTHORIZING", result.status)
        assertTrue(result.checkoutUrl!!.startsWith("https://sandbox.asaas.com/"))
    }

    @Test fun `cutoff inactivates first then deletes future and verifies none remains`() {
        val context = recurrence(PaymentMethod.PIX, subscription = "sub_1")
        server.enqueue(json("""{"id":"sub_1","status":"INACTIVE"}"""))
        server.enqueue(json("""{"data":[
          {"id":"pay_overdue","subscription":"sub_1","dueDate":"2026-09-12","status":"OVERDUE","billingType":"PIX","value":104.90},
          {"id":"pay_future","subscription":"sub_1","dueDate":"2026-10-10","status":"PENDING","billingType":"PIX","value":104.90}],"hasMore":false}"""))
        server.enqueue(json("""{"id":"pay_future","deleted":true}"""))
        server.enqueue(json("""{"data":[
          {"id":"pay_overdue","subscription":"sub_1","dueDate":"2026-09-12","status":"OVERDUE","billingType":"PIX","value":104.90}],"hasMore":false}"""))
        assertTrue(provider.stop(context, LocalDate.of(2026, 9, 13)))
        val suspend = server.takeRequest()
        assertEquals("PUT", suspend.method)
        assertEquals("INACTIVE", mapper.readTree(suspend.body.readUtf8()).path("status").asText())
        assertEquals("/v3/subscriptions/sub_1/payments?limit=100&offset=0", server.takeRequest().path)
        val deletion = server.takeRequest()
        assertEquals("DELETE", deletion.method)
        assertEquals("/v3/payments/pay_future", deletion.path)
        assertEquals("/v3/subscriptions/sub_1/payments?limit=100&offset=0", server.takeRequest().path)
        assertEquals(0, server.requestCount - 4)
    }

    @Test fun `cutoff stays pending when final provider list still has future open`() {
        val context = recurrence(PaymentMethod.PIX, subscription = "sub_1")
        val future = """{"data":[{"id":"pay_future","subscription":"sub_1","dueDate":"2026-10-10",
          "status":"PENDING","billingType":"PIX","value":104.90}],"hasMore":false}"""
        server.enqueue(json("""{"id":"sub_1","status":"INACTIVE"}"""))
        server.enqueue(json(future))
        server.enqueue(json("""{"id":"pay_future","deleted":true}"""))
        server.enqueue(json(future))

        assertFalse(provider.stop(context, LocalDate.of(2026, 9, 13)))
        assertEquals(4, server.requestCount)
    }

    @Test fun `expired pix renewal updates same payment with exact snapshot and no post`() {
        val quote = quote(PaymentMethod.PIX)
        val instrument = PaymentInstrument(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), quote,
            "EXPIRED", paymentId = "pay_1", expiresAt = Instant.parse("2026-09-12T00:00:00Z"))
        val context = ProviderPaymentContext(instrument, UUID.randomUUID(), LocalDate.of(2026, 9, 10))
        server.enqueue(json("""{"id":"pay_1","externalReference":"${instrument.id}","billingType":"PIX","value":104.90,
          "dueDate":"2026-09-20","status":"PENDING","split":[{"id":"split_1","walletId":"wallet_platform","fixedValue":3.00,"status":"PENDING"}] }"""))
        server.enqueue(json("""{"payload":"000201renewed","encodedImage":"qr-renewed","expirationDate":"2026-09-20 23:59:59"}"""))
        val paymentProvider = HttpAsaasPayments(server.url("/v3").toUri(), "wallet_platform", credentials)
        val result = paymentProvider.renewPix(context, LocalDate.of(2026, 9, 20))
        val request = server.takeRequest(); val body = mapper.readTree(request.body.readUtf8())
        assertEquals("PUT", request.method)
        assertEquals("/v3/payments/pay_1", request.path)
        assertEquals("PIX", body.path("billingType").asText())
        assertEquals("104.9", body.path("value").decimalValue().stripTrailingZeros().toPlainString())
        assertEquals(instrument.id.toString(), body.path("externalReference").asText())
        assertEquals("/v3/payments/pay_1/pixQrCode", server.takeRequest().path)
        assertEquals(LocalDate.of(2026, 9, 20), result!!.dueDate)
        assertEquals("pay_1", result.paymentId)
        assertEquals("000201renewed", result.pixPayload)
    }

    @Test fun `refund exposes only exact observed fee linked to same payment`() {
        val quote = quote(PaymentMethod.PIX)
        val instrument = PaymentInstrument(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), quote,
            "AVAILABLE", paymentId = "pay_1")
        val context = ProviderPaymentContext(instrument, UUID.randomUUID(), LocalDate.of(2026, 9, 10))
        server.enqueue(json("""{"id":"pay_1","externalReference":"${instrument.id}","billingType":"PIX","value":104.90,
          "dueDate":"2026-09-10","status":"REFUNDED","split":[],"refunds":[]}"""))
        server.enqueue(json("""{"data":[
          {"id":"txn_fee_1","paymentId":"pay_1","type":"REFUND_REQUEST_FEE","value":-1.75},
          {"id":"txn_principal","paymentId":"pay_1","type":"PAYMENT_REVERSAL","value":-104.90},
          {"id":"txn_other","paymentId":"pay_other","type":"REFUND_REQUEST_FEE","value":-9.99},
          {"id":"txn_credit","paymentId":"pay_1","type":"REFUND_REQUEST_FEE","value":1.75}],"hasMore":false}"""))

        val result = HttpAsaasPayments(server.url("/v3").toUri(), "wallet_platform", credentials).recover(context)!!
        assertEquals("REFUNDED", result.status)
        assertEquals(listOf(ProviderResidualCost("txn_fee_1", 175)), result.residualCosts)
        assertEquals("/v3/financialTransactions?limit=100&offset=0&order=desc", server.takeRequest().let {
            assertEquals("/v3/payments/pay_1", it.path); server.takeRequest().path })
    }

    @Test fun `chargeback becomes terminal only with exact linked statement debit`() {
        val quote = quote(PaymentMethod.CARD)
        val instrument = PaymentInstrument(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), quote,
            "AVAILABLE", paymentId = "pay_1")
        val context = ProviderPaymentContext(instrument, UUID.randomUUID(), LocalDate.of(2026, 9, 10))
        server.enqueue(json("""{"id":"pay_1","externalReference":"${instrument.id}","billingType":"CREDIT_CARD","value":104.90,
          "dueDate":"2026-09-10","status":"CHARGEBACK_DISPUTE","split":[]}"""))
        server.enqueue(json("""{"data":[{"id":"txn_chargeback","paymentId":"pay_1","type":"CHARGEBACK","value":-104.90}],"hasMore":false}"""))

        val result = HttpAsaasPayments(server.url("/v3").toUri(), "wallet_platform", credentials).recover(context)!!
        assertEquals("CHARGEBACK", result.status)
        assertTrue(result.residualCosts.isEmpty())
    }

    private fun recurrence(method: PaymentMethod, subscription: String? = null): RecurrenceAuthorization {
        val quote = quote(method); val id = UUID.randomUUID()
        return RecurrenceAuthorization(PaymentRecurrence(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), method,
            quote.baseCents, quote.feesCents, quote.totalCents, LocalDate.of(2026, 10, 10), "ACTIVE", subscription, null, null),
            quote, PaymentPayer("Maria Silva", "12345678901"))
    }
    private fun quote(method: PaymentMethod) = FeeQuote(UUID.randomUUID(), "terms-1", method, 10_000, 490,
        10_490, 10_000, 300, 190)
    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
    private class Credentials : PaymentProviderCredentials {
        override fun apiKey(accountId: UUID) = "key"
        override fun customer(accountId: UUID, payerId: UUID) = PaymentCustomer(UUID.randomUUID(), "cus_1", false,
            PaymentPayer("Maria Silva", "12345678901"))
        override fun rejectCustomer(accountId: UUID, payerId: UUID) = Unit
        override fun saveCustomer(accountId: UUID, payerId: UUID, providerId: String) = Unit
    }
}
