package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkFactory
import br.com.saqz.groups.application.create.TransactionRunner
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
            SELECT c.phone, c.group_name, c.body, c.channel, link.code,
                   charge.amount_cents, charge.due_date, owner.pix_key, owner.pix_label
            FROM notification_delivery_context c
            JOIN group_notifications n ON n.sequence = c.sequence
            JOIN group_messages m ON m.id = n.message_id
            LEFT JOIN notification_attendance_links link ON link.message_id = n.message_id
            LEFT JOIN group_charges charge ON charge.id = m.charge_id
            LEFT JOIN access_groups owner ON owner.id = c.group_id
            JOIN notification_whatsapp_queue q ON q.notification_id = c.sequence AND q.phone = c.phone
            WHERE c.sequence = :id AND c.whatsapp_enabled
        """).param("id", id).query { rs, _ ->
            val link = rs.getString("code")?.let { "\nConfirmar minha presença no Saqz: ${links.confirm(AttendanceLinkCode.from(it))}" }.orEmpty()
            val body = if (rs.getString("channel") == "CHARGE" && rs.getObject("amount_cents") != null) {
                chargeBody(
                    rs.getLong("amount_cents"), rs.getObject("due_date", LocalDate::class.java),
                    rs.getString("pix_key"), rs.getString("pix_label"),
                )
            } else rs.getString("body")
            WhatsAppNotification(id, rs.getString("phone"), "Saqz · ${rs.getString("group_name")}\n$body$link")
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

/**
 * Texto da cobrança no WhatsApp particular. A linha do Pix só entra quando o grupo tem chave; sem link, porque
 * o app ainda não tem rota pública para a cobrança.
 */
internal fun chargeBody(amountCents: Long, dueDate: LocalDate, pixKey: String?, pixLabel: String?): String {
    val amount = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(amountCents, 2)).replace(' ', ' ')
    val pix = pixKey?.let { "\nPix: $it" + (pixLabel?.let { label -> " ($label)" } ?: "") }.orEmpty()
    val due = dueDate.format(DateTimeFormatter.ofPattern("dd/MM"))
    return "Você tem uma cobrança de $amount em aberto (venceu em $due).$pix\nDepois de pagar, o organizador dá baixa."
}
