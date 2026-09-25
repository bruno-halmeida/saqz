package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.communication.GroupCommunicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Janela de presença das 24 h (Live Activity no iPhone, Live Update no Android). Desligada por
 * padrão: liga junto com o app nas lojas, depois do roteiro em aparelho (VUL-270).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.attendance-window", name = ["enabled"], havingValue = "true")
class AttendanceWindowConfiguration {
    @Bean fun attendanceWindowJob(communication: GroupCommunicationService) = AttendanceWindowJob(communication)
}

class AttendanceWindowJob(private val communication: GroupCommunicationService) {
    /** A cada minuto: a janela dura 2 h, então abrir com até 1 min de atraso basta. */
    @Scheduled(fixedDelayString = "\${saqz.notifications.attendance-window.delay-ms:60000}")
    fun run() {
        communication.openAttendanceWindows()
    }
}
