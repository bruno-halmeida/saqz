package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.sharedkernel.group.GroupChargePayments
import br.com.saqz.sharedkernel.group.PayableGroupCharge
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupChargePayments(dataSource: DataSource) : GroupChargePayments {
    private val jdbc = JdbcClient.create(dataSource)
    override fun lock(chargeId: UUID): PayableGroupCharge? {
        val group = jdbc.sql("SELECT group_id FROM group_charges WHERE id=:id").param("id", chargeId)
            .query(UUID::class.java).optional().orElse(null) ?: return null
        jdbc.sql("SELECT id FROM access_groups WHERE id=:id FOR UPDATE").param("id", group).query(UUID::class.java).optional()
        return jdbc.sql("""SELECT c.*, g.owner_user_id, g.deleted_at IS NULL AS group_active FROM group_charges c
            JOIN access_groups g ON g.id=c.group_id WHERE c.id=:id FOR UPDATE OF c""")
            .param("id", chargeId).query { r, _ -> PayableGroupCharge(chargeId, group,
                r.getObject("member_user_id", UUID::class.java), r.getLong("amount_cents"),
                r.getObject("due_date", LocalDate::class.java), r.getString("status") == "PENDING",
                r.getLong("version"), r.getObject("electronic_order_id", UUID::class.java), r.getBoolean("group_active"), r.getObject("owner_user_id", UUID::class.java), r.getObject("billing_month", LocalDate::class.java))
            }.optional().orElse(null)
    }
    override fun reserve(chargeId: UUID, orderId: UUID) = jdbc.sql("""UPDATE group_charges SET electronic_order_id=:order
        WHERE id=:id AND status='PENDING' AND electronic_order_id IS NULL""")
        .param("order", orderId).param("id", chargeId).update() == 1
    override fun release(chargeId: UUID, orderId: UUID) {
        val cancelled = jdbc.sql("""UPDATE group_charges c SET status='CANCELLED',electronic_order_id=NULL,
            version=version+1,updated_at=now() WHERE c.id=:id AND c.electronic_order_id=:order AND c.status='PENDING'
            AND EXISTS (SELECT 1 FROM games g WHERE g.id=c.game_id AND g.status='CANCELLED') RETURNING c.id""")
            .param("id", chargeId).param("order", orderId).query(UUID::class.java).optional().isPresent
        if (cancelled) {
            jdbc.sql("""INSERT INTO group_charge_events(id,charge_id,group_id,actor_user_id,old_status,new_status,note,occurred_at)
                SELECT :event,id,group_id,changed_by_user_id,'PENDING','CANCELLED','Jogo cancelado; instrumento eletrônico encerrado',now()
                FROM group_charges WHERE id=:id""").param("event", UUID.randomUUID()).param("id", chargeId).update()
            return
        }
        jdbc.sql("UPDATE group_charges SET electronic_order_id=NULL WHERE id=:id AND electronic_order_id=:order AND status='PENDING'")
            .param("id", chargeId).param("order", orderId).update()
    }
    override fun recordPayment(chargeId: UUID, orderId: UUID, actorId: UUID, pix: Boolean, at: Instant) =
        apply(chargeId, orderId, actorId, at, false, pix)
    override fun recordReversal(chargeId: UUID, orderId: UUID, actorId: UUID, at: Instant) =
        apply(chargeId, orderId, actorId, at, true, false)
    private fun apply(charge: UUID, order: UUID, actor: UUID, at: Instant, reversal: Boolean, pix: Boolean): Boolean {
        val effect = if (reversal) "REVERSAL" else "PAYMENT"
        if (jdbc.sql("SELECT count(*) FROM group_charge_payment_effects WHERE order_id=:order AND effect=:effect")
                .param("order", order).param("effect", effect).query(Int::class.java).single() > 0) return true
        val old = if (reversal) "PAID" else "PENDING"
        val target = if (reversal) "CANCELLED" else "PAID"
        val changed = jdbc.sql("""UPDATE group_charges SET status=CAST(:target AS charge_status),
            paid_method=CAST(:method AS charge_paid_method), review_required=review_required OR :reversal,
            changed_by_user_id=:actor,version=version+1,updated_at=:at
            WHERE id=:id AND electronic_order_id=:order AND status::text=:old""")
            .param("target", target).param("method", if (reversal) null else if (pix) "PIX" else "OTHER")
            .param("reversal", reversal).param("actor", actor).param("at", Timestamp.from(at))
            .param("id", charge).param("order", order).param("old", old).update()
        if (changed != 1) return false
        jdbc.sql("""INSERT INTO group_charge_events(id,charge_id,group_id,actor_user_id,old_status,new_status,note,occurred_at)
            SELECT :event,id,group_id,:actor,CAST(:old AS charge_status),CAST(:target AS charge_status),:note,:at
            FROM group_charges WHERE id=:id""")
            .param("event", UUID.randomUUID()).param("actor", actor).param("old", old).param("target", target)
            .param("note", if (reversal) "Reversão eletrônica; dívida não reaberta" else "Pagamento eletrônico confirmado")
            .param("at", Timestamp.from(at)).param("id", charge).update()
        jdbc.sql("INSERT INTO group_charge_payment_effects VALUES (:order,:effect,:charge,:at)")
            .param("order", order).param("effect", effect).param("charge", charge).param("at", Timestamp.from(at)).update()
        return true
    }
}
