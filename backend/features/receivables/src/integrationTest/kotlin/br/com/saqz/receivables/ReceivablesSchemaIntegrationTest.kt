package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReceivablesSchemaIntegrationTest {
    private val location = "filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()

    @Test
    fun `financial migration preserves manual data and validates on repeat`() {
        val database = TestPostgres.empty(owner = this)
        database.dataSource.connection.use { connection ->
            connection.execute("CREATE TABLE group_charges(id uuid PRIMARY KEY, amount_cents bigint NOT NULL)")
            connection.execute("INSERT INTO group_charges VALUES ('${UUID.randomUUID()}', 2300)")
        }
        val migration = Flyway.configure().dataSource(database.dataSource).locations(location)
            .baselineOnMigrate(true).baselineVersion("46").load()
        assertEquals(13, migration.migrate().migrationsExecuted)
        assertEquals(0, migration.migrate().migrationsExecuted)
        database.dataSource.connection.use {
            assertEquals(2300, it.number("SELECT amount_cents FROM group_charges"))
            assertEquals(32, it.number("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'receivable_%'"))
        }
    }

    @Test
    fun `owner and legal identity cannot create duplicate financial accounts`() = migrated { connection ->
        val owner = UUID.randomUUID()
        connection.account(owner = owner, digest = "identity")
        assertEquals("23505", assertFailsWith<SQLException> { connection.account(owner = owner) }.sqlState)
        assertEquals("23505", assertFailsWith<SQLException> { connection.account(digest = "identity") }.sqlState)
        assertEquals(1, connection.number("SELECT count(*) FROM receivable_accounts"))
    }

    @Test
    fun `events deduplicate within account but same event id may exist in another account`() = migrated { connection ->
        val a = connection.account()
        val b = connection.account()
        fun event(account: UUID) = connection.execute("""
            INSERT INTO receivable_provider_events VALUES
            ('${UUID.randomUUID()}', '$account', 'event-1', 'PAYMENT_CONFIRMED', 'ciphertext', now(), null, null, 0)
        """)
        event(a)
        assertEquals("23505", assertFailsWith<SQLException> { event(a) }.sqlState)
        event(b)
        assertEquals(2, connection.number("SELECT count(*) FROM receivable_provider_events"))
    }

    @Test
    fun `one monthly order survives change of financial account and group disappearance`() = migrated { connection ->
        val a = connection.account()
        val b = connection.account()
        val group = UUID.randomUUID()
        val member = UUID.randomUUID()
        fun order(account: UUID) = connection.execute("""
            INSERT INTO receivable_orders(id, account_id, group_id, member_user_id, group_charge_id,
                billing_month, due_date, status, base_cents, request_id, issued_at)
            VALUES ('${UUID.randomUUID()}', '$account', '$group', '$member', '${UUID.randomUUID()}',
                '2026-09-01', '2026-09-10', 'ISSUED', 10000, '${UUID.randomUUID()}', now())
        """)
        order(a)
        assertEquals("23505", assertFailsWith<SQLException> { order(b) }.sqlState)
        // There is deliberately no live group/user foreign key: historical identity is independent.
        assertEquals(1, connection.number("SELECT count(*) FROM receivable_orders WHERE group_id='$group'"))
    }

    @Test
    fun `ledger is append only and duplicate payment cannot increase balance twice`() = migrated { connection ->
        val account = connection.account()
        fun movement() = connection.execute("""
            INSERT INTO receivable_movements VALUES
            ('${UUID.randomUUID()}', '$account', null, 'PAYMENT', 10000, 'payment-1', now())
        """)
        movement()
        assertEquals("23505", assertFailsWith<SQLException> { movement() }.sqlState)
        assertEquals("P0001", assertFailsWith<SQLException> { connection.execute("DELETE FROM receivable_movements") }.sqlState)
        assertEquals("P0001", assertFailsWith<SQLException> { connection.execute("UPDATE receivable_movements SET amount_cents=1") }.sqlState)
        assertEquals(10000, connection.number("SELECT sum(amount_cents) FROM receivable_movements"))
    }

    @Test
    fun `bank destination from another account cannot receive a withdrawal`() = migrated { connection ->
        val a = connection.account()
        val b = connection.account()
        val destination = UUID.randomUUID()
        val operation = UUID.randomUUID()
        connection.execute("INSERT INTO receivable_bank_destinations VALUES ('$destination','$b','ciphertext','digest',now(),null,now())")
        connection.execute("""
            INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,
                request_digest,status,next_attempt_at,created_at,updated_at)
            VALUES ('$operation','$a','${UUID.randomUUID()}','${UUID.randomUUID()}','WITHDRAW',
                '${UUID.randomUUID()}','digest','READY',now(),now(),now())
        """)
        assertEquals("23503", assertFailsWith<SQLException> {
            connection.execute("""
                INSERT INTO receivable_transfers(id,account_id,destination_id,operation_id,amount_cents,fee_cents,status,created_at,updated_at)
                VALUES ('${UUID.randomUUID()}','$a','$destination','$operation',10000,0,'REQUESTED',now(),now())
            """)
        }.sqlState)
        assertEquals(0, connection.number("SELECT count(*) FROM receivable_transfers"))
    }

    private fun migrated(block: (Connection) -> Unit) {
        TestPostgres.migrated(location, owner = this).dataSource.connection.use(block)
    }

    private fun Connection.account(
        owner: UUID = UUID.randomUUID(), digest: String = UUID.randomUUID().toString(),
    ): UUID = UUID.randomUUID().also { id ->
        execute("""
            INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,
                registration,created_at,updated_at)
            VALUES ('$id','$owner','$digest','ciphertext','INCOMPLETE',now(),now())
        """)
    }

    private fun Connection.execute(sql: String) { createStatement().use { it.execute(sql) } }
    private fun Connection.number(sql: String): Long = createStatement().use { statement ->
        statement.executeQuery(sql).use { result -> result.next(); result.getLong(1) }
    }
}
