package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.application.create.TransactionRunner
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcNotificationPush(dataSource: DataSource, private val transaction: TransactionRunner) {
    private val jdbc = JdbcClient.create(dataSource)
    fun register(actor: UUID, installation: UUID, token: String, platform: String) = transaction.inTransaction {
        // Serializa rotação/transferência do mesmo token sem guardar dois destinatários.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:token, 0))").param("token", token).query { _, _ -> Unit }.single()
        jdbc.sql("DELETE FROM notification_devices WHERE token = :token AND installation_id <> :id")
            .param("token", token).param("id", installation).update()
        jdbc.sql("""
            INSERT INTO notification_devices (installation_id, user_id, token, platform) VALUES (:id, :actor, :token, :platform)
            ON CONFLICT (installation_id) DO UPDATE SET user_id = excluded.user_id, token = excluded.token,
                platform = excluded.platform, updated_at = now()
        """).param("id", installation).param("actor", actor).param("token", token).param("platform", platform).update()
        Unit
    }
    fun unregister(actor: UUID, installation: UUID) {
        jdbc.sql("DELETE FROM notification_devices WHERE user_id = :actor AND installation_id = :id")
            .param("actor", actor).param("id", installation).update()
    }

    fun drain(sender: NotificationPushSender, limit: Int = 20) {
        repeat(limit) { if (!deliverOne(sender)) return }
    }

    private fun deliverOne(sender: NotificationPushSender): Boolean = transaction.inTransaction {
        val id = jdbc.sql("""
            SELECT notification_id FROM notification_push_queue
            WHERE completed_at IS NULL AND next_attempt_at <= now() AND attempts < 10
            ORDER BY notification_id LIMIT 1 FOR UPDATE SKIP LOCKED
        """).query(Long::class.java).optional().orElse(null) ?: return@inTransaction false
        val message = jdbc.sql("""
            SELECT m.group_id, g.name FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
            JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
            JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = n.recipient_id AND gm.active
            JOIN group_charges c ON c.id = m.charge_id AND c.status = 'PENDING'
            LEFT JOIN group_notification_preferences p ON p.user_id = n.recipient_id
            WHERE n.sequence = :id AND coalesce(p.reminders, true)
        """).param("id", id).query { rs, _ ->
            NotificationPush(id, rs.getObject("group_id", UUID::class.java), "Saqz · ${rs.getString("name")}",
                "Você recebeu um lembrete de cobrança. Abra o app para conferir.")
        }.optional().orElse(null)
        var retry = false
        if (message != null) {
            val devices = jdbc.sql("""
                SELECT d.installation_id, d.token FROM notification_devices d
                JOIN group_notifications n ON n.recipient_id = d.user_id
                WHERE n.sequence = :id AND NOT EXISTS (
                    SELECT 1 FROM notification_push_deliveries r WHERE r.notification_id = :id AND r.installation_id = d.installation_id)
                ORDER BY d.installation_id FOR UPDATE OF d
            """).param("id", id).query { rs, _ -> rs.getObject("installation_id", UUID::class.java) to rs.getString("token") }.list()
            devices.forEach { (installation, token) ->
                when (sender.send(token, message)) {
                    PushDelivery.SENT -> jdbc.sql("INSERT INTO notification_push_deliveries VALUES (:n, :d) ON CONFLICT DO NOTHING")
                        .param("n", id).param("d", installation).update()
                    PushDelivery.INVALID_TOKEN -> jdbc.sql("DELETE FROM notification_devices WHERE installation_id = :id AND token = :token")
                        .param("id", installation).param("token", token).update()
                    PushDelivery.RETRY -> retry = true
                }
            }
        }
        if (retry) jdbc.sql("""
            UPDATE notification_push_queue SET attempts = attempts + 1,
                next_attempt_at = now() + make_interval(secs => least(3600, 30 * power(2, attempts))::integer)
            WHERE notification_id = :id
        """).param("id", id).update()
        else jdbc.sql("UPDATE notification_push_queue SET completed_at = now() WHERE notification_id = :id").param("id", id).update()
        true
    }
}
