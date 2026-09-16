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

class AutomaticAttendanceReminder(
    private val autoConfirm: AutoConfirmAttendance,
    private val reminders: GroupCommunicationService,
) {
    @Scheduled(fixedDelayString = "\${saqz.notifications.reminder.delay-ms:60000}")
    fun run() {
        autoConfirm.applyOpenGames()
        reminders.remindAutomatically()
    }
}
