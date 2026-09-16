package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.communication.GroupCommunicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.reminder", name = ["enabled"], havingValue = "true")
class AttendanceReminderConfiguration {
    @Bean fun automaticAttendanceReminder(reminders: GroupCommunicationService) =
        AutomaticAttendanceReminder(reminders)
}

class AutomaticAttendanceReminder(private val reminders: GroupCommunicationService) {
    @Scheduled(fixedDelayString = "\${saqz.notifications.reminder.delay-ms:60000}")
    fun run() {
        reminders.remindAutomatically()
    }
}
