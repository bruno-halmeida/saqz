package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupReceivablesStore(dataSource: DataSource) : GroupReceivablesStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val accounts = JdbcFinancialAccountRepository(dataSource)
    private val mapper = jacksonObjectMapper()
    private class Holder<T>(val value: T)
    override fun <T> transaction(block: () -> T): T = try { transactions.execute { Holder(block()) }.value }
    catch (_: DuplicateKeyException) { throw FinancialRequestConflict() }

    override fun lockAccount(id: UUID): FinancialAccount? {
        jdbc.sql("SELECT id FROM receivable_accounts WHERE id=:id FOR UPDATE").param("id", id)
            .query(UUID::class.java).optional().orElse(null) ?: return null
        return accounts.findById(id)
    }
    override fun state(accountId: UUID, groupId: UUID): GroupReceivablesState? = jdbc.sql("""
        SELECT enabled,pix_enabled,card_enabled FROM receivable_group_links WHERE account_id=:account AND group_id=:group
    """.trimIndent()).param("account", accountId).param("group", groupId).query { rs, _ ->
        GroupReceivablesState(accountId, groupId, rs.getBoolean("enabled"), rs.getBoolean("pix_enabled"), rs.getBoolean("card_enabled"))
    }.optional().orElse(null)

    override fun replay(accountId: UUID, request: FinancialRequest, digest: String): Boolean {
        val existing = jdbc.sql("SELECT actor_user_id,request_digest FROM receivable_operations WHERE account_id=:account AND request_id=:request")
            .param("account", accountId).param("request", request.requestId).query { rs, _ ->
                rs.getObject("actor_user_id", UUID::class.java) to rs.getString("request_digest")
            }.optional().orElse(null) ?: return false
        if (existing.first != request.actorUserId || existing.second != digest) throw FinancialRequestConflict()
        return true
    }

    override fun configure(accountId: UUID, groupId: UUID, request: FinancialRequest, digest: String,
                           review: GroupReceivablesReview?, at: Instant): GroupReceivablesState {
        val enabled = review != null
        val operation = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,status,next_attempt_at,created_at,updated_at)
            VALUES (:id,:account,:request,:actor,:kind,:group,:digest,'SUCCEEDED',:at,:at,:at)
        """.trimIndent()).param("id", operation).param("account", accountId).param("request", request.requestId)
            .param("actor", request.actorUserId).param("kind", if (enabled) "ACTIVATE_GROUP" else "DEACTIVATE_GROUP")
            .param("group", groupId).param("digest", digest).param("at", Timestamp.from(at)).update()
        if (enabled) {
            jdbc.sql("""
                INSERT INTO receivable_group_links(account_id,group_id,enabled,pix_enabled,card_enabled,activated_by,activated_at)
                VALUES (:account,:group,true,:pix,:card,:actor,:at)
                ON CONFLICT(account_id,group_id) DO UPDATE SET enabled=true,pix_enabled=EXCLUDED.pix_enabled,
                    card_enabled=EXCLUDED.card_enabled,activated_by=EXCLUDED.activated_by,activated_at=EXCLUDED.activated_at,disabled_at=NULL
            """.trimIndent()).param("account", accountId).param("group", groupId)
                .param("pix", review.schedules.any { it.method == PaymentMethod.PIX })
                .param("card", review.schedules.any { it.method == PaymentMethod.CARD })
                .param("actor", request.actorUserId).param("at", Timestamp.from(at)).update()
        } else {
            jdbc.sql("UPDATE receivable_group_links SET enabled=false,disabled_at=:at WHERE account_id=:account AND group_id=:group")
                .param("at", Timestamp.from(at)).param("account", accountId).param("group", groupId).update()
            val recurrences = jdbc.sql("""
                UPDATE receivable_recurrences SET status='STOP_PENDING',cutoff_at=:at
                WHERE account_id=:account AND group_id=:group AND status IN ('AUTHORIZING','ACTIVE') RETURNING id
            """.trimIndent()).param("at", Timestamp.from(at)).param("account", accountId).param("group", groupId).query(UUID::class.java).list()
            for (recurrence in recurrences) {
                jdbc.sql("""
                    INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,status,next_attempt_at,created_at,updated_at)
                    VALUES (:id,:account,:request,:actor,'STOP_RECURRENCE',:recurrence,:digest,'READY',:at,:at,:at)
                """.trimIndent()).param("id", UUID.randomUUID()).param("account", accountId)
                    .param("request", UUID.nameUUIDFromBytes("stop:$operation:$recurrence".toByteArray()))
                    .param("actor", request.actorUserId).param("recurrence", recurrence).param("digest", digest)
                    .param("at", Timestamp.from(at)).update()
            }
        }
        jdbc.sql("""
            INSERT INTO receivable_group_configurations VALUES (:operation,:account,:group,:actor,:enabled,:fingerprint,CAST(:conditions AS jsonb),:at)
        """.trimIndent()).param("operation", operation).param("account", accountId).param("group", groupId)
            .param("actor", request.actorUserId).param("enabled", enabled).param("fingerprint", review?.fingerprint)
            .param("conditions", mapper.writeValueAsString(mapOf("schedules" to review?.schedules.orEmpty(), "prices" to review?.prices.orEmpty())))
            .param("at", Timestamp.from(at)).update()
        return state(accountId, groupId)!!
    }
}
