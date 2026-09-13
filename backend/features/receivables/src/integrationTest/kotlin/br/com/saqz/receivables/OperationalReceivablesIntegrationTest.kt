package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.jdbc.JdbcOperationalNotices
import br.com.saqz.receivables.adapter.output.jdbc.JdbcOperationalReceivables
import br.com.saqz.receivables.adapter.output.jdbc.JdbcExternalResidualCostLedger
import br.com.saqz.receivables.application.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.*

class OperationalReceivablesIntegrationTest {
    private val location = "filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()
    private val now = Instant.parse("2026-09-13T12:00:00Z")

    @Test
    fun `queue is deterministic sanitized and excludes succeeded by default`() = fixture { dataSource, account ->
        val store = JdbcOperationalReceivables(dataSource)
        operation(dataSource, account, OperationKind.CREATE_INSTRUMENT, OperationStatus.UNKNOWN, now, "PROVIDER_TIMEOUT")
        operation(dataSource, account, OperationKind.CREATE_INSTRUMENT, OperationStatus.SUCCEEDED, now.plusSeconds(1), null)

        val page = store.list(null, null, 1, 25)

        assertEquals(1, page.total)
        assertEquals("PROVIDER_TIMEOUT", page.items.single().failureCode)
        assertFalse(page.hasNext)
        assertFalse(page.items.single().toString().contains("ciphertext"))
    }

    @Test
    fun `recovery lease rejects concurrent actor and replay is provider free`() = fixture { dataSource, account ->
        val store = JdbcOperationalReceivables(dataSource)
        val operation = operation(dataSource, account, OperationKind.CREATE_INSTRUMENT, OperationStatus.UNKNOWN, now, "TIMEOUT")
        val request = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val command = OperationalRecoveryCommand(request, operation, actor, "Consultar estado remoto")
        val first = store.reserve(command, now, now.plusSeconds(90)) as OperationalRecoveryReservation.Claimed

        assertEquals(OperationalRecoveryReservation.Busy,
            store.reserve(command.copy(requestId = UUID.randomUUID()), now.plusSeconds(1), now.plusSeconds(91)))
        val outcome = store.complete(command, first.token, OperationalRecoveryObservation(OperationStatus.UNKNOWN), now.plusSeconds(2))
        assertEquals(OperationalRecoveryResult.STILL_UNKNOWN, outcome.result)
        val replay = store.reserve(command, now.plusSeconds(3), now.plusSeconds(93)) as OperationalRecoveryReservation.Replay
        assertEquals(outcome, replay.outcome)
        val detail = store.detail(operation)!!
        assertEquals(actor, detail.audit.single().actorUserId)
        assertEquals("Consultar estado remoto", detail.audit.single().reason)
        assertEquals(OperationalRecoveryResult.STILL_UNKNOWN, detail.audit.single().result)
        assertEquals(0, count(dataSource, "receivable_movements"), "operational recovery must not create cash effects")
    }

    @Test
    fun `request id cannot change operation actor or reason`() = fixture { dataSource, account ->
        val store = JdbcOperationalReceivables(dataSource)
        val operation = operation(dataSource, account, OperationKind.CANCEL_INSTRUMENT, OperationStatus.UNKNOWN, now, null)
        val command = OperationalRecoveryCommand(UUID.randomUUID(), operation, UUID.randomUUID(), "Ler cancelamento")
        val claim = store.reserve(command, now, now.plusSeconds(90)) as OperationalRecoveryReservation.Claimed
        store.complete(command, claim.token, OperationalRecoveryObservation(OperationStatus.REJECTED), now.plusSeconds(1))

        assertEquals(OperationalRecoveryReservation.Conflict,
            store.reserve(command.copy(reason = "Motivo diferente"), now.plusSeconds(2), now.plusSeconds(92)))
        assertEquals(OperationalRecoveryReservation.Conflict,
            store.reserve(command.copy(actorUserId = UUID.randomUUID()), now.plusSeconds(2), now.plusSeconds(92)))
    }

