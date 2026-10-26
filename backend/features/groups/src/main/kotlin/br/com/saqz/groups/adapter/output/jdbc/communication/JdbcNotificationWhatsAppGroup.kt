package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkFactory
import br.com.saqz.groups.application.communication.NotificationWhatsAppGroupSender
import br.com.saqz.groups.application.communication.QueueRun
import br.com.saqz.groups.application.communication.QueueStep
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppGroupButton
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Fila durável de avisos/presença para o grupo de WhatsApp vinculado.
 *
 * Cada rodada entrega até [drain]'s `limit` jobs `PENDING`; antes de enviar revalida o vínculo
 * (ativo, habilitado, não quebrado), o jogo (REMINDER ainda aberto) e a presença da instância no
 * grupo. Grupo inexistente ou instância fora dele quebram o vínculo e cancelam todos os pendentes
 * do grupo; falha transitória entra em backoff, até 10 tentativas → `FAILED`.
 */
class JdbcNotificationWhatsAppGroup(
    dataSource: DataSource, private val transaction: TransactionRunner, private val links: AttendanceLinkFactory,
    /** Imagem "Hora do acerto · Pague aqui embaixo" (links.saqz.app/assets/whatsapp-acerto.jpg). */
    private val settleImageUrl: String? = null,
) {
    private val jdbc = JdbcClient.create(dataSource)

    fun drain(sender: NotificationWhatsAppGroupSender, directory: WhatsAppGroupDirectory, limit: Int = 20): QueueRun {
        var run = QueueRun()
        repeat(limit) { run += deliverOne(sender, directory) ?: return run }
        return run
    }

    private fun deliverOne(sender: NotificationWhatsAppGroupSender, directory: WhatsAppGroupDirectory): QueueStep? =
        transaction.inTransaction {
            val job = jdbc.sql("""
                SELECT message_id, attempts FROM notification_whatsapp_group_queue
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                ORDER BY next_attempt_at, message_id LIMIT 1 FOR UPDATE SKIP LOCKED
            """).query { rs, _ -> Job(rs.getObject("message_id", UUID::class.java), rs.getInt("attempts")) }
                .optional().orElse(null)
                ?: return@inTransaction null

            val message = load(job.messageId) ?: run { finish(job.messageId, "CANCELLED"); return@inTransaction QueueStep() }
            val binding = message.binding
            if (binding == null || !binding.enabled || binding.brokenAt != null) {
                finish(job.messageId, "CANCELLED")
                return@inTransaction QueueStep()
            }
            // O modo do grupo pode ter mudado com a mensagem na fila: lembrete automático só sai na origem do modo atual.
            if (message.channel == "REMINDER" &&
                (!reminderOpen(message.gameId, message.groupId) || message.reminderOrigin?.let { it != message.updatesMode } == true)
            ) {
                finish(job.messageId, "CANCELLED")
                return@inTransaction QueueStep()
            }
            // Só ids: JID, nome do grupo e texto da mensagem nunca vão para o log.
            val ids = arrayOf<Pair<String, Any?>>("messageId" to job.messageId, "groupId" to message.groupId, "gameId" to message.gameId)
            try {
                directory.groupInfo(binding.whatsappJid)
                if (!directory.isMember(binding.whatsappJid)) {
                    breakBinding(binding.groupId)
                    JobLog.itemFailed("notification_whatsapp_group", "binding_broken", "NOT_MEMBER", *ids)
                    return@inTransaction QueueStep(failed = 1)
                }
            } catch (error: DirectoryError) {
                val broken = error is DirectoryError.NotInGroup
                if (broken) breakBinding(binding.groupId) else scheduleRetry(job)
                JobLog.itemFailed("notification_whatsapp_group", if (broken) "binding_broken" else "directory_failed", error, *ids)
                return@inTransaction QueueStep(failed = 1)
            }
            val content = content(message, binding) ?: run { finish(job.messageId, "CANCELLED"); return@inTransaction QueueStep() }
            when (val result = sender.send(binding.whatsappJid, job.messageId, content.text, content.buttons, content.image)) {
                WhatsAppDelivery.Accepted -> {
                    finish(job.messageId, "ACCEPTED")
                    JobLog.item("notification_whatsapp_group", "whatsapp_group_accepted", *ids)
                    QueueStep(delivered = 1)
                }
                WhatsAppDelivery.Failed -> {
                    finish(job.messageId, "FAILED")
                    JobLog.itemFailed("notification_whatsapp_group", "whatsapp_group_failed", "FAILED", *ids)
                    QueueStep(failed = 1)
                }
                is WhatsAppDelivery.Retry -> {
                    scheduleRetry(job, result.afterSeconds)
                    JobLog.itemFailed("notification_whatsapp_group", "whatsapp_group_failed", "RETRY", *ids)
                    QueueStep(failed = 1)
                }
            }
        }

    private fun load(messageId: UUID): PendingMessage? = jdbc.sql("""
        SELECT m.group_id, m.channel, m.game_id, m.body, m.reminder_origin, m.event_kind, m.whatsapp_button_label, m.whatsapp_button_url,
               g.attendance_updates_mode, b.whatsapp_jid, b.group_name, b.enabled,
               b.broken_at, link.code
        FROM group_messages m
        JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
        LEFT JOIN group_whatsapp_bindings b ON b.group_id = m.group_id
        LEFT JOIN notification_attendance_links link ON link.message_id = m.id
        WHERE m.id = :id
    """).param("id", messageId).query { rs, _ ->
        val binding = rs.getString("whatsapp_jid")?.let {
            Binding(
                groupId = rs.getObject("group_id", UUID::class.java),
                whatsappJid = it,
                groupName = rs.getString("group_name"),
                enabled = rs.getBoolean("enabled"),
                brokenAt = rs.getObject("broken_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
        PendingMessage(
            groupId = rs.getObject("group_id", UUID::class.java),
            channel = rs.getString("channel"),
            gameId = rs.getObject("game_id", UUID::class.java),
            body = rs.getString("body"),
            code = rs.getString("code"),
            binding = binding,
            reminderOrigin = rs.getString("reminder_origin"),
            updatesMode = rs.getString("attendance_updates_mode"),
            settleButton = rs.getString("whatsapp_button_url")?.takeIf { rs.getString("event_kind") == "SETTLE_TIME" }
                ?.let { WhatsAppGroupButton(rs.getString("whatsapp_button_label"), it) },
        )
    }.optional().orElse(null)

    private fun reminderOpen(gameId: UUID?, groupId: UUID): Boolean = gameId != null && jdbc.sql("""
        SELECT count(*) FROM games WHERE id = :game AND group_id = :group
          AND status = 'PUBLISHED' AND starts_at > now()
    """).param("game", gameId).param("group", groupId).query(Int::class.java).single() == 1

    private fun content(message: PendingMessage, binding: Binding): GroupContent? {
        // Hora do acerto (FIN-013): o texto já vem montado com o nome do grupo; um botão de URL e a imagem do acerto.
        message.settleButton?.let { return GroupContent(message.body, listOf(it), settleImageUrl) }
        val prefix = if (message.channel == "REMINDER") binding.groupName else "Saqz · ${binding.groupName}"
        val text = "$prefix\n${message.body}"
        if (message.channel != "REMINDER") return GroupContent(text, emptyList())
        val code = message.code ?: return null
        val attendance = AttendanceLinkCode.from(code)
        return GroupContent(
            text,
            listOf(
                WhatsAppGroupButton(CONFIRM_LABEL, links.confirm(attendance).toString()),
                WhatsAppGroupButton(DECLINE_LABEL, links.decline(attendance).toString()),
            ),
        )
    }

    private fun breakBinding(groupId: UUID) {
        jdbc.sql("UPDATE group_whatsapp_bindings SET broken_at = now(), updated_at = now() WHERE group_id = :groupId")
            .param("groupId", groupId).update()
        jdbc.sql("""
            UPDATE notification_whatsapp_group_queue q SET status = 'CANCELLED', completed_at = now()
            FROM group_messages m
            WHERE m.id = q.message_id AND m.group_id = :groupId AND q.status = 'PENDING'
        """).param("groupId", groupId).update()
    }

    private fun scheduleRetry(job: Job, afterSeconds: Long = 60) {
        if (job.attempts + 1 >= 10) {
            finish(job.messageId, "FAILED")
            return
        }
        val delay = maxOf(afterSeconds, minOf(3600L, 60L shl job.attempts))
        jdbc.sql("""
            UPDATE notification_whatsapp_group_queue SET attempts = attempts + 1,
                next_attempt_at = now() + (:delay * interval '1 second') WHERE message_id = :id
        """).param("id", job.messageId).param("delay", delay).update()
    }

    private fun finish(messageId: UUID, status: String) {
        jdbc.sql("""
            UPDATE notification_whatsapp_group_queue SET status = :status, completed_at = now(),
                attempts = attempts + CASE WHEN :status = 'CANCELLED' THEN 0 ELSE 1 END WHERE message_id = :id
        """).param("id", messageId).param("status", status).update()
    }

    private data class Job(val messageId: UUID, val attempts: Int)
    private data class Binding(
        val groupId: UUID, val whatsappJid: String, val groupName: String, val enabled: Boolean, val brokenAt: Instant?,
    )
    private data class PendingMessage(
        val groupId: UUID, val channel: String, val gameId: UUID?, val body: String, val code: String?, val binding: Binding?,
        val reminderOrigin: String?, val updatesMode: String, val settleButton: WhatsAppGroupButton? = null,
    )
    /** [buttons] vazio é mensagem de texto; não-vazio vira botões, nunca embutidos no texto. */
    private data class GroupContent(val text: String, val buttons: List<WhatsAppGroupButton>, val image: String? = null)

    private companion object {
        const val CONFIRM_LABEL = "😍 Vou, me confirma!"
        const val DECLINE_LABEL = "😢 Não conseguirei ir!"
    }
}
