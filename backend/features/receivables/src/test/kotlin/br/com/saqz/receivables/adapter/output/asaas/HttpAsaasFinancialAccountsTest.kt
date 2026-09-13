package br.com.saqz.receivables.adapter.output.asaas

import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import kotlin.test.*

class HttpAsaasFinancialAccountsTest {
    @Test fun `read merge post preserves legal identity and sends exact cents as decimal`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(json("old@example.test", "100.00")))
            server.enqueue(MockResponse().setBody(json("new@example.test", "2500.01")))
            val adapter = HttpAsaasFinancialAccounts(server.url("/v3").toUri())
            val current = adapter.readCommercialInfo("subaccount-secret")
            val correction = RegistrationCorrection("new@example.test", null, "11999999999", null, 250001,
                "01001000", "Rua Nova", "10", null, "Centro")
            val updated = adapter.updateCommercialInfo("subaccount-secret", CommercialRegistration(current.personType,
                current.cpfCnpj, current.birthDate, current.companyType, current.companyName, current.taxRegime, correction))
            assertEquals(250001, updated.correction.incomeCents)
            val get = server.takeRequest(); assertEquals("GET", get.method); assertEquals("subaccount-secret", get.getHeader("access_token"))
            val post = server.takeRequest(); assertEquals("POST", post.method)
            val body = jacksonObjectMapper().readTree(post.body.readUtf8())
            assertEquals("12345678901", body["cpfCnpj"].asText()); assertEquals("FISICA", body["personType"].asText())
            assertEquals("1990-01-01", body["birthDate"].asText()); assertEquals("new@example.test", body["email"].asText())
            assertEquals("2500.01", body["incomeValue"].asText()); assertFalse(body.has("name")); assertFalse(body.has("ownerUserId"))
        }
    }
    @Test fun `provider rejection is distinct from unavailable response`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400)); server.enqueue(MockResponse().setResponseCode(503))
            val adapter = HttpAsaasFinancialAccounts(server.url("/v3").toUri())
            assertFailsWith<FinancialProviderRejected> { adapter.readCommercialInfo("key") }
            assertFailsWith<FinancialProviderUnavailable> { adapter.readCommercialInfo("key") }
        }
    }
    private fun json(email: String, income: String) = """{
        "personType":"FISICA","cpfCnpj":"12345678901","birthDate":"1990-01-01",
        "email":"$email","mobilePhone":"11999999999","incomeValue":$income,
        "postalCode":"01001000","address":"Rua Nova","addressNumber":"10","province":"Centro"
    }""".trimIndent()
}
