package br.com.saqz.receivables

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.adapter.output.jdbc.JdbcReceivablesRollout
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialAccountRepository
import java.time.Clock

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasOnboarding
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialOnboardingStore
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialOperationStore
import br.com.saqz.receivables.domain.RegistrationStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.*

class FinancialOnboardingIntegrationTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z")
    private val owner = UUID.randomUUID()
    private val request = FinancialRequest(UUID.randomUUID(), owner)
    private fun registration(name: String = "Titular") = LegalRegistration(name, "owner@example.test", "12345678901",
        "11999999999", 250000, "Rua Teste", "10", "Centro", "01001000", LocalDate.parse("1990-01-01"))

    @Test fun `rollout revocation blocks registration and ready provisioning while preserving replay documents and recovery`() {
        val db = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner=this)
        val jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(db.dataSource)
        jdbc.sql("INSERT INTO receivable_terms VALUES ('v1','terms','${"a".repeat(64)}','2026-01-01','2026-01-01')").update()
        val rollout = JdbcReceivablesRollout(db.dataSource,{ true },Clock.systemUTC())
        val store = JdbcFinancialOnboardingStore(db.dataSource,FinancialSecrets("test",mapOf("test" to ByteArray(32) { 1 }),ByteArray(32) { 2 }))
        MockWebServer().use { server ->
            server.start()
            val service = OnboardFinancialAccount(store,JdbcFinancialOperationStore(db.dataSource),
                HttpAsaasOnboarding(server.url("/v3").toUri(),"platform-secret",true),rollout=rollout,accounts=JdbcFinancialAccountRepository(db.dataSource))
            assertEquals(FinancialError.OPERATIONS_DISABLED,assertIs<FinancialResult.Failure>(service.begin(request,true,"v1",registration(),now)).error)
            assertNull(store.findOwned(owner))
            jdbc.sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'").update()
            val account = assertIs<FinancialResult.Success<br.com.saqz.receivables.domain.FinancialAccount>>(service.begin(request,true,"v1",registration(),now)).value
            jdbc.sql("UPDATE receivable_rollout SET backend_mode='OFF'").update()
            assertEquals(account.id,assertIs<FinancialResult.Success<br.com.saqz.receivables.domain.FinancialAccount>>(service.begin(request,true,"v1",registration(),now)).value.id)
            assertFalse(service.provision(account.id,now))
            assertEquals(OperationStatus.READY,store.creationOperation(account.id).status)
            assertEquals(0,server.requestCount)
            jdbc.sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'").update()
            server.enqueue(json("""{"id":"lost","walletId":"lost-wallet"}"""))
            assertTrue(service.provision(account.id,now))
            assertEquals(OperationStatus.UNKNOWN,store.creationOperation(account.id).status)
            jdbc.sql("UPDATE receivable_rollout SET backend_mode='OFF'").update()
            assertTrue(service.provision(account.id,now.plusSeconds(60)))
            assertEquals(OperationStatus.UNKNOWN,store.creationOperation(account.id).status)
            assertEquals(1,server.requestCount)
            store.saveProviderAccount(account.id,ProviderAccount("existing","wallet","subaccount-secret"),now)
            server.enqueue(json("""{"data":[]}"""))
            server.enqueue(json("""{"general":"APPROVED"}"""))
            assertIs<FinancialResult.Success<List<FinancialDocument>>>(service.refresh(request,now.plusSeconds(90)))
            assertEquals(3,server.requestCount)
        }
    }

    @Test
    fun `voluntary consent and published terms are required before any account or provider operation`() = fixture { store, service, server ->
        assertEquals(FinancialError.INVALID_INPUT, (service.begin(request, false, "v1", registration(), now) as FinancialResult.Failure).error)
        assertEquals(FinancialError.INVALID_INPUT, (service.begin(request, true, "missing", registration(), now) as FinancialResult.Failure).error)
        assertNull(store.findOwned(owner))
        assertEquals(0, server.requestCount)
        assertEquals(FinancialError.NOT_FOUND, (service.refresh(request, now) as FinancialResult.Failure).error)
    }

    @Test
    fun `registration retry keeps account terms and provider creation unique`() = fixture { store, service, server ->
        server.enqueue(json("""{"id":"acc_1","walletId":"wallet_1","apiKey":"subaccount-secret"}"""))
        val first = service.begin(request, true, "v1", registration(), now) as FinancialResult.Success
        assertEquals(RegistrationStatus.INCOMPLETE, first.value.registration)
        assertFalse(first.value.newOperationsEnabled)
        assertTrue(service.provision(first.value.id, now))
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/v3/accounts", post.path)
        assertEquals("platform-secret", post.getHeader("access_token"))
        val body = jacksonObjectMapper().readTree(post.body.readUtf8())
        assertEquals("12345678901", body["cpfCnpj"].asText())
        assertEquals(0, java.math.BigDecimal("2500.00").compareTo(body["incomeValue"].decimalValue()))
        assertEquals("1990-01-01", body["birthDate"].asText())
        assertFalse(body.has("companyType"))
        val second = service.begin(request.copy(requestId = UUID.randomUUID()), true, "v1", registration(), now) as FinancialResult.Success
        assertEquals(first.value.id, second.value.id)
        assertEquals(RegistrationStatus.UNDER_REVIEW, second.value.registration)
        assertFalse(service.provision(first.value.id, now.plusSeconds(60)))
        assertEquals("subaccount-secret", store.credentials(first.value.id)!!.apiKey)
        assertEquals("acc_1", store.creationOperation(first.value.id).providerReference)
        assertEquals(1, server.requestCount)
        assertEquals(FinancialError.CONFLICT, (service.begin(request, true, "v1", registration("Changed"), now) as FinancialResult.Failure).error)
        assertEquals(FinancialError.CONFLICT, (service.begin(request.copy(actorUserId = UUID.randomUUID()), true,
            "v1", registration(), now) as FinancialResult.Failure).error)
    }

    @Test
    fun `lost account creation response stays uncertain without creating a second subaccount`() = fixture { store, service, server ->
        server.enqueue(json("""{"id":"acc_lost","walletId":"wallet_lost"}"""))
        val account = (service.begin(request, true, "v1", registration(), now) as FinancialResult.Success).value
        assertTrue(service.provision(account.id, now))
        assertEquals(OperationStatus.UNKNOWN, store.creationOperation(account.id).status)
        assertTrue(service.provision(account.id, now.plusSeconds(60)))
        assertEquals(OperationStatus.UNKNOWN, store.creationOperation(account.id).status)
        assertNull(store.credentials(account.id))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `documents wait for initialization and approval follows authenticated general status`() = fixture { store, service, server ->
        val account = (service.begin(request, true, "v1", registration(), now) as FinancialResult.Success).value
        store.saveProviderAccount(account.id, ProviderAccount("acc_1", "wallet_1", "subaccount-secret"), now)
        assertEquals(FinancialError.RESULT_PENDING, (service.refresh(request, now.plusSeconds(14)) as FinancialResult.Failure).error)
        assertEquals(0, server.requestCount)
        server.enqueue(json("""{"data":[{"id":"doc_1","type":"IDENTIFICATION","status":"PENDING","onboardingUrl":"https://asaas.com/onboarding/test"}]}"""))
        server.enqueue(json("""{"general":"APPROVED"}"""))
        val documents = service.refresh(request, now.plusSeconds(15)) as FinancialResult.Success
        assertEquals(listOf(FinancialDocument("doc_1", "IDENTIFICATION", "PENDING", "https://asaas.com/onboarding/test")), documents.value)
        assertEquals(RegistrationStatus.APPROVED, store.findOwned(owner)!!.registration)
        assertFalse(store.findOwned(owner)!!.newOperationsEnabled)
        assertEquals("subaccount-secret", server.takeRequest().getHeader("access_token"))
        assertEquals("/v3/myAccount/status", server.takeRequest().path)
        server.enqueue(json("""{"data":[]}"""))
        server.enqueue(json("""{"general":"NEW_UNKNOWN_STATUS"}"""))
        assertEquals(FinancialError.PROVIDER_UNAVAILABLE, (service.refresh(request, now.plusSeconds(30)) as FinancialResult.Failure).error)
    }

    @Test
    fun `onboarding link forbids API upload and API document sends actual multipart file`() = fixture { store, service, server ->
        val account = (service.begin(request, true, "v1", registration(), now) as FinancialResult.Success).value
        store.saveProviderAccount(account.id, ProviderAccount("acc_1", "wallet_1", "subaccount-secret"), now)
        server.enqueue(json("""{"data":[{"id":"doc_1","type":"IDENTIFICATION","status":"PENDING","onboardingUrl":"https://asaas.com/onboarding/test"}]}"""))
        server.enqueue(json("""{"general":"PENDING"}"""))
        val rejected = service.upload(request, "doc_1", "IDENTIFICATION", "application/pdf", "pdf-content".toByteArray(), now.plusSeconds(15))
        assertEquals(FinancialError.INVALID_INPUT, (rejected as FinancialResult.Failure).error)
        assertEquals(RegistrationStatus.CORRECTION_REQUIRED, store.findOwned(owner)!!.registration)
        server.takeRequest(); server.takeRequest()
        server.enqueue(json("""{"data":[{"id":"doc_2","type":"IDENTIFICATION","status":"PENDING"}]}"""))
        server.enqueue(json("""{"general":"AWAITING_APPROVAL"}"""))
        server.enqueue(json("""{"id":"file_1"}"""))
        assertIs<FinancialResult.Success<Unit>>(service.upload(request, "doc_2", "IDENTIFICATION", "application/pdf",
            "pdf-content".toByteArray(), now.plusSeconds(20)))
        server.takeRequest(); server.takeRequest()
        val upload = server.takeRequest()
        assertEquals("/v3/myAccount/documents/doc_2", upload.path)
        assertEquals("subaccount-secret", upload.getHeader("access_token"))
        assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val body = upload.body.readUtf8()
        assertTrue(body.contains("name=\"documentFile\""))
        assertTrue(body.contains("pdf-content"))
        assertEquals(RegistrationStatus.UNDER_REVIEW, store.findOwned(owner)!!.registration)
    }

    @Test
    fun `BaaS switch blocks new accounts without removing existing financial access`() = fixture(enabled = false) { store, service, server ->
        assertEquals(FinancialError.PROVIDER_UNAVAILABLE, (service.begin(request, true, "v1", registration(), now) as FinancialResult.Failure).error)
        assertNull(store.findOwned(owner))
        val existing = store.begin(request, "v1", registration(), now)
        assertEquals(existing.id, (service.begin(request, true, "v1", registration(), now) as FinancialResult.Success).value.id)
        assertFalse(service.provision(existing.id, now))
        assertEquals(OperationStatus.READY, store.creationOperation(existing.id).status)
        assertEquals(0, server.requestCount)
    }

    @Test fun `own recovery retains unique operation and never retries unknown account creation`() = fixture { store, service, server ->
        val recovery = FinancialRequest(UUID.randomUUID(), owner)
        assertEquals(FinancialResult.Failure(FinancialError.NOT_FOUND, recovery.requestId), service.recover(recovery, now))
        val account = assertIs<FinancialResult.Success<br.com.saqz.receivables.domain.FinancialAccount>>(
            service.begin(request, true, "v1", registration(), now)).value
        val operation = store.creationOperation(account.id)
        server.enqueue(json("""{"id":"acc_lost","walletId":"wallet_lost"}"""))
        assertEquals(FinancialResult.Success(account, recovery.requestId), service.recover(recovery, now))
        assertEquals(OperationStatus.UNKNOWN, store.creationOperation(account.id).status)
        assertEquals(FinancialResult.Success(account, recovery.requestId), service.recover(recovery, now.plusSeconds(100)))
        assertEquals(operation.id, store.creationOperation(account.id).id)
        assertEquals(1, server.requestCount); assertNull(store.credentials(account.id))
        val foreign = recovery.copy(actorUserId = UUID.randomUUID())
        assertEquals(FinancialResult.Failure(FinancialError.NOT_FOUND, recovery.requestId), service.recover(foreign, now))
        assertEquals(1, server.requestCount)
    }

    @Test fun `recovery HTTP uses session owner exact request and no-store without legal data`() = fixture { store, service, server ->
        var actor = owner
        val resolver = object : org.springframework.web.method.support.HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: org.springframework.core.MethodParameter) =
                parameter.parameterType == br.com.saqz.sharedkernel.RequestIdentity::class.java
            override fun resolveArgument(parameter: org.springframework.core.MethodParameter,
                container: org.springframework.web.method.support.ModelAndViewContainer?,
                webRequest: org.springframework.web.context.request.NativeWebRequest,
                binder: org.springframework.web.bind.support.WebDataBinderFactory?) = br.com.saqz.sharedkernel.RequestIdentity("subject", "request")
        }
        val controller = br.com.saqz.receivables.adapter.input.http.FinancialAccountsController(
            br.com.saqz.receivables.adapter.input.http.FinancialActorResolver { actor }, service, store, Clock.fixed(now, java.time.ZoneOffset.UTC))
        val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(resolver).build()
        fun post(body: String) = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/receivables/accounts/me/recover").contentType("application/json").content(body)).andReturn().response
        val id = UUID.randomUUID(); val body = """{"requestId":"$id"}"""
        assertEquals(404, post(body).status)
        val account = assertIs<FinancialResult.Success<br.com.saqz.receivables.domain.FinancialAccount>>(
            service.begin(request, true, "v1", registration(), now)).value
        server.enqueue(json("""{"id":"acc_1","walletId":"wallet_1","apiKey":"subaccount-secret"}"""))
        val response = post(body); val json = jacksonObjectMapper().readTree(response.contentAsString)
        assertEquals(200, response.status); assertEquals("no-store", response.getHeader("Cache-Control"))
        assertEquals(id.toString(), json["requestId"].asText()); assertEquals(account.id.toString(), json["value"]["id"].asText())
        assertEquals(owner.toString(), json["value"]["ownerUserId"].asText()); assertEquals("UNDER_REVIEW", json["value"]["registration"].asText())
        assertFalse(json["value"]["newOperationsEnabled"].asBoolean())
        assertEquals(setOf("id", "ownerUserId", "registration", "newOperationsEnabled"), json["value"].fieldNames().asSequence().toSet())
        assertEquals(200, post(body).status); assertEquals(1, server.requestCount)
        assertEquals(400, post("{}").status); assertEquals(400, post("""{"requestId":"bad"}""").status)
        actor = UUID.randomUUID(); assertEquals(404, post(body).status); assertEquals(1, server.requestCount)
    }

    @Test fun `custom document instructions are preserved for the holder`() = fixture { store, service, server ->
        val account = (service.begin(request, true, "v1", registration(), now) as FinancialResult.Success).value
        store.saveProviderAccount(account.id, ProviderAccount("acc", "wallet", "subaccount-secret"), now)
        server.enqueue(json("""{"data":[{"id":"doc","type":"CUSTOM","status":"PENDING","description":"Ata de eleição"}]}"""))
        server.enqueue(json("""{"general":"PENDING"}"""))
        assertEquals(FinancialResult.Success(listOf(FinancialDocument("doc", "CUSTOM", "PENDING", null, "Ata de eleição")), request.requestId),
            service.refresh(request, now.plusSeconds(15)))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun fixture(enabled: Boolean = true,
                        block: (JdbcFinancialOnboardingStore, OnboardFinancialAccount, MockWebServer) -> Unit) {
        val database = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner = this)
        database.dataSource.connection.use { connection -> connection.createStatement().use {
            it.execute("INSERT INTO receivable_terms VALUES ('v1','terms','${"a".repeat(64)}','2026-01-01','2026-01-01')")
        } }
        database.dataSource.connection.use { it.createStatement().execute("UPDATE receivable_rollout SET backend_mode='ALL_USERS'") }
        val rollout = JdbcReceivablesRollout(database.dataSource, { true }, Clock.systemUTC())
        val accounts = JdbcFinancialAccountRepository(database.dataSource)
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val store = JdbcFinancialOnboardingStore(database.dataSource, secrets)
        MockWebServer().use { server ->
            server.start()
            val provider = HttpAsaasOnboarding(server.url("/v3").toUri(), "platform-secret", enabled)
            val service = OnboardFinancialAccount(store, JdbcFinancialOperationStore(database.dataSource), provider, rollout = rollout, accounts = accounts)
            block(store, service, server)
            database.dataSource.connection.use { connection -> connection.createStatement().use { statement ->
                statement.executeQuery("SELECT legal_data_encrypted,credentials_encrypted FROM receivable_accounts").use { rs ->
                    while (rs.next()) {
                        assertFalse(rs.getString(1).contains("12345678901"))
                        assertFalse(rs.getString(2).orEmpty().contains("subaccount-secret"))
                    }
                }
            } }
        }
    }
}
