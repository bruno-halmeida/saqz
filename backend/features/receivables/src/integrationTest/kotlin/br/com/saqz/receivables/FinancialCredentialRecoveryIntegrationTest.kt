package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasCredentialRecovery
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasOnboarding
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.application.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import javax.sql.DataSource
import kotlin.test.*

class FinancialCredentialRecoveryIntegrationTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val key = RecoveryCredential("candidate-secret")

    @Test fun `lost creation response is recovered in same account with only authenticated reads and exact replay`() = fixture { f ->
        f.sql("UPDATE receivable_operations SET status='READY'")
        f.server.enqueue(json("""{"id":"acc_original","walletId":"wallet_original"}"""))
        assertTrue(f.provision.provision(f.command.accountId, now))
        assertEquals(OperationStatus.UNKNOWN, f.onboarding.creationOperation(f.command.accountId).status)
        assertNull(f.onboarding.credentials(f.command.accountId))
        val creation = f.server.takeRequest()
        assertEquals("POST", creation.method); assertEquals("/v3/accounts", creation.path)
        f.enqueueProof()
        assertEquals(CredentialRecoveryResult.RECOVERED, f.service.recover(f.command, key))
        assertEquals("candidate-secret", f.onboarding.credentials(f.command.accountId)!!.apiKey)
        assertEquals(OperationStatus.SUCCEEDED, f.onboarding.creationOperation(f.command.accountId).status)
        assertEquals("acc_original", f.onboarding.creationOperation(f.command.accountId).providerReference)
        assertFalse(f.provision.provision(f.command.accountId, now.plusSeconds(100)))
        assertEquals(CredentialRecoveryResult.ALREADY_RECOVERED, f.newService().recover(f.command, key))
        assertEquals(4, f.server.requestCount)
        val parent = f.server.takeRequest()
        assertEquals("GET", parent.method); assertEquals("platform-secret", parent.getHeader("access_token"))
        assertEquals("/v3/accounts?cpfCnpj=12345678901&offset=0&limit=100", parent.path)
        val owner = f.server.takeRequest(); val wallet = f.server.takeRequest()
        assertEquals("GET", owner.method); assertEquals("/v3/myAccount/commercialInfo/", owner.path)
        assertEquals("GET", wallet.method); assertEquals("/v3/wallets/", wallet.path)
        assertEquals("candidate-secret", owner.getHeader("access_token")); assertEquals("candidate-secret", wallet.getHeader("access_token"))
        assertEquals("SUCCEEDED", f.scalar("SELECT status FROM receivable_credential_recoveries"))
        assertFalse(f.onboarding.findOwned(f.command.ownerUserId)!!.newOperationsEnabled)
        assertEquals("UNDER_REVIEW", f.scalar("SELECT registration FROM receivable_accounts"))
        assertFalse(f.scalar("SELECT credentials_encrypted FROM receivable_accounts").contains(key.value))
        assertFalse(f.scalar("SELECT credential_encrypted FROM receivable_credential_recoveries").contains(key.value))
        assertEquals("2", f.scalar("SELECT count(*) FROM receivable_credential_recovery_events"))
        assertFails { f.sql("DELETE FROM receivable_credential_recovery_events") }
        assertFails { f.sql("UPDATE receivable_credential_recovery_events SET operator_id='${UUID.randomUUID()}'") }
    }

    @Test fun `request identity survives failure and rejects changed secret actor account and provider`() = fixture { f ->
        f.server.enqueue(json("""{"hasMore":false,"totalCount":0,"data":[]}"""))
        assertEquals(CredentialRecoveryResult.IDENTITY_MISMATCH, f.service.recover(f.command, key))
        listOf(f.command.copy(operatorId=UUID.randomUUID()), f.command.copy(ownerUserId=UUID.randomUUID()),
            f.command.copy(accountId=UUID.randomUUID()), f.command.copy(providerAccountId="other"),
            f.command.copy(walletId="other"), f.command.copy(creationOperationId=UUID.randomUUID())).forEach {
            assertEquals(CredentialRecoveryResult.CONFLICT, f.service.recover(it, key))
        }
        assertEquals(CredentialRecoveryResult.CONFLICT, f.service.recover(f.command, RecoveryCredential("changed")))
        assertEquals(1, f.server.requestCount)
        f.enqueueProof()
        assertEquals(CredentialRecoveryResult.RECOVERED, f.newService().recover(f.command, key))
    }

    @Test fun `foreign local owner and original operation are refused before HTTP`() = fixture { f ->
        assertEquals(CredentialRecoveryResult.NOT_ELIGIBLE, f.service.recover(f.command.copy(ownerUserId=UUID.randomUUID()), key))
        assertEquals(CredentialRecoveryResult.NOT_ELIGIBLE, f.service.recover(f.command.copy(creationOperationId=UUID.randomUUID()), key))
        assertEquals(0, f.server.requestCount)
    }

    @Test fun `ready running rejected or succeeded creation and existing credential are never replaced`() = fixture { f ->
        for (state in listOf("READY", "RUNNING", "REJECTED", "SUCCEEDED")) {
            f.sql("UPDATE receivable_operations SET status='$state'")
            assertEquals(CredentialRecoveryResult.NOT_ELIGIBLE, f.service.recover(f.command, key))
        }
        f.sql("UPDATE receivable_operations SET status='UNKNOWN'")
        f.onboarding.saveProviderAccount(f.command.accountId, ProviderAccount("acc_original", "wallet_original", "existing-secret"), now)
        assertEquals(CredentialRecoveryResult.NOT_ELIGIBLE, f.service.recover(f.command, key))
        assertEquals("existing-secret", f.onboarding.credentials(f.command.accountId)!!.apiKey)
        assertEquals(0, f.server.requestCount)
    }

    @Test fun `known provider account wallet and operation reference must agree`() = fixture { f ->
        f.sql("UPDATE receivable_accounts SET provider_account_id='other'")
        assertEquals(CredentialRecoveryResult.CONFLICT, f.service.recover(f.command, key))
        f.sql("UPDATE receivable_accounts SET provider_account_id=NULL, provider_wallet_id='other'")
        assertEquals(CredentialRecoveryResult.CONFLICT, f.service.recover(f.command, key))
        f.sql("UPDATE receivable_accounts SET provider_wallet_id=NULL")
        f.sql("UPDATE receivable_operations SET provider_reference='other'")
        assertEquals(CredentialRecoveryResult.CONFLICT, f.service.recover(f.command, key))
        assertEquals(0, f.server.requestCount)
    }

    @Test fun `absent ambiguous truncated malformed or foreign parent list cannot select a subaccount`() = fixture { f ->
        val candidate = f.accountJson()
        listOf("""{"hasMore":false,"totalCount":0,"data":[]}""",
            """{"hasMore":false,"totalCount":2,"data":[$candidate,$candidate]}""",
            """{"hasMore":true,"totalCount":1,"data":[$candidate]}""",
            """{"data":[$candidate]}""", f.list(f.accountJson(id="other")),
            f.list(f.accountJson(wallet="other")), f.list(f.accountJson(cpf="99999999999"))).forEach { body ->
            f.server.enqueue(json(body))
            assertEquals(CredentialRecoveryResult.IDENTITY_MISMATCH, f.service.recover(f.command, key))
            assertNull(f.onboarding.credentials(f.command.accountId))
        }
        assertEquals(7, f.server.requestCount)
    }

    @Test fun `candidate key must authenticate exact legal owner and exact wallet`() = fixture { f ->
        f.server.enqueue(json(f.list(f.accountJson())))
        f.server.enqueue(json("""{"cpfCnpj":"99999999999"}"""))
        assertEquals(CredentialRecoveryResult.IDENTITY_MISMATCH, f.service.recover(f.command, key))
        f.enqueueProof(wallet="other")
        assertEquals(CredentialRecoveryResult.IDENTITY_MISMATCH, f.service.recover(f.command, key))
        assertNull(f.onboarding.credentials(f.command.accountId))
        assertEquals(OperationStatus.UNKNOWN, f.onboarding.creationOperation(f.command.accountId).status)
    }

    @Test fun `overflowing account or wallet counts cannot prove uniqueness`() {
        for (count in listOf("4294967297", "18446744073709551617")) {
            for (invalidIndex in listOf(0, 2)) fixture { f ->
                val bodies = listOf(f.list(f.accountJson()), "{\"cpfCnpj\":\"12345678901\"}",
                    f.list("{\"id\":\"wallet_original\"}"))
                bodies.forEachIndexed { index, body ->
                    f.server.enqueue(json(if (index == invalidIndex) body.replace("\"totalCount\":1", "\"totalCount\":$count") else body))
                }
                assertEquals(CredentialRecoveryResult.IDENTITY_MISMATCH, f.service.recover(f.command, key))
                assertNull(f.onboarding.credentials(f.command.accountId))
                assertEquals(OperationStatus.UNKNOWN, f.onboarding.creationOperation(f.command.accountId).status)
                assertEquals("0", f.scalar("SELECT count(*) FROM receivable_credential_recovery_events WHERE event='IMPORTED'"))
                assertEquals(invalidIndex + 1, f.server.requestCount)
            }
        }
    }

    @Test fun `accepted response at any proof step cannot attach the credential`() {
        for (pendingIndex in 0..2) fixture { f ->
            val bodies = listOf(f.list(f.accountJson()), "{\"cpfCnpj\":\"12345678901\"}",
                f.list("{\"id\":\"wallet_original\"}"))
            bodies.forEachIndexed { index, body ->
                f.server.enqueue(json(body).setResponseCode(if (index == pendingIndex) 202 else 200))
            }
            assertEquals(CredentialRecoveryResult.UNAVAILABLE, f.service.recover(f.command, key))
            assertNull(f.onboarding.credentials(f.command.accountId))
            assertEquals(OperationStatus.UNKNOWN, f.onboarding.creationOperation(f.command.accountId).status)
            assertEquals("PENDING", f.scalar("SELECT status FROM receivable_credential_recoveries"))
            assertEquals("0", f.scalar("SELECT count(*) FROM receivable_credential_recovery_events WHERE event='IMPORTED'"))
            assertEquals(pendingIndex + 1, f.server.requestCount)
        }
    }

    @Test fun `provider failure remains pending and diagnostics never contain credential or response`() = fixture { f ->
        f.server.enqueue(MockResponse().setResponseCode(503).setBody("candidate-secret 12345678901"))
        val result = f.service.recover(f.command, key)
        assertEquals(CredentialRecoveryResult.UNAVAILABLE, result)
        assertFalse(result.toString().contains(key.value)); assertFalse(key.toString().contains(key.value))
        assertEquals("PENDING", f.scalar("SELECT status FROM receivable_credential_recoveries"))
        assertNull(f.onboarding.credentials(f.command.accountId))
        f.enqueueProof()
        assertEquals(CredentialRecoveryResult.RECOVERED, f.newService().recover(f.command, key))
    }

    @Test fun `database failure rolls back credential original operation and audit together`() = fixture { f ->
        f.sql("""CREATE FUNCTION fail_recovery_completion() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''forced''; END'""")
        f.sql("""CREATE TRIGGER fail_recovery BEFORE UPDATE ON receivable_credential_recoveries FOR EACH ROW EXECUTE FUNCTION fail_recovery_completion()""")
        f.enqueueProof()
        assertEquals(CredentialRecoveryResult.UNAVAILABLE, f.service.recover(f.command, key))
        assertNull(f.onboarding.credentials(f.command.accountId))
        assertEquals(OperationStatus.UNKNOWN, f.onboarding.creationOperation(f.command.accountId).status)
        assertEquals("PENDING", f.scalar("SELECT status FROM receivable_credential_recoveries"))
        f.sql("DROP TRIGGER fail_recovery ON receivable_credential_recoveries")
        f.enqueueProof()
        assertEquals(CredentialRecoveryResult.RECOVERED, f.newService().recover(f.command, key))
    }

    @Test fun `failure writing immutable completion event also rolls back the attached key`() = fixture { f ->
        f.sql("""CREATE FUNCTION fail_recovery_event() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN IF NEW.event = ''IMPORTED'' THEN RAISE EXCEPTION ''forced''; END IF; RETURN NEW; END'""")
        f.sql("""CREATE TRIGGER fail_recovery_event BEFORE INSERT ON receivable_credential_recovery_events FOR EACH ROW EXECUTE FUNCTION fail_recovery_event()""")
        f.enqueueProof()
        assertEquals(CredentialRecoveryResult.UNAVAILABLE, f.service.recover(f.command, key))
        assertNull(f.onboarding.credentials(f.command.accountId))
        assertEquals(OperationStatus.UNKNOWN, f.onboarding.creationOperation(f.command.accountId).status)
        assertEquals("PENDING", f.scalar("SELECT status FROM receivable_credential_recoveries"))
        assertEquals("1", f.scalar("SELECT count(*) FROM receivable_credential_recovery_events"))
    }

    @Test fun `concurrent distinct requests cannot replace winning credential`() = fixture { f ->
        val barrier = CyclicBarrier(2)
        val provider = object : CredentialRecoveryProvider {
            override fun matches(command: CredentialRecoveryCommand, target: CredentialRecoveryTarget, credential: RecoveryCredential): Boolean {
                barrier.await(); return true
            }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf(Callable { f.newService(provider).recover(f.command, key) },
                Callable { f.newService(provider).recover(f.command.copy(requestId=UUID.randomUUID()), RecoveryCredential("second-key")) }))
                .map { it.get() }
            assertEquals(1, results.count { it == CredentialRecoveryResult.RECOVERED })
            assertEquals(1, results.count { it == CredentialRecoveryResult.NOT_ELIGIBLE })
            assertEquals("1", f.scalar("SELECT count(*) FROM receivable_credential_recoveries WHERE status='SUCCEEDED'"))
        } finally { pool.shutdownNow() }
    }

    @Test fun `account change during provider validation invalidates attachment`() = fixture { f ->
        val provider = object : CredentialRecoveryProvider {
            override fun matches(command: CredentialRecoveryCommand, target: CredentialRecoveryTarget, credential: RecoveryCredential): Boolean {
                f.sql("UPDATE receivable_accounts SET version=version+1"); return true
            }
        }
        assertEquals(CredentialRecoveryResult.CONFLICT, f.newService(provider).recover(f.command, key))
        assertNull(f.onboarding.credentials(f.command.accountId))
        assertEquals(OperationStatus.UNKNOWN, f.onboarding.creationOperation(f.command.accountId).status)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private inner class Fixture(val ds: DataSource, val server: MockWebServer) {
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val onboarding = JdbcFinancialOnboardingStore(ds, secrets)
        val owner = UUID.randomUUID()
        val account = onboarding.begin(FinancialRequest(UUID.randomUUID(), owner), "v1",
            LegalRegistration("Name", "owner@example.test", "12345678901", "11999999999", 100,
                "Street", "1", "Area", "12345678", LocalDate.parse("1990-01-01")), now)
        val command = CredentialRecoveryCommand(UUID.randomUUID(), account.id, owner,
            onboarding.creationOperation(account.id).id, UUID.randomUUID(), "acc_original", "wallet_original")
        val provision = OnboardFinancialAccount(onboarding, JdbcFinancialOperationStore(ds),
            HttpAsaasOnboarding(server.url("/v3").toUri(), "platform-secret", true), clock,
            JdbcReceivablesRollout(ds, { true }, clock), JdbcFinancialAccountRepository(ds))
        val service get() = newService()
        fun newService(provider: CredentialRecoveryProvider = HttpAsaasCredentialRecovery(server.url("/v3").toUri(), "platform-secret")) =
            RecoverFinancialCredential(JdbcFinancialCredentialRecovery(ds, secrets), provider, clock)
        fun accountJson(id: String="acc_original", wallet: String="wallet_original", cpf: String="123.456.789-01") =
            """{"id":"$id","walletId":"$wallet","cpfCnpj":"$cpf"}"""
        fun list(item: String) = """{"hasMore":false,"totalCount":1,"data":[$item]}"""
        fun enqueueProof(wallet: String="wallet_original") {
            server.enqueue(json(list(accountJson())))
            server.enqueue(json("""{"cpfCnpj":"12345678901"}"""))
            server.enqueue(json(list("""{"id":"$wallet"}""")))
        }
        fun sql(sql: String) { ds.connection.use { it.createStatement().use { s -> s.execute(sql) } } }
        fun scalar(sql: String): String = ds.connection.use { it.createStatement().use { s ->
            s.executeQuery(sql).use { rs -> check(rs.next()); rs.getString(1) } } }
    }
    private fun fixture(block: (Fixture) -> Unit) {
        val db = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner=this)
        db.dataSource.connection.use { it.createStatement().use { s ->
            s.execute("INSERT INTO receivable_terms VALUES ('v1','terms','${"a".repeat(64)}','2026-01-01','2026-01-01')")
            s.execute("UPDATE receivable_rollout SET backend_mode='ALL_USERS'")
        } }
        MockWebServer().use { server ->
            server.start()
            val f = Fixture(db.dataSource, server)
            f.sql("UPDATE receivable_operations SET status='UNKNOWN'")
            block(f)
        }
    }
}
