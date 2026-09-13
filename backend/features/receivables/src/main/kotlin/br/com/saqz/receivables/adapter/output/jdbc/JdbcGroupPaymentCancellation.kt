package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.sharedkernel.group.GroupChargePaymentCancellation
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** The groups transaction already holds its group and charge locks. Recovery will close remote instruments. */
class JdbcGroupPaymentCancellation(dataSource: DataSource, private val charges: br.com.saqz.sharedkernel.group.GroupChargePayments) : GroupChargePaymentCancellation {
    private val jdbc = JdbcClient.create(dataSource)
    override fun request(orderId: UUID, actorId: UUID, at: Instant) {
        jdbc.sql("SELECT a.id FROM receivable_accounts a JOIN receivable_orders o ON o.account_id=a.id WHERE o.id=:id FOR UPDATE OF a")
            .param("id", orderId).query(UUID::class.java).optional()
        val empty = jdbc.sql("SELECT count(*) FROM receivable_instruments WHERE order_id=:id AND status NOT IN ('CANCELLED','EXPIRED')")
            .param("id", orderId).query(Int::class.java).single() == 0
        val status = if (empty) "CANCELLED" else "CANCEL_PENDING"
        val changed = jdbc.sql("UPDATE receivable_orders SET status=:status WHERE id=:id AND status='ISSUED'")
            .param("status", status).param("id", orderId).update()
        if (changed == 1) {
            val request = UUID.nameUUIDFromBytes("game-cancel:$orderId".toByteArray())
            jdbc.sql("""INSERT INTO receivable_operations(id,account_id,request_id,actor_user_id,kind,resource_id,request_digest,status,next_attempt_at,created_at,updated_at)
                SELECT :request,account_id,:request,:actor,'CANCEL_ORDER',id,:digest,'SUCCEEDED',:at,:at,:at FROM receivable_orders WHERE id=:id
                ON CONFLICT(account_id,request_id) DO NOTHING""")
                .param("request", request).param("actor", actorId).param("digest", br.com.saqz.receivables.application.paymentDigest("game-cancel:$orderId"))
                .param("at", java.sql.Timestamp.from(at)).param("id", orderId).update()
            if (empty) {
                val charge = jdbc.sql("SELECT group_charge_id FROM receivable_orders WHERE id=:id").param("id", orderId).query(UUID::class.java).single()
                charges.release(charge, orderId)
            }
        }
    }
}