    @Test
    fun `retry after crash reads terminal database state before any provider call`() = fixture { dataSource, account ->
        val store = JdbcOperationalReceivables(dataSource)
        val operation = operation(dataSource, account, OperationKind.CREATE_INSTRUMENT, OperationStatus.UNKNOWN, now, "TIMEOUT")
        val command = OperationalRecoveryCommand(UUID.randomUUID(), operation, UUID.randomUUID(), "Retomar após queda")
        store.reserve(command, now, now.plusSeconds(2)) as OperationalRecoveryReservation.Claimed
        dataSource.connection.use { connection -> connection.prepareStatement(
            "UPDATE receivable_operations SET status='SUCCEEDED',provider_reference='pay_existing' WHERE id=?",
        ).use { it.setObject(1, operation); it.executeUpdate() } }

        val replay = store.reserve(command, now.plusSeconds(3), now.plusSeconds(93)) as OperationalRecoveryReservation.Replay

        assertEquals(OperationalRecoveryResult.CONFIRMED, replay.outcome.result)
        assertEquals(OperationStatus.SUCCEEDED, replay.outcome.operationStatus)
        assertEquals(1, store.detail(operation)!!.audit.size)
    }

    @Test
    fun `admin recovery refuses withdrawal and refund before external IO`() = fixture { dataSource, account ->
        val store = JdbcOperationalReceivables(dataSource)
        for (kind in listOf(OperationKind.WITHDRAW, OperationKind.REFUND)) {
            val operation = operation(dataSource, account, kind, OperationStatus.UNKNOWN, now, null)
            val result = store.reserve(OperationalRecoveryCommand(UUID.randomUUID(), operation, UUID.randomUUID(), "Não permitido"),
                now, now.plusSeconds(90))
            assertEquals(OperationalRecoveryReservation.NotRecoverable, result)
        }
        assertEquals(0, count(dataSource, "receivable_operational_recoveries"))
        assertEquals(0, count(dataSource, "receivable_operation_audit"))
    }

    @Test
    fun `notices preserve exact content window author and idempotency without communication side effects`() = fixture { dataSource, _ ->
        val store = JdbcOperationalNotices(dataSource)
        val command = PublishOperationalNotice(UUID.randomUUID(), UUID.randomUUID(), OperationalNoticeAudience.PLAN_OWNERS,
            "Manutenção programada", "Emissões ficarão indisponíveis durante a janela.", now, now.plusSeconds(3600))

        val created = store.publish(command, now.minusSeconds(1))
        assertEquals(created, store.publish(command, now))
        assertEquals(listOf(created), store.active(OperationalNoticeAudience.PLAN_OWNERS, now.plusSeconds(1)))
        assertTrue(store.active(OperationalNoticeAudience.PLAN_OWNERS, now.plusSeconds(3600)).isEmpty())
        assertEquals(1, store.list(null, 1, 25).total)
        assertFailsWith<OperationalNoticeConflict> { store.publish(command.copy(message = "Mudou"), now) }
    }

