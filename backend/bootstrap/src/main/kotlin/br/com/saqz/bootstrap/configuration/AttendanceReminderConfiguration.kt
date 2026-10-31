package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.reminder", name = ["enabled"], havingValue = "true")
class AttendanceReminderConfiguration {
    @Bean fun automaticAttendanceReminder(reminders: GroupCommunicationService) = AutomaticAttendanceReminder(reminders)
}

/**
 * A lista de convocados vai 3 vezes por dia, às 9h, às 12h30 e às 18h de São Paulo (DEB-066), do jogo publicado
 * até o início dele. 9h e 18h vêm de `saqz.notifications.reminder.cron` e 12h30 de
 * `saqz.notifications.reminder.midday-cron` (cron do Spring, seis campos; um cron só não junta minutos diferentes).
 * As duas execuções são o mesmo lembrete: lista no grupo do WhatsApp e push só para quem ainda não respondeu. A chave antiga `SAQZ_NOTIFICATIONS_REMINDER_DELAY_MS` (fixedDelay de 7 h) deixou de valer: quem a
 * define em produção deve apagá-la e, para outro horário, definir `SAQZ_NOTIFICATIONS_REMINDER_CRON`.
 */
class AutomaticAttendanceReminder(private val reminders: GroupCommunicationService) {
    @Scheduled(
        cron = "\${saqz.notifications.reminder.cron:0 0 9,18 * * *}",
        zone = "America/Sao_Paulo",
    )
    fun run() {
        JobLog.cron("attendance_reminder", { reminders.remindAutomatically() }) { mapOf("published" to it) }
    }

    @Scheduled(
        cron = "\${saqz.notifications.reminder.midday-cron:0 30 12 * * *}",
        zone = "America/Sao_Paulo",
    )
    fun runMidday() = run()

    /**
     * Grupos no modo "a cada confirmação": a cada 5 min a lista do próximo jogo é comparada com a última publicada e,
     * se mudou, vai ao grupo do WhatsApp (freio de 15 min e 12 por dia no [GroupCommunicationService.remindOnChanges]). O dia
     * que chega às 9h sem lista ganha uma mesmo sem mudança, a partir de 4 dias antes do jogo.
     */
    @Scheduled(
        cron = "\${saqz.notifications.reminder.on-change-cron:0 */5 * * * *}",
        zone = "America/Sao_Paulo",
    )
    fun runOnChanges() {
        JobLog.cron("attendance_updates", { reminders.remindOnChanges() }) { mapOf("published" to it) }
    }
}
