package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialOperationStore
import br.com.saqz.receivables.application.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class FinancialOperationsIntegrationTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z")

    @Test
    fun `same request returns durable result and different input conflicts`() = fixture { store, operation ->
        store.register(operation, now)
        val claim = store.claim(operation.accountId, operation.id, now, now.plusSeconds(90))!!
        assertTrue(store.finish(claim, OperationStatus.SUCCEEDED, "pay_1", now, now))
        val resumed = store.register(operation.copy(id = UUID.randomUUID()), now)
        assertEquals(operation.id, resumed.id)
        assertEquals(OperationStatus.SUCCEEDED, resumed.status)
        assertEquals("pay_1", resumed.providerReference)
        assertFailsWith<FinancialRequestConflict> { store.register(operation.copy(requestDigest = "b".repeat(64)), now) }
        assertFailsWith<FinancialRequestConflict> { store.register(operation.copy(actorUserId = UUID.randomUUID()), now) }
    }

    @Test
    fun `concurrent workers claim only one execution and other account cannot claim`() = fixture { store, operation ->
        store.register(operation, now)
        assertNull(store.claim(UUID.randomUUID(), operation.id, now, now.plusSeconds(90)))
        Executors.newFixedThreadPool(4).use { pool ->
            val results = pool.invokeAll((1..8).map {
                Callable { store.claim(operation.accountId, operation.id, now, now.plusSeconds(90)) }
            }).map { it.get() }.filterNotNull()
            assertEquals(1, results.size)
            assertFalse(results.single().recoveryOnly)
            assertEquals(operation.id, results.single().operation.id)
        }
    }

    @Test
    fun `abandoned lease switches to recovery and stale worker cannot overwrite result`() = fixture { store, operation ->
        store.register(operation, now)
        val first = store.claim(operation.accountId, operation.id, now, now.plusSeconds(90))!!
        assertNull(store.claim(operation.accountId, operation.id, now.plusSeconds(89), now.plusSeconds(180)))
        val recovery = store.claim(operation.accountId, operation.id, now.plusSeconds(91), now.plusSeconds(180))!!
        assertTrue(recovery.recoveryOnly)
        assertTrue(store.finish(recovery, OperationStatus.SUCCEEDED, "pay_recovered", now.plusSeconds(92), now))
        assertFalse(store.finish(first, OperationStatus.REJECTED, null, now.plusSeconds(93), now))
        assertEquals("pay_recovered", store.register(operation, now).providerReference)
    }

    @Test
    fun `timeout and absent recovery never create another payment`() = fixture { store, operation ->
        store.register(operation, now)
        val remotePayments = mutableListOf<String>()
        var found = false
        val provider = object : FinancialOperationProvider {
            override fun execute(operation: FinancialOperation): ProviderOperationResult {
                remotePayments += "pay_1"
                throw java.net.SocketTimeoutException("untrusted provider diagnostic")
            }
            override fun recover(operation: FinancialOperation): ProviderOperationResult =
                if (found) ProviderOperationResult.Confirmed(remotePayments.single()) else ProviderOperationResult.Unknown
        }
        val runner = RunFinancialOperation(store, provider)
        assertTrue(runner.run(operation.accountId, operation.id, now))
        assertEquals(OperationStatus.UNKNOWN, store.register(operation, now).status)
        assertFalse(runner.run(operation.accountId, operation.id, now.plusSeconds(30)))
        assertTrue(runner.run(operation.accountId, operation.id, now.plusSeconds(60)))
        assertEquals(OperationStatus.UNKNOWN, store.register(operation, now).status)
        found = true
        assertTrue(runner.run(operation.accountId, operation.id, now.plusSeconds(120)))
        assertEquals(listOf("pay_1"), remotePayments)
        assertEquals(OperationStatus.SUCCEEDED, store.register(operation, now).status)
        assertEquals("pay_1", store.register(operation, now).providerReference)
        assertFalse(runner.run(operation.accountId, operation.id, now.plusSeconds(180)))
    }

    private fun fixture(block: (JdbcFinancialOperationStore, FinancialOperation) -> Unit) {
        val location = "filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()
        val database = TestPostgres.migrated(location, owner = this)
        val account = UUID.randomUUID()
        val owner = UUID.randomUUID()
        database.dataSource.connection.use { connection ->
            connection.prepareStatement("""
                INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,
                    registration,created_at,updated_at) VALUES (?,?,'digest','ciphertext','APPROVED',now(),now())
            """.trimIndent()).use { it.setObject(1, account); it.setObject(2, owner); it.executeUpdate() }
        }
        block(JdbcFinancialOperationStore(database.dataSource), FinancialOperation(
            UUID.randomUUID(), account, UUID.randomUUID(), owner, OperationKind.CREATE_INSTRUMENT,
            UUID.randomUUID(), "a".repeat(64),
        ))
    }
}
