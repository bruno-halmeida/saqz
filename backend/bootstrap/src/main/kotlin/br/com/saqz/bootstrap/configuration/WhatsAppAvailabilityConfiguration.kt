package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.WhatsAppAvailabilityController
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Sempre registrado, ao contrário do [NotificationWhatsAppConfiguration], que só existe com o
 * WhatsApp ligado: é aqui que o app descobre se mostra o que depende dele.
 */
@Configuration(proxyBeanMethods = false)
class WhatsAppAvailabilityConfiguration {
    @Bean
    fun whatsAppAvailabilityController(
        @Value("\${saqz.notifications.whatsapp.enabled:false}") enabled: Boolean,
    ) = WhatsAppAvailabilityController(enabled)
}
