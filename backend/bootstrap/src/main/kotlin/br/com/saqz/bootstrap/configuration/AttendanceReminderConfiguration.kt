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
 * A lista de convocados vai 2 vezes por dia, às 9h e às 18h de São Paulo (DEB-066), do jogo publicado até
 * o início dele. O horário vem de `saqz.notifications.reminder.cron` (cron do Spring, seis
 * campos). A chave antiga `SAQZ_NOTIFICATIONS_REMINDER_DELAY_MS` (fixedDelay de 7 h) deixou de valer: quem a
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
}
