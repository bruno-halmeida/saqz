package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.flags.FeatureFlags
import br.com.saqz.access.application.flags.FeatureKeys
import br.com.saqz.groups.adapter.input.http.WhatsAppAvailabilityController
import br.com.saqz.groups.adapter.input.http.WhatsAppNumberCheckController
import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import br.com.saqz.groups.application.ratelimit.PerUserRateLimiter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Sempre registrado, ao contrário do [NotificationWhatsAppConfiguration], que só existe com o
 * WhatsApp ligado: é aqui que o app descobre se mostra o que depende dele.
 */
@Configuration(proxyBeanMethods = false)
class WhatsAppAvailabilityConfiguration {
    /**
     * Ligado = integração no servidor (a propriedade) **e** flag `whatsapp` ligada para quem pergunta
     * (DEB-119). Sem banco (testes de endpoint) não há flags e vale só a propriedade.
     */
    @Bean
    fun whatsAppAvailabilityController(
        @Value("\${saqz.notifications.whatsapp.enabled:false}") enabled: Boolean,
        features: ObjectProvider<FeatureFlags>,
    ) = WhatsAppAvailabilityController { subject, client ->
        enabled && features.getIfAvailable()?.let { flags ->
            flags.enabledFor(FeatureKeys.WHATSAPP, subject?.let(flags::userIdBySubject), client)
        } ?: enabled
    }

    /** O verificador só existe com o WhatsApp ligado; sem ele a rota responde "sem resposta". */
    @Bean
    fun whatsAppNumberCheckController(check: ObjectProvider<WhatsAppNumberCheck>, clock: ObjectProvider<Clock>) =
        WhatsAppNumberCheckController(
            check.ifAvailable,
            PerUserRateLimiter(clock.getIfAvailable { Clock.systemUTC() }, WhatsAppNumberCheckController.CHECKS_PER_MINUTE),
        )
}
