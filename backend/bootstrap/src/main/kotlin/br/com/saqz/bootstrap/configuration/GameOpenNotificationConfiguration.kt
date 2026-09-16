package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.communication.GroupCommunicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Toque diário por push anunciando que o jogo está liberado para confirmação: um canal a mais
 * com o atleta, em cadência própria (1x/dia), separado do lembrete de WhatsApp.
 *
 * Nasce junto com os lembretes de presença — o mesmo liga/desliga do organizador — mas com o
 * horário em `saqz.notifications.game-open.cron`, então dá para mudar a frequência sem mexer
 * no intervalo do WhatsApp.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.reminder", name = ["enabled"], havingValue = "true")
class GameOpenNotificationConfiguration {
    @Bean fun dailyGameOpenNotification(communication: GroupCommunicationService) =
        DailyGameOpenNotification(communication)
}

class DailyGameOpenNotification(private val communication: GroupCommunicationService) {
    /** 14h de São Paulo, todos os dias. */
    @Scheduled(cron = "\${saqz.notifications.game-open.cron:0 0 14 * * *}", zone = "America/Sao_Paulo")
    fun run() {
        communication.announceOpenGames()
    }
}
