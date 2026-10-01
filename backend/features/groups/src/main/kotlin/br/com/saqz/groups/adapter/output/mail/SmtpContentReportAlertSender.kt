package br.com.saqz.groups.adapter.output.mail

import br.com.saqz.groups.application.moderation.ContentReportAlert
import br.com.saqz.groups.application.moderation.ContentReportAlertSender
import br.com.saqz.groups.application.moderation.ReportReason
import br.com.saqz.groups.application.moderation.ReportTarget
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** E-mail interno para a equipe: texto puro, com tudo o que precisa para agir sem abrir o banco. */
class SmtpContentReportAlertSender(
    private val sender: JavaMailSender,
    private val from: String,
    private val recipient: String,
) : ContentReportAlertSender {
    override fun send(alert: ContentReportAlert) {
        val mime = sender.createMimeMessage()
        val helper = MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name())
        helper.setFrom(from)
        helper.setTo(recipient)
        helper.setSubject(subject(alert))
        helper.setText(body(alert))
        sender.send(mime)
    }

    internal companion object {
        private val TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"))

        fun subject(alert: ContentReportAlert): String {
            val kind = if (alert.reason == ReportReason.BLOCKED) "Bloqueio" else "Denúncia"
            return "[Saqz] $kind: ${reason(alert.reason)} · ${target(alert.target)} em \"${alert.groupName}\""
        }

        fun body(alert: ContentReportAlert): String = buildString {
            appendLine(
                if (alert.reason == ReportReason.BLOCKED) "Uma pessoa bloqueou outra no Saqz. Analise em até 24 horas."
                else "Nova denúncia no Saqz. Analise e responda em até 24 horas.",
            )
            appendLine()
            appendLine("Denúncia: ${alert.id}")
            appendLine("Recebida em: ${TIME.format(alert.createdAt)} (horário de Brasília)")
            appendLine("Motivo: ${reason(alert.reason)}")
            appendLine("Alvo: ${target(alert.target)} (${alert.targetId})")
            appendLine("Grupo: ${alert.groupName} (${alert.groupId})")
            appendLine("Responsável pelo conteúdo: ${alert.responsibleName ?: "—"} (${alert.responsibleUserId ?: "—"})")
            appendLine("Denunciado por: ${alert.reporterName} (${alert.reporterId})")
            appendLine()
            appendLine("Conteúdo denunciado:")
            appendLine(alert.excerpt ?: "(sem conteúdo)")
            appendLine()
            appendLine("Detalhes de quem denunciou:")
            appendLine(alert.details ?: "(sem detalhes)")
            appendLine()
            appendLine("Como agir (API do painel, autenticado como admin):")
            if (alert.target == ReportTarget.MESSAGE) {
                appendLine("- Remover o aviso: POST /admin/groups/${alert.groupId}/messages/${alert.targetId}/remove")
            }
            alert.responsibleUserId?.let { appendLine("- Suspender a conta responsável: POST /admin/users/$it/suspend") }
            appendLine("- Marcar como resolvida: POST /admin/reports/${alert.id}/resolve")
        }

        fun reason(reason: ReportReason) = when (reason) {
            ReportReason.SPAM -> "Spam ou propaganda"
            ReportReason.OFFENSIVE -> "Conteúdo ofensivo ou impróprio"
            ReportReason.HARASSMENT -> "Assédio ou ameaça"
            ReportReason.OTHER -> "Outro motivo"
            ReportReason.BLOCKED -> "Pessoa bloqueada"
        }

        fun target(target: ReportTarget) = when (target) {
            ReportTarget.USER -> "pessoa"
            ReportTarget.GROUP -> "grupo"
            ReportTarget.MESSAGE -> "aviso"
        }
    }
}