    @Test
    fun `external residual cost appends exact observed cents once and rejects cross-account instrument`() = fixture { dataSource, account ->
        val instrument = instrument(dataSource, account)
        val service = ReconcileExternalResidualCost(JdbcExternalResidualCostLedger(dataSource))
        val observed = ObservedResidualCost(account, instrument, "pay_123:refund_fee", 173)

        assertTrue(service.execute(observed, now))
        assertFalse(service.execute(observed, now.plusSeconds(1)))
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.executeQuery(
                "SELECT amount_cents FROM receivable_movements WHERE kind='RESIDUAL_COST' AND provider_reference='pay_123:refund_fee'",
            ).use { result -> assertTrue(result.next()); assertEquals(-173, result.getLong(1)); assertFalse(result.next()) } }
        }
        assertFailsWith<IllegalArgumentException> {
            service.execute(observed.copy(accountId = UUID.randomUUID(), providerReference = "pay_123:other"), now)
        }
    }

    private fun fixture(block: (DataSource, UUID) -> Unit) {
        val dataSource = TestPostgres.migrated(location, owner = this).dataSource
        val account = UUID.randomUUID()
        dataSource.connection.use { connection ->
            connection.prepareStatement("""
                INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,
                    registration,created_at,updated_at) VALUES (?,?,?,?, 'APPROVED',?,?)
            """.trimIndent()).use {
                it.setObject(1, account); it.setObject(2, UUID.randomUUID()); it.setString(3, UUID.randomUUID().toString())
                it.setString(4, "ciphertext"); it.setTimestamp(5, Timestamp.from(now)); it.setTimestamp(6, Timestamp.from(now)); it.executeUpdate()
            }
        }
        block(dataSource, account)
    }

    private fun operation(
        dataSource: DataSource,
        account: UUID,
        kind: OperationKind,
        status: OperationStatus,
        updatedAt: Instant,
        failureCode: String?,
    ): UUID = UUID.randomUUID().also { id ->
        dataSource.connection.use { connection ->
            connection.prepareStatement("""
                INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,
                    request_digest,status,next_attempt_at,failure_code,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent()).use {
                it.setObject(1, id); it.setObject(2, account); it.setObject(3, UUID.randomUUID()); it.setObject(4, UUID.randomUUID())
                it.setString(5, kind.name); it.setObject(6, UUID.randomUUID()); it.setString(7, "0".repeat(64)); it.setString(8, status.name)
                it.setTimestamp(9, Timestamp.from(now)); it.setString(10, failureCode); it.setTimestamp(11, Timestamp.from(now.minusSeconds(60)))
                it.setTimestamp(12, Timestamp.from(updatedAt)); it.executeUpdate()
            }
        }
    }

    private fun instrument(dataSource: DataSource, account: UUID): UUID = UUID.randomUUID().also { instrument ->
        val actor = UUID.randomUUID(); val terms = "terms-${UUID.randomUUID()}"; val acceptance = UUID.randomUUID()
        val fee = UUID.randomUUID(); val order = UUID.randomUUID()
        dataSource.connection.use { connection -> connection.createStatement().use { statement ->
            statement.execute("INSERT INTO receivable_terms VALUES ('$terms','published','${"0".repeat(64)}',now(),now())")
            statement.execute("INSERT INTO receivable_terms_acceptances VALUES ('$acceptance','$account','$actor','$terms','CHARGE','${UUID.randomUUID()}',now())")
            statement.execute("INSERT INTO receivable_fee_schedules VALUES ('$fee','PIX',0,300,0,200,'$terms',now(),now(),'$actor')")
            statement.execute("""
                INSERT INTO receivable_orders(id,account_id,group_id,member_user_id,group_charge_id,due_date,status,base_cents,request_id,issued_at)
                VALUES ('$order','$account','${UUID.randomUUID()}','${UUID.randomUUID()}','${UUID.randomUUID()}','2026-09-13','REFUNDED',10000,'${UUID.randomUUID()}',now())
            """.trimIndent())
            statement.execute("""
                INSERT INTO receivable_instruments(id,account_id,order_id,method,fee_schedule_id,base_cents,fees_cents,total_cents,
                    commission_cents,expected_provider_fee_cents,expected_net_cents,acceptance_id,status,created_at)
                VALUES ('$instrument','$account','$order','PIX','$fee',10000,500,10500,200,300,10000,'$acceptance','REFUNDED',now())
            """.trimIndent())
        } }
    }

    private fun count(dataSource: DataSource, table: String): Long = dataSource.connection.use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery("SELECT count(*) FROM $table").use { it.next(); it.getLong(1) } }
    }
}
