package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.sharedkernel.group.GroupChargeLedger
import br.com.saqz.sharedkernel.group.LedgerCharge
import br.com.saqz.sharedkernel.group.LedgerGroup
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

/** Lado dos grupos da baixa pelo app (FIN-011, FIN-014). Sem transação própria: participa da de recebimentos. */
class JdbcGroupChargeLedger(dataSource: DataSource) : GroupChargeLedger {
    private val jdbc = JdbcClient.create(dataSource)

    override fun group(groupId: UUID): LedgerGroup? = jdbc.sql("SELECT id,name,owner_user_id FROM access_groups WHERE id=:id AND deleted_at IS NULL")
        .param("id", groupId).query { rs, _ -> LedgerGroup(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getObject("owner_user_id", UUID::class.java)) }
        .optional().orElse(null)

    override fun isActiveMember(groupId: UUID, userId: UUID) = jdbc.sql("""
        SELECT count(*) FROM group_memberships m JOIN access_groups g ON g.id=m.group_id AND g.deleted_at IS NULL
        WHERE m.group_id=:group AND m.user_id=:user AND m.active
    """.trimIndent()).param("group", groupId).param("user", userId).query(Int::class.java).single() > 0

    override fun managers(groupId: UUID): Set<UUID> = jdbc.sql("""
        SELECT g.owner_user_id AS id FROM access_groups g WHERE g.id=:group AND g.deleted_at IS NULL
        UNION SELECT m.user_id FROM group_memberships m JOIN access_groups g ON g.id=m.group_id AND g.deleted_at IS NULL
        WHERE m.group_id=:group AND m.active AND m.role='ADMIN'
    """.trimIndent()).param("group", groupId).query(UUID::class.java).list().filterNotNull().toSet()

    override fun displayName(userId: UUID): String? = jdbc.sql("SELECT display_name FROM access_users WHERE id=:id")
        .param("id", userId).query(String::class.java).optional().orElse(null)

    override fun openCharges(groupId: UUID, payerId: UUID): List<LedgerCharge> = jdbc.sql("""
        $SELECT WHERE c.group_id=:group AND c.member_user_id=:payer AND c.status='PENDING' ORDER BY c.due_date,c.created_at,c.id
    """.trimIndent()).param("group", groupId).param("payer", payerId).query(::map).list()

    override fun lock(chargeIds: Collection<UUID>): List<LedgerCharge> {
        if (chargeIds.isEmpty()) return emptyList()
        return jdbc.sql("$SELECT WHERE c.id IN (:ids) ORDER BY c.id FOR UPDATE OF c").param("ids", chargeIds.toList()).query(::map).list()
    }

    override fun settleByApp(chargeId: UUID, method: String, actorId: UUID, at: Instant): Boolean {
        require(method in setOf("PIX", "CARD"))
        val changed = jdbc.sql("""
            UPDATE group_charges SET status='PAID',paid_method=CAST(:paid AS charge_paid_method),settled_by='APP',app_method=:method,
                changed_by_user_id=:actor,version=version+1,updated_at=:at
            WHERE id=:id AND status='PENDING' AND EXISTS (SELECT 1 FROM access_groups g WHERE g.id=group_charges.group_id AND g.deleted_at IS NULL)
        """.trimIndent()).param("paid", if (method == "PIX") "PIX" else "OTHER").param("method", method).param("actor", actorId)
            .param("at", Timestamp.from(at)).param("id", chargeId).update()
        if (changed != 1) return false
        jdbc.sql("""INSERT INTO group_charge_events(id,charge_id,group_id,actor_user_id,old_status,new_status,note,occurred_at)
            SELECT :event,id,group_id,:actor,'PENDING','PAID',:note,:at FROM group_charges WHERE id=:id""")
            .param("event", UUID.randomUUID()).param("actor", actorId)
            .param("note", if (method == "PIX") "Pago pelo app · Pix" else "Pago pelo app · cartão")
            .param("at", Timestamp.from(at)).param("id", chargeId).update()
        return true
    }

    override fun requireReview(chargeIds: Collection<UUID>, actorId: UUID, at: Instant) {
        if (chargeIds.isEmpty()) return
        jdbc.sql("""UPDATE group_charges SET review_required=true,changed_by_user_id=:actor,version=version+1,updated_at=:at
            WHERE id IN (:ids) AND NOT review_required""").param("actor", actorId).param("at", Timestamp.from(at))
            .param("ids", chargeIds.toList()).update()
    }

    override fun record(id: UUID, groupId: UUID, direction: String, origin: String, description: String, amountCents: Long,
                        date: LocalDate, actorId: UUID, at: Instant) {
        require(direction in setOf("IN", "OUT") && amountCents > 0)
        val text = description.trim().take(160).let { if (it.length < 2) "Pelo app" else it }
        val inserted = jdbc.sql("""
            INSERT INTO group_expenses (id,group_id,description,amount_cents,expense_date,category,custom_category,direction,status,
                created_by_user_id,changed_by_user_id,version,created_at,updated_at,origin)
            SELECT :id,:group,:description,:amount,:date,'OTHER','Pelo app',CAST(:direction AS expense_direction),'ACTIVE',:actor,:actor,1,:at,:at,:origin
            WHERE EXISTS (SELECT 1 FROM access_groups g WHERE g.id=:group) ON CONFLICT (id) DO NOTHING
        """.trimIndent()).param("id", id).param("group", groupId).param("description", text).param("amount", amountCents)
            .param("date", date).param("direction", direction).param("actor", actorId).param("at", Timestamp.from(at))
            .param("origin", origin).update()
        if (inserted == 1) jdbc.sql("""
            INSERT INTO group_expense_events (id,expense_id,group_id,actor_user_id,action,description,amount_cents,expense_date,category,
                custom_category,notes,direction,status,version,occurred_at)
            VALUES (:event,:id,:group,:actor,'CREATED',:description,:amount,:date,'OTHER','Pelo app',NULL,CAST(:direction AS expense_direction),'ACTIVE',1,:at)
        """.trimIndent()).param("event", UUID.randomUUID()).param("id", id).param("group", groupId).param("actor", actorId)
            .param("description", text).param("amount", amountCents).param("date", date).param("direction", direction)
            .param("at", Timestamp.from(at)).update()
    }

    private fun map(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = LedgerCharge(
        rs.getObject("id", UUID::class.java), rs.getObject("group_id", UUID::class.java), rs.getObject("member_user_id", UUID::class.java),
        rs.getString("kind"), rs.getObject("game_id", UUID::class.java), rs.getObject("game_date", LocalDate::class.java),
        rs.getObject("billing_month", LocalDate::class.java), rs.getString("guest_name"), rs.getObject("due_date", LocalDate::class.java),
        rs.getTimestamp("created_at").toInstant(), rs.getLong("amount_cents"), rs.getLong("payable"), rs.getString("status"),
        rs.getString("settled_by"),
    )

    private companion object {
        const val SELECT = """SELECT c.id,c.group_id,c.member_user_id,c.kind::text AS kind,c.game_id,game.local_date AS game_date,c.billing_month,
            CASE WHEN c.guest_seq>0 THEN c.guest_display_name END AS guest_name,c.due_date,c.created_at,c.amount_cents,
            coalesce(c.payable_cents,c.amount_cents) AS payable,c.status::text AS status,c.settled_by
            FROM group_charges c JOIN access_groups g ON g.id=c.group_id AND g.deleted_at IS NULL
            LEFT JOIN games game ON game.id=c.game_id"""
    }
}
