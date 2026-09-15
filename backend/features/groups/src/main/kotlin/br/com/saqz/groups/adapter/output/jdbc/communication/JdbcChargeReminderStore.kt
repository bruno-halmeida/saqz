package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.*
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import javax.sql.DataSource

class JdbcChargeReminderStore(dataSource: DataSource) : ChargeReminderStore {
    private val jdbc = JdbcClient.create(dataSource)
    override fun previous(group: UUID, actor: UUID, request: UUID): ChargeReminderRequest? = jdbc.sql(
        "SELECT charge_ids, notification_count FROM charge_reminder_requests WHERE group_id = :g AND actor_id = :a AND request_id = :r",
    ).param("g", group).param("a", actor).param("r", request).query { rs, _ ->
        ChargeReminderRequest(rs.getString("charge_ids").split(",").map(UUID::fromString).toSet(),
            ChargeReminderReceipt(rs.getInt("notification_count")))
    }.optional().orElse(null)

    override fun pending(group: UUID, ids: Set<UUID>): List<PendingReminderCharge> = jdbc.sql("""
        SELECT c.id, c.member_user_id, c.amount_cents FROM group_charges c
        JOIN group_memberships m ON m.group_id = c.group_id AND m.user_id = c.member_user_id AND m.active
        WHERE c.group_id = :g AND c.id IN (:ids) AND c.status = 'PENDING'
        ORDER BY c.id FOR UPDATE OF c, m
    """).param("g", group).param("ids", ids).query { rs, _ ->
        PendingReminderCharge(rs.getObject("id", UUID::class.java), rs.getObject("member_user_id", UUID::class.java), rs.getLong("amount_cents"))
    }.list()

    override fun create(group: UUID, actor: UUID, request: UUID, charges: List<PendingReminderCharge>): ChargeReminderReceipt {
        charges.forEach { charge ->
            val message = UUID.randomUUID()
            val amount = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(charge.amountCents, 2))
            jdbc.sql("""
                INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, charge_id, recipient_count)
                SELECT :id, :g, :a, display_name, 'CHARGE', :r, :body, :charge, 1 FROM access_users WHERE id = :a
            """).param("id", message).param("g", group).param("a", actor).param("r", UUID.randomUUID())
                .param("body", "Você tem uma cobrança de $amount em aberto. Confira os detalhes em Minhas cobranças.")
                .param("charge", charge.id).update()
            val notification = jdbc.sql("""
                INSERT INTO group_notifications (recipient_id, message_id) VALUES (:recipient, :message) RETURNING sequence
            """).param("recipient", charge.memberId).param("message", message).query(Long::class.java).single()
            jdbc.sql("INSERT INTO notification_push_queue (notification_id) VALUES (:id)").param("id", notification).update()
        }
        jdbc.sql("""
            INSERT INTO charge_reminder_requests (group_id, actor_id, request_id, charge_ids, notification_count)
            VALUES (:g, :a, :r, :ids, :count)
        """).param("g", group).param("a", actor).param("r", request)
            .param("ids", charges.map { it.id.toString() }.sorted().joinToString(",")).param("count", charges.size).update()
        return ChargeReminderReceipt(charges.size)
    }
}
