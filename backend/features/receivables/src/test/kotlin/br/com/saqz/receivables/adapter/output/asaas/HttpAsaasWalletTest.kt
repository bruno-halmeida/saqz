package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class HttpAsaasWalletTest {
    private val server = MockWebServer().also { it.start() }
    private val adapter = HttpAsaasWallet(URI(server.url("/v3/").toString()))
    private val mapper = jacksonObjectMapper()

    @AfterEach fun stop() = server.shutdown()

    @Test
    fun `reads separate balance and pending receivables in exact cents`() {
        server.enqueue(json("""{"balance":123.45}"""))
        server.enqueue(json("""{"quantity":2,"value":67.89,"netValue":65.00}"""))
        assertEquals(12_345L to 6_789L, adapter.balance("subaccount-key"))
        assertEquals("/v3/finance/balance", server.takeRequest().path)
        assertEquals("/v3/finance/payment/statistics?status=PENDING", server.takeRequest().path)
    }

    @Test
    fun `statement preserves signed cents and paginates within provider limit`() {
        server.enqueue(json("""{"hasMore":true,"data":[{"id":"ftn_1","type":"TRANSFER_FEE","value":-5.99,"balance":3772.81,"date":"2026-09-12","description":"Taxa"}]}"""))
        val page = adapter.statement("key", 20, 10)
        assertEquals(-599, page.items.single().amountCents)
        assertEquals(377_281, page.items.single().balanceCents)
        assertEquals(LocalDate.parse("2026-09-12"), page.items.single().occurredOn)
        assertEquals(21, page.nextOffset)
        assertEquals("/v3/financialTransactions?offset=20&limit=10&order=asc", server.takeRequest().path)
    }

    @Test
    fun `withdrawal sends exact decimal destination and stable external reference`() {
        server.enqueue(json("""{"id":"transfer-1","status":"PENDING","transferFee":1.25}"""))
        val operation = UUID.randomUUID()
        val result = adapter.withdraw("secret", operation, 12_345, details())
        val request = server.takeRequest()
        assertEquals("/v3/transfers", request.path)
        assertEquals("secret", request.getHeader("access_token"))
        val body = mapper.readTree(request.body.readUtf8())
        assertEquals("123.45", body["value"].decimalValue().toPlainString())
        assertEquals(operation.toString(), body["externalReference"].asText())
        assertEquals("001", body["bankAccount"]["bank"]["code"].asText())
        assertEquals("CONTA_CORRENTE", body["bankAccount"]["bankAccountType"].asText())
        assertEquals(125, assertIs<ProviderWithdrawalResult.Known>(result).feeCents)
    }

    @Test
    fun `timeout recovery only lists by external reference and accepts one exact match`() {
        server.enqueue(json("""{"data":[{"id":"transfer-2","status":"DONE","transferFee":0.00}],"hasMore":false}"""))
        val operation = UUID.randomUUID()
        val result = adapter.recoverWithdrawal("secret", operation)
        assertEquals(WithdrawalStatus.COMPLETED, assertIs<ProviderWithdrawalResult.Known>(result).status)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.startsWith("/v3/transfers?externalReference=$operation"))
    }

    @Test
    fun `ambiguous recovery remains unknown and never infers rejection`() {
        server.enqueue(json("""{"data":[],"hasMore":false}"""))
        assertIs<ProviderWithdrawalResult.Unknown>(adapter.recoverWithdrawal("secret", UUID.randomUUID()))
    }

    @Test
    fun `provider insufficient balance is typed without leaking response`() {
        server.enqueue(MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
            .setBody("""{"errors":[{"code":"invalid_value","description":"Saldo insuficiente"}]}"""))
        val result = adapter.withdraw("secret", UUID.randomUUID(), 1, details())
        assertEquals("INSUFFICIENT_BALANCE", assertIs<ProviderWithdrawalResult.Rejected>(result).code)
    }

    @Test
    fun `fractional subcent provider value is rejected instead of rounded`() {
        server.enqueue(json("""{"balance":1.001}"""))
        assertFailsWith<WalletProviderFailure> { adapter.balance("key") }
    }

    private fun details() = BankDestinationDetails("001", BankAccountType.CHECKING, "Maria Silva",
        "12345678901", "1234", "98765", "0")
    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
}
