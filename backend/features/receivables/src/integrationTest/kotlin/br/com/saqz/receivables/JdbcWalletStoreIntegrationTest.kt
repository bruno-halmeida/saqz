package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcWalletStore
import br.com.saqz.receivables.application.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class JdbcWalletStoreIntegrationTest {
    private val location = "filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 7 }), ByteArray(32) { 9 })

    @Test
    fun `destination is encrypted identity-bound masked upstream and idempotent`() {
        val database = TestPostgres.migrated(location, owner = this)
        val owner = UUID.randomUUID(); val account = database.dataSource.connection.use { it.account(owner, "12345678901") }
        val store = JdbcWalletStore(database.dataSource, secrets)
        val request = FinancialRequest(UUID.randomUUID(), owner)
        val first = store.saveDestination(account, request, details(), now)
        assertEquals(first.id, store.saveDestination(account, request, details(), now).id)
        assertEquals(first.id, store.findDestinationByRequest(account, request.requestId)?.id)
        assertFailsWith<FinancialRequestConflict> {
            store.saveDestination(account, request, details().copy(account = "11111"), now)
        }
        database.dataSource.connection.use { connection ->
            val ciphertext = connection.text("SELECT details_encrypted FROM receivable_bank_destinations WHERE id='${first.id}'")
            assertFalse(ciphertext.contains("12345678901")); assertFalse(ciphertext.contains("98765"))
        }
    }

    @Test
    fun `destination and withdrawals are isolated by account`() {
        val database = TestPostgres.migrated(location, owner = this)
        val aOwner = UUID.randomUUID(); val bOwner = UUID.randomUUID()
        val a = database.dataSource.connection.use { it.account(aOwner, "12345678901") }
        val b = database.dataSource.connection.use { it.account(bOwner, "98765432100") }
        val store = JdbcWalletStore(database.dataSource, secrets)
        val destination = store.saveDestination(a, FinancialRequest(UUID.randomUUID(), aOwner), details(), now)
        assertFailsWith<WalletDestinationMismatch> {
            store.prepareWithdrawal(b, FinancialRequest(UUID.randomUUID(), bOwner), destination.id, 100, 10_000, now)
        }
        assertNull(store.findWithdrawal(b, UUID.randomUUID()))
    }

    @Test
    fun `unknown result keeps reservation and recovery cannot be claimed before backoff`() {
        val fixture = fixture()
        val request = FinancialRequest(UUID.randomUUID(), fixture.owner)
        val withdrawal = fixture.store.prepareWithdrawal(fixture.account, request,
            fixture.destination.id, 7_000, 10_000, now)
        assertEquals(withdrawal.id, fixture.store.findWithdrawalByRequest(fixture.account, request.requestId)?.id)
        val firstClaim = assertNotNull(fixture.store.claimWithdrawal(fixture.account, withdrawal.id, now))
        val pending = fixture.store.finishWithdrawal(firstClaim, ProviderWithdrawalResult.Unknown, now)
        assertEquals(WithdrawalStatus.UNKNOWN, pending.status)
        assertNull(fixture.store.claimWithdrawal(fixture.account, withdrawal.id, now.plusSeconds(59)))
        assertFailsWith<WalletInsufficientBalance> {
            fixture.store.prepareWithdrawal(fixture.account, FinancialRequest(UUID.randomUUID(), fixture.owner),
                fixture.destination.id, 3_001, 10_000, now.plusSeconds(1))
        }
        val recovery = assertNotNull(fixture.store.claimWithdrawal(fixture.account, withdrawal.id, now.plusSeconds(61)))
        assertTrue(recovery.recoveryOnly)
        val completed = fixture.store.finishWithdrawal(recovery,
            ProviderWithdrawalResult.Known("transfer-1", WithdrawalStatus.COMPLETED, 125), now.plusSeconds(61))
        assertEquals(125, completed.feeCents)
        assertEquals(WithdrawalStatus.COMPLETED, completed.status)
    }

    @Test
    fun `processing withdrawal can be reconciled to completed without becoming a new submission`() {
        val f = fixture()
        val original = f.store.prepareWithdrawal(f.account, FinancialRequest(UUID.randomUUID(), f.owner),
            f.destination.id, 12345, 20000, now)
        val submission = assertNotNull(f.store.claimWithdrawal(f.account, original.id, now))
        assertFalse(submission.recoveryOnly)
        f.store.finishWithdrawal(submission, ProviderWithdrawalResult.Known("transfer", WithdrawalStatus.PROCESSING, 173), now)
        val recovery = assertNotNull(f.store.claimWithdrawal(f.account, original.id, now.plusSeconds(61)))
        assertTrue(recovery.recoveryOnly)
        val completed = f.store.finishWithdrawal(recovery,
            ProviderWithdrawalResult.Known("transfer", WithdrawalStatus.COMPLETED, 173), now.plusSeconds(61))
        assertEquals(WithdrawalStatus.COMPLETED, completed.status)
        assertEquals(12345, completed.amountCents); assertEquals(173, completed.feeCents)
        assertNull(f.store.claimWithdrawal(f.account, original.id, now.plusSeconds(122)))
    }

    @Test
    fun `concurrent reservations serialize and cannot exceed observed balance`() {
        val fixture = fixture()
        val executor = Executors.newFixedThreadPool(2)
        val results = try {
            (1..2).map {
                executor.submit(Callable {
                    runCatching { fixture.store.prepareWithdrawal(fixture.account,
                        FinancialRequest(UUID.randomUUID(), fixture.owner), fixture.destination.id, 7_000, 10_000, now) }
                })
            }.map { it.get() }
        } finally { executor.shutdownNow() }
        assertEquals(1, results.count(Result<Withdrawal>::isSuccess))
        assertIs<WalletInsufficientBalance>(results.single(Result<Withdrawal>::isFailure).exceptionOrNull())
    }

    private fun fixture(): Fixture {
        val database = TestPostgres.migrated(location, owner = this)
        val owner = UUID.randomUUID()
        val account = database.dataSource.connection.use { it.account(owner, "12345678901") }
        val store = JdbcWalletStore(database.dataSource, secrets)
        val destination = store.saveDestination(account, FinancialRequest(UUID.randomUUID(), owner), details(), now)
        return Fixture(store, owner, account, destination)
    }
    private data class Fixture(val store: JdbcWalletStore, val owner: UUID, val account: UUID,
                               val destination: BankDestination)
    private fun details() = BankDestinationDetails("001", BankAccountType.CHECKING, "Maria Silva",
        "12345678901", "1234", "98765", "0")
    private fun Connection.account(owner: UUID, legalIdentity: String): UUID = UUID.randomUUID().also { id ->
        prepareStatement("""
            INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,
                registration,new_operations_enabled,created_at,updated_at)
            VALUES (?,?,?,'ciphertext','APPROVED',false,now(),now())
        """.trimIndent()).use {
            it.setObject(1, id); it.setObject(2, owner); it.setString(3, secrets.legalIdentityDigest(legalIdentity)); it.executeUpdate()
        }
    }
    private fun Connection.text(sql: String): String = createStatement().use { statement ->
        statement.executeQuery(sql).use { result -> result.next(); result.getString(1) }
    }
}
