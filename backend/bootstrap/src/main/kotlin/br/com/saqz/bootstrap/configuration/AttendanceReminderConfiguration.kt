package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.attendance.AutoConfirmAttendance
import br.com.saqz.groups.application.communication.GroupCommunicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.reminder", name = ["enabled"], havingValue = "true")
class AttendanceReminderConfiguration {
    @Bean fun automaticAttendanceReminder(
        autoConfirm: AutoConfirmAttendance,
        reminders: GroupCommunicationService,
    ) = AutomaticAttendanceReminder(autoConfirm, reminders)
}

/**
 * A lista de convocados vai a cada 3 h, das 8h às 22h em São Paulo (8h, 11h, 14h, 17h e 20h), do jogo
 * publicado até o início dele. O horário vem de `saqz.notifications.reminder.cron` (cron do Spring, seis
 * campos). A chave antiga `SAQZ_NOTIFICATIONS_REMINDER_DELAY_MS` (fixedDelay de 7 h) deixou de valer: quem a
 * define em produção deve apagá-la e, para outro horário, definir `SAQZ_NOTIFICATIONS_REMINDER_CRON`.
 */
class AutomaticAttendanceReminder(
    private val autoConfirm: AutoConfirmAttendance,
    private val reminders: GroupCommunicationService,
) {
    @Scheduled(
        cron = "\${saqz.notifications.reminder.cron:0 0 8,11,14,17,20 * * *}",
        zone = "America/Sao_Paulo",
    )
    fun run() {
        autoConfirm.applyOpenGames()
        reminders.remindAutomatically()
    }
}
