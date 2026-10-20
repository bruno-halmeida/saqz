package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcNotificationPush(dataSource: DataSource, private val transaction: TransactionRunner) {
    private val jdbc = JdbcClient.create(dataSource)
    // O token de push-to-start segue gravado (apps 1.0.4+ ainda o enviam), mas nada mais o usa desde o DEB-067.
    fun register(actor: UUID, installation: UUID, token: String, platform: String, liveActivityStartToken: String? = null) =
        transaction.inTransaction {
            // Serializa rotação/transferência do mesmo token sem guardar dois destinatários.
            jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:token, 0))").param("token", token).query { _, _ -> Unit }.single()
            jdbc.sql("DELETE FROM notification_devices WHERE token = :token AND installation_id <> :id")
                .param("token", token).param("id", installation).update()
            jdbc.sql("""
                INSERT INTO notification_devices (installation_id, user_id, token, platform, live_activity_start_token)
                VALUES (:id, :actor, :token, :platform, :start)
                ON CONFLICT (installation_id) DO UPDATE SET user_id = excluded.user_id, token = excluded.token,
                    platform = excluded.platform, live_activity_start_token = excluded.live_activity_start_token,
                    updated_at = now()
            """).param("id", installation).param("actor", actor).param("token", token).param("platform", platform)
                .param("start", liveActivityStartToken, java.sql.Types.VARCHAR).update()
            Unit
        }
    fun unregister(actor: UUID, installation: UUID) {
        jdbc.sql("DELETE FROM notification_devices WHERE user_id = :actor AND installation_id = :id")
            .param("actor", actor).param("id", installation).update()
    }

    fun drain(sender: NotificationPushSender, limit: Int = 20): QueueRun {
        var run = QueueRun()
        repeat(limit) { run += deliverOne(sender) ?: return run }
        return run
    }

    private fun deliverOne(sender: NotificationPushSender): QueueStep? = transaction.inTransaction {
        val id = jdbc.sql("""
            SELECT notification_id FROM notification_push_queue
            WHERE completed_at IS NULL AND next_attempt_at <= now() AND attempts < 10
            ORDER BY notification_id LIMIT 1 FOR UPDATE SKIP LOCKED
        """).query(Long::class.java).optional().orElse(null) ?: return@inTransaction null
        var recipientId: UUID? = null
        val message = jdbc.sql("""
            SELECT c.group_id, c.recipient_id, c.channel, c.body, c.push_body, c.game_id, u.firebase_subject,
                   (SELECT g.local_date FROM games g WHERE g.id = c.game_id) AS game_local_date,
                   coalesce((SELECT g.confirmation_deadline <= now() FROM games g WHERE g.id = c.game_id), false) AS deadline_passed,
                   EXISTS (SELECT 1 FROM game_position_slots s WHERE s.game_id = c.game_id AND s.slots > 0) AS positions
            FROM notification_delivery_context c
            JOIN access_users u ON u.id = c.recipient_id
            WHERE c.sequence = :id AND c.push_enabled
        """).param("id", id).query { rs, _ ->
            val channel = rs.getString("channel")
            recipientId = rs.getObject("recipient_id", UUID::class.java)
            val body = when (channel) {
                // Aviso de jogo cancelado nasce no canal NOTICE com o jogo vinculado e vai com o texto; o mural não.
                "NOTICE" -> if (rs.getObject("game_id") != null) rs.getString("body")
                    else "Você recebeu um aviso do grupo. Abra o app para conferir."
                "CHAT" -> "Você recebeu uma mensagem no grupo. Abra o app para conferir."
                "REMINDER" ->
                    if (rs.getBoolean("deadline_passed")) "Lista do jogo atualizada. Abra o app para conferir."
                    else reminderPushBody(rs.getObject("game_local_date", java.time.LocalDate::class.java))
                "GAME_OPEN" -> "O jogo está liberado. Abra o app para confirmar sua presença."
                "ATTENDANCE_WINDOW" -> rs.getString("body")
                "TRIAL_ENDING" -> rs.getString("push_body") ?: rs.getString("body")
                "WAITLIST_DEADLINE", "FIRST_MEMBER", "FIRST_CONFIRMATION", "ENTRY_REQUEST", "AUTO_CONFIRMATION" -> rs.getString("body")
                else -> "Você recebeu um lembrete de cobrança. Abra o app para conferir."
            }
            NotificationPush(
                id, rs.getObject("group_id", UUID::class.java), "Saqz", body, channel,
                recipient = rs.getString("firebase_subject"), gameId = rs.getObject("game_id", UUID::class.java),
                positions = rs.getBoolean("positions"),
            )
        }.optional().orElse(null)
        var retry = false
        var delivered = 0
        var failed = 0
        if (message != null) {
            val devices = jdbc.sql("""
                SELECT d.installation_id, d.token FROM notification_devices d
                JOIN group_notifications n ON n.recipient_id = d.user_id
                WHERE n.sequence = :id AND NOT EXISTS (
                    SELECT 1 FROM notification_push_deliveries r WHERE r.notification_id = :id AND r.installation_id = d.installation_id)
                ORDER BY d.installation_id FOR UPDATE OF d
            """).param("id", id).query { rs, _ ->
                rs.getObject("installation_id", UUID::class.java) to rs.getString("token")
            }.list()
            devices.forEach { (installation, token) ->
                val delivery = sender.send(token, message)
                when (delivery) {
                    PushDelivery.SENT -> {
                        jdbc.sql("INSERT INTO notification_push_deliveries VALUES (:n, :d) ON CONFLICT DO NOTHING")
                            .param("n", id).param("d", installation).update()
                        delivered++
                        JobLog.item("notification_push", "push_sent", *itemIds(id, message, recipientId, installation))
                    }
                    PushDelivery.INVALID_TOKEN -> {
                        jdbc.sql("DELETE FROM notification_devices WHERE installation_id = :id AND token = :token")
                            .param("id", installation).param("token", token).update()
                        failed++
                        JobLog.itemFailed("notification_push", "push_failed", delivery.name, *itemIds(id, message, recipientId, installation))
                    }
                    PushDelivery.RETRY -> {
                        retry = true
                        failed++
                        JobLog.itemFailed("notification_push", "push_failed", delivery.name, *itemIds(id, message, recipientId, installation))
                    }
                }
            }
        }
        if (retry) jdbc.sql("""
            UPDATE notification_push_queue SET attempts = attempts + 1,
                next_attempt_at = now() + make_interval(secs => least(3600, 60 * power(2, attempts))::integer)
            WHERE notification_id = :id
        """).param("id", id).update()
        else jdbc.sql("UPDATE notification_push_queue SET completed_at = now() WHERE notification_id = :id").param("id", id).update()
        QueueStep(delivered, failed)
    }

    // Só ids: a mensagem e o token nunca vão para o log.
    private fun itemIds(notificationId: Long, message: NotificationPush, userId: UUID?, installation: UUID) = arrayOf<Pair<String, Any?>>(
        "notificationId" to notificationId, "groupId" to message.groupId, "gameId" to message.gameId,
        "userId" to userId, "installationId" to installation,
    )
}
