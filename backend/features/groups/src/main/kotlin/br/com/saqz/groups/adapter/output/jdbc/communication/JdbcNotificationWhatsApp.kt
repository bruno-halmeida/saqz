package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.*
import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkFactory
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.sql.DataSource

class JdbcNotificationWhatsApp(
    dataSource: DataSource, private val transaction: TransactionRunner, private val links: AttendanceLinkFactory,
    private val receivables: br.com.saqz.sharedkernel.group.GroupReceivables = br.com.saqz.sharedkernel.group.GroupReceivables.NONE,
) {
    private val jdbc = JdbcClient.create(dataSource)

    fun drain(sender: NotificationWhatsAppSender, limit: Int = 20): QueueRun {
        var run = QueueRun()
        repeat(limit) { run += deliverOne(sender) ?: return run }
        return run
    }

    private fun deliverOne(sender: NotificationWhatsAppSender): QueueStep? = transaction.inTransaction {
        val job = jdbc.sql("""
            SELECT notification_id, attempts FROM notification_whatsapp_queue
            WHERE status = 'PENDING' AND next_attempt_at <= now()
            ORDER BY notification_id LIMIT 1 FOR UPDATE SKIP LOCKED
        """).query { rs, _ -> rs.getLong("notification_id") to rs.getInt("attempts") }.optional().orElse(null)
            ?: return@inTransaction null
        val (id, attempts) = job
        var groupId: UUID? = null
        var userId: UUID? = null
        var messageId: UUID? = null
        val message = jdbc.sql("""
            SELECT c.phone, c.group_name, c.group_id, c.recipient_id, n.message_id, c.body, c.channel, link.code,
                   coalesce(charge.payable_cents, charge.amount_cents) AS amount_cents, charge.due_date, owner.pix_key, owner.pix_label,
                   charge.id AS charge_id, m.whatsapp_body, m.whatsapp_button_label, m.whatsapp_button_url, m.whatsapp_follow_up
            FROM notification_delivery_context c
            JOIN group_notifications n ON n.sequence = c.sequence
            JOIN group_messages m ON m.id = n.message_id
            LEFT JOIN notification_attendance_links link ON link.message_id = n.message_id
            LEFT JOIN group_charges charge ON charge.id = m.charge_id
            LEFT JOIN access_groups owner ON owner.id = c.group_id
            JOIN notification_whatsapp_queue q ON q.notification_id = c.sequence AND q.phone = c.phone
            WHERE c.sequence = :id AND c.whatsapp_enabled
        """).param("id", id).query { rs, _ ->
            groupId = rs.getObject("group_id", UUID::class.java)
            userId = rs.getObject("recipient_id", UUID::class.java)
            messageId = rs.getObject("message_id", UUID::class.java)
            val link = rs.getString("code")?.let { "\nConfirmar minha presença no Saqz: ${links.confirm(AttendanceLinkCode.from(it))}" }.orEmpty()
            val header = "Saqz · ${rs.getString("group_name")}\n"
            if (rs.getString("channel") == "PAYMENT") {
                // F3/F4: texto, botão e a segunda mensagem só com o copia e cola, gravados por recebimentos.
                val button = rs.getString("whatsapp_button_url")?.let { WhatsAppGroupButton(rs.getString("whatsapp_button_label") ?: "Abrir no app", it) }
                return@query WhatsAppNotification(id, rs.getString("phone"), header + rs.getString("whatsapp_body"), button = button,
                    followUp = rs.getString("whatsapp_follow_up"))
            }
            if (rs.getString("channel") == "CHARGE" && rs.getObject("amount_cents") != null) {
                val amount = rs.getLong("amount_cents")
                val due = rs.getObject("due_date", LocalDate::class.java)
                // Com recebimentos ativos sai a chave pessoal e entram "Pagar no app" e o copia e cola numa segunda mensagem.
                val collection = receivables.chargeCollection(groupId!!, userId!!, listOf(rs.getObject("charge_id", UUID::class.java)))
                if (collection != null) return@query WhatsAppNotification(id, rs.getString("phone"), header + appChargeBody(amount, due),
                    button = WhatsAppGroupButton("Pagar no app", collection.buttonUrl), followUp = collection.pixPayload)
                return@query WhatsAppNotification(id, rs.getString("phone"),
                    header + chargeBody(amount, due, rs.getString("pix_key"), rs.getString("pix_label")) + link)
            }
            WhatsAppNotification(id, rs.getString("phone"), header + rs.getString("body") + link)
        }.optional().orElse(null)
        if (message == null) {
            finish(id, "CANCELLED")
            return@inTransaction QueueStep()
        }
        // Só ids: telefone, nome do grupo e texto da mensagem nunca vão para o log.
        val ids = arrayOf<Pair<String, Any?>>("notificationId" to id, "groupId" to groupId, "userId" to userId, "messageId" to messageId)
        var step = QueueStep()
        when (val result = sender.send(message)) {
            WhatsAppDelivery.Accepted -> {
                finish(id, "ACCEPTED")
                step = QueueStep(delivered = 1)
                JobLog.item("notification_whatsapp", "whatsapp_accepted", *ids)
            }
            WhatsAppDelivery.Failed -> {
                finish(id, "FAILED")
                step = QueueStep(failed = 1)
                JobLog.itemFailed("notification_whatsapp", "whatsapp_failed", "FAILED", *ids)
            }
            is WhatsAppDelivery.Retry -> {
                step = QueueStep(failed = 1)
                JobLog.itemFailed("notification_whatsapp", "whatsapp_failed", if (attempts + 1 >= 10) "RETRY_EXHAUSTED" else "RETRY", *ids)
                if (attempts + 1 >= 10) finish(id, "FAILED") else {
                    val delay = maxOf(result.afterSeconds, minOf(3600L, 60L shl attempts))
                    jdbc.sql("""
                        UPDATE notification_whatsapp_queue SET attempts = attempts + 1,
                            next_attempt_at = now() + (:delay * interval '1 second') WHERE notification_id = :id
                    """).param("id", id).param("delay", delay).update()
                }
            }
        }
        step
    }

    private fun finish(id: Long, status: String) {
        jdbc.sql("""
            UPDATE notification_whatsapp_queue SET status = :status, completed_at = now(),
                attempts = attempts + CASE WHEN :status = 'CANCELLED' THEN 0 ELSE 1 END WHERE notification_id = :id
        """).param("id", id).param("status", status).update()
    }
}

/** Cobrança no WhatsApp particular com recebimentos ativos (FIN-013, texto do canvas "WhatsApp · particular do atleta"). */
internal fun appChargeBody(amountCents: Long, dueDate: LocalDate): String {
    val amount = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(amountCents, 2)).replace('\u00a0', ' ')
    val due = dueDate.format(DateTimeFormatter.ofPattern("dd/MM"))
    return "Você tem $amount em aberto (venceu em $due).\nPague pelo app ou copie o código da próxima mensagem no app do seu banco. A baixa sai sozinha."
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
