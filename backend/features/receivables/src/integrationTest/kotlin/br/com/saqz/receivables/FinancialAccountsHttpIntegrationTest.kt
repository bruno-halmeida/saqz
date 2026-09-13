package br.com.saqz.receivables

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.adapter.output.jdbc.JdbcReceivablesRollout
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialAccountRepository
import java.time.Clock

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.input.http.FinancialAccountsController
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasOnboarding
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialOnboardingStore
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialOperationStore
import br.com.saqz.receivables.application.OnboardFinancialAccount
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class FinancialAccountsHttpIntegrationTest {
    @Test
    fun `accounts HTTP flow requires consent isolates identity and returns pending without exposing secrets`() {
        val database = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner = this)
        database.dataSource.connection.use { it.createStatement().use { statement ->
            statement.execute("INSERT INTO receivable_terms VALUES ('v1','terms','${"a".repeat(64)}','2026-01-01','2026-01-01')")
        } }
        database.dataSource.connection.use { it.createStatement().execute("UPDATE receivable_rollout SET backend_mode='ALL_USERS'") }
        val rollout = JdbcReceivablesRollout(database.dataSource, { true }, Clock.systemUTC())
        val accounts = JdbcFinancialAccountRepository(database.dataSource)
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val store = JdbcFinancialOnboardingStore(database.dataSource, secrets)
        var actor = UUID.randomUUID()
        val owner = actor
        val mapper = jacksonObjectMapper()
        MockWebServer().use { server ->
            server.start()
            val service = OnboardFinancialAccount(store, JdbcFinancialOperationStore(database.dataSource),
                HttpAsaasOnboarding(server.url("/v3").toUri(), "platform-secret", true), rollout = rollout, accounts = accounts)
            val controller = FinancialAccountsController(FinancialActorResolver { UUID.fromString(it.subject) }, service,
                store, Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC))
            val resolver = object : HandlerMethodArgumentResolver {
                override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
                override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?,
                    request: NativeWebRequest, binder: WebDataBinderFactory?) = RequestIdentity(actor.toString())
            }
            val mvc = MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(resolver).build()
            val requestId = UUID.randomUUID()
            val payload = mutableMapOf<String, Any>("requestId" to requestId.toString(), "acceptedTerms" to false,
                "termsVersion" to "v1", "name" to "Titular", "email" to "owner@example.test",
                "cpfCnpj" to "12345678901", "mobilePhone" to "11999999999", "incomeCents" to 250000,
                "address" to "Rua Teste", "addressNumber" to "10", "province" to "Centro",
                "postalCode" to "01001000", "birthDate" to "1990-01-01")
            val missing = mvc.perform(get("/api/receivables/accounts/me")).andReturn().response
            assertEquals(404, missing.status)
            assertEquals("NOT_FOUND", mapper.readTree(missing.contentAsString)["error"].asText())
            fun create() = mvc.perform(post("/api/receivables/accounts").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(payload))).andReturn().response
            val rejected = create()
            assertEquals(400, rejected.status)
            assertEquals(requestId.toString(), mapper.readTree(rejected.contentAsString)["requestId"].asText())
            assertNull(store.findOwned(owner))
            payload["acceptedTerms"] = true
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"id":"acc_1","walletId":"wallet_1","apiKey":"subaccount-secret"}"""))
            val created = create()
            assertEquals(200, created.status)
            assertEquals("UNDER_REVIEW", mapper.readTree(created.contentAsString)["value"]["registration"].asText())
            assertFalse(created.contentAsString.contains("subaccount-secret"))
            assertFalse(created.contentAsString.contains("12345678901"))
            assertEquals(200, create().status)
            assertEquals(1, server.requestCount)
            payload["name"] = "Changed"
            assertEquals(409, create().status)
            val pending = mvc.perform(get("/api/receivables/accounts/me/documents")).andReturn().response
            assertEquals(202, pending.status)
            assertEquals("RESULT_PENDING", mapper.readTree(pending.contentAsString)["error"].asText())
            val invalidUpload = mvc.perform(multipart("/api/receivables/accounts/me/documents/doc_1")
                .file(MockMultipartFile("documentFile", "file.txt", "text/plain", "invalid".toByteArray()))
                .param("requestId", UUID.randomUUID().toString()).param("type", "IDENTIFICATION")).andReturn().response
            assertEquals(400, invalidUpload.status)
            actor = UUID.randomUUID()
            assertEquals(404, mvc.perform(get("/api/receivables/accounts/me")).andReturn().response.status)
            assertEquals(404, mvc.perform(get("/api/receivables/accounts/me/documents")).andReturn().response.status)
            assertNotNull(store.findOwned(owner))
        }
    }
}
