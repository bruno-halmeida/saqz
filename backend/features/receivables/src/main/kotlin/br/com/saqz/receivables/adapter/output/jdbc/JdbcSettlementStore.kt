package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcSettlementStore(dataSource: DataSource) : SettlementStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private class Holder<T>(val value: T)
    override fun <T> transaction(block: () -> T): T = transactions.execute { Holder(block()) }!!.value

    override fun claim(accountId: UUID, paymentId: String, at: Instant): Boolean {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))").param("key", "settlement:$accountId:$paymentId")
            .query { _, _ -> Unit }.single()
        return settlement(accountId, paymentId) == null
    }

    override fun save(record: SettlementRecord) {
        jdbc.sql("""
            INSERT INTO payment_settlements(account_id,provider_payment_id,code_id,group_id,method,value_cents,net_cents,commission_cents,
                settled_charge_ids,leftover_cents,commission_operation_id,settled_at)
            VALUES (:account,:payment,:code,:group,:method,:value,:net,:commission,:charges,:leftover,:operation,:at)
        """.trimIndent()).param("account", record.accountId).param("payment", record.paymentId).param("code", record.codeId)
            .param("group", record.groupId).param("method", record.method?.name).param("value", record.valueCents)
            .param("net", record.netCents).param("commission", record.commissionCents)
            .param("charges", record.settledChargeIds.joinToString(",")).param("leftover", record.leftoverCents)
            .param("operation", record.commissionOperationId).param("at", Timestamp.from(record.at)).update()
    }

    override fun settlement(accountId: UUID, paymentId: String): SettlementRecord? = jdbc.sql(
        "SELECT * FROM payment_settlements WHERE account_id=:account AND provider_payment_id=:payment",
    ).param("account", accountId).param("payment", paymentId).query { rs, _ -> record(rs) }.optional().orElse(null)

    override fun markReversed(accountId: UUID, paymentId: String, at: Instant) = jdbc.sql("""
        UPDATE payment_settlements SET reversed_at=:at WHERE account_id=:account AND provider_payment_id=:payment AND reversed_at IS NULL
    """.trimIndent()).param("at", Timestamp.from(at)).param("account", accountId).param("payment", paymentId).update() == 1

    override fun codeByReference(accountId: UUID, reference: String?, qrId: String?): UUID? {
        val id = reference?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return jdbc.sql("""
            SELECT id FROM payment_codes WHERE account_id=:account AND (id=:id OR (CAST(:qr AS varchar) IS NOT NULL AND provider_qr_id=:qr))
            ORDER BY (id=:id) DESC LIMIT 1
        """.trimIndent()).param("account", accountId).param("id", id, java.sql.Types.OTHER).param("qr", qrId, java.sql.Types.VARCHAR)
            .query(UUID::class.java).optional().orElse(null)
    }

    override fun expireCodesCovering(chargeIds: Collection<UUID>, except: UUID?, at: Instant) {
        if (chargeIds.isEmpty()) return
        jdbc.sql("""
            UPDATE payment_codes SET status='EXPIRED' WHERE status IN ('CREATING','PENDING') AND id<>coalesce(CAST(:except AS uuid),'00000000-0000-0000-0000-000000000000')
                AND id IN (SELECT code_id FROM payment_code_charges WHERE charge_id IN (:charges))
        """.trimIndent()).param("except", except, java.sql.Types.OTHER).param("charges", chargeIds.toList()).update()
    }

    override fun insertReview(review: NewReview) {
        jdbc.sql("""
            INSERT INTO payment_reviews(id,account_id,provider_payment_id,payer_name,amount_cents,fees_cents,received_at,status)
            VALUES (:id,:account,:payment,:payer,:amount,:fees,:at,'OPEN') ON CONFLICT (account_id,provider_payment_id) DO NOTHING
        """.trimIndent()).param("id", review.id).param("account", review.accountId).param("payment", review.paymentId)
            .param("payer", review.payerName).param("amount", review.amountCents).param("fees", review.feesCents)
            .param("at", Timestamp.from(review.receivedAt)).update()
    }

    override fun accountGroups(accountId: UUID): List<UUID> = jdbc.sql(
        "SELECT group_id FROM receivable_group_links WHERE account_id=:account AND enabled ORDER BY activated_at,group_id",
    ).param("account", accountId).query(UUID::class.java).list().filterNotNull()

    override fun registerCommissionTransfer(accountId: UUID, operationId: UUID, actorId: UUID, at: Instant) {
        jdbc.sql("""
            INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,status,next_attempt_at,created_at,updated_at)
            VALUES (:id,:account,:id,:actor,'TRANSFER_COMMISSION',:id,:digest,'READY',:at,:at,:at) ON CONFLICT (account_id,request_id) DO NOTHING
        """.trimIndent()).param("id", operationId).param("account", accountId).param("actor", actorId)
            .param("digest", paymentDigest("TRANSFER_COMMISSION:$operationId")).param("at", Timestamp.from(at)).update()
    }

    private fun record(rs: ResultSet) = SettlementRecord(rs.getObject("account_id", UUID::class.java), rs.getString("provider_payment_id"),
        rs.getObject("code_id", UUID::class.java), rs.getObject("group_id", UUID::class.java), rs.getString("method")?.let(PaymentMethod::valueOf),
        rs.getLong("value_cents"), rs.getObject("net_cents") as Long?, rs.getLong("commission_cents"),
        rs.getString("settled_charge_ids").split(',').filter { it.isNotBlank() }.map(UUID::fromString), rs.getLong("leftover_cents"),
        rs.getObject("commission_operation_id", UUID::class.java), rs.getTimestamp("settled_at").toInstant())
}

/** Comissão do Pix para a carteira do Saqz (seção E): uma operação por pagamento, nunca duas transferências. */
class JdbcCommissionTransfers(dataSource: DataSource, private val operations: FinancialOperationStore,
    private val provider: PaymentFactsProvider, private val clock: Clock) : CommissionTransfers {
    private val jdbc = JdbcClient.create(dataSource)

    override fun run(accountId: UUID, operationId: UUID) {
        val now = clock.instant()
        val claim = operations.claim(accountId, operationId, now, now.plusSeconds(90)) ?: return
        val amount = jdbc.sql("SELECT commission_cents FROM payment_settlements WHERE commission_operation_id=:id")
            .param("id", operationId).query(Long::class.java).optional().orElse(0L)
        val result = try {
            when {
                amount <= 0 -> null
                claim.recoveryOnly -> provider.findTransfer(accountId, operationId)
                else -> provider.transfer(accountId, operationId, amount)
            }
        } catch (_: DefinitivePaymentRejection) {
            operations.finish(claim, OperationStatus.REJECTED, null, now, now.plusSeconds(60)); return
        } catch (_: Exception) { null }
        operations.finish(claim, if (result != null) OperationStatus.SUCCEEDED else OperationStatus.UNKNOWN, result, now, now.plusSeconds(60))
    }

    fun recoverPending() {
        JobLog.frequent("commission_transfers", {
            jdbc.sql("""SELECT account_id,id FROM receivable_operations WHERE kind='TRANSFER_COMMISSION'
                AND ((status IN ('READY','UNKNOWN') AND next_attempt_at<=:now) OR (status='RUNNING' AND lease_until<=:now)) LIMIT 50""")
                .param("now", Timestamp.from(clock.instant()))
                .query { rs, _ -> rs.getObject("account_id", UUID::class.java) to rs.getObject("id", UUID::class.java) }.list()
                .onEach { (account, id) -> runCatching { run(account, id) } }.size
        }) { mapOf("attempted" to it) }
    }
}
