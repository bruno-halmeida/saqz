package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkFactory
import br.com.saqz.groups.application.create.TransactionRunner
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

class JdbcNotificationWhatsApp(
    dataSource: DataSource, private val transaction: TransactionRunner, private val links: AttendanceLinkFactory,
) {
    private val jdbc = JdbcClient.create(dataSource)

    fun drain(sender: NotificationWhatsAppSender, limit: Int = 20) {
        repeat(limit) { if (!deliverOne(sender)) return }
    }

    private fun deliverOne(sender: NotificationWhatsAppSender): Boolean = transaction.inTransaction {
        val job = jdbc.sql("""
            SELECT notification_id, attempts FROM notification_whatsapp_queue
            WHERE status = 'PENDING' AND next_attempt_at <= now()
            ORDER BY notification_id LIMIT 1 FOR UPDATE SKIP LOCKED
        """).query { rs, _ -> rs.getLong("notification_id") to rs.getInt("attempts") }.optional().orElse(null)
            ?: return@inTransaction false
        val (id, attempts) = job
        val message = jdbc.sql("""
            SELECT c.phone, c.group_name, c.body, link.code FROM notification_delivery_context c
            JOIN group_notifications n ON n.sequence = c.sequence
            LEFT JOIN notification_attendance_links link ON link.message_id = n.message_id
            JOIN notification_whatsapp_queue q ON q.notification_id = c.sequence AND q.phone = c.phone
            WHERE c.sequence = :id AND c.whatsapp_enabled
        """).param("id", id).query { rs, _ ->
            val link = rs.getString("code")?.let { "\nConfirmar minha presença no Saqz: ${links.confirm(AttendanceLinkCode.from(it))}" }.orEmpty()
            WhatsAppNotification(id, rs.getString("phone"), "Saqz · ${rs.getString("group_name")}\n${rs.getString("body")}$link")
        }.optional().orElse(null)
        if (message == null) {
            finish(id, "CANCELLED")
            return@inTransaction true
        }
        when (val result = sender.send(message)) {
            WhatsAppDelivery.Accepted -> finish(id, "ACCEPTED")
            WhatsAppDelivery.Failed -> finish(id, "FAILED")
            is WhatsAppDelivery.Retry -> if (attempts + 1 >= 10) finish(id, "FAILED") else {
                val delay = maxOf(result.afterSeconds, minOf(3600L, 60L shl attempts))
                jdbc.sql("""
                    UPDATE notification_whatsapp_queue SET attempts = attempts + 1,
                        next_attempt_at = now() + (:delay * interval '1 second') WHERE notification_id = :id
                """).param("id", id).param("delay", delay).update()
            }
        }
        true
    }

    private fun finish(id: Long, status: String) {
        jdbc.sql("""
            UPDATE notification_whatsapp_queue SET status = :status, completed_at = now(),
                attempts = attempts + CASE WHEN :status = 'CANCELLED' THEN 0 ELSE 1 END WHERE notification_id = :id
        """).param("id", id).param("status", status).update()
    }
}
