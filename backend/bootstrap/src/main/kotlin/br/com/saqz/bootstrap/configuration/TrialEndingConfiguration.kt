package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcTrialEndingNotice
import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppNotification
import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import br.com.saqz.sharedkernel.subscription.TrialUsageLookup
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcTrialEndingRepository
import br.com.saqz.subscriptions.application.SubscriptionPlanLookup
import br.com.saqz.subscriptions.application.TrialEndingReminders
import br.com.saqz.subscriptions.application.TrialEndingRepository
import br.com.saqz.subscriptions.application.TrialEndingWhatsApp
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

/**
 * Aviso diário de fim do teste do Organizador (3 dias, 1 dia e no dia do fim). Nasce junto com os lembretes de
 * presença — o mesmo liga/desliga do organizador —, com o horário em `saqz.notifications.trial-ending.cron`.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.reminder", name = ["enabled"], havingValue = "true")
class TrialEndingConfiguration {
    @Bean fun trialEndingRepository(dataSource: DataSource): TrialEndingRepository = JdbcTrialEndingRepository(dataSource)

    @Bean fun trialEndingNotice(dataSource: DataSource): TrialEndingNotice = JdbcTrialEndingNotice(dataSource)

    @Bean fun trialEndingWhatsApp(dataSource: DataSource, sender: ObjectProvider<NotificationWhatsAppSender>): TrialEndingWhatsApp =
        PrivateTrialEndingWhatsApp(JdbcClient.create(dataSource)) { sender.ifAvailable }

    @Bean fun dailyTrialEndingReminders(
        trials: TrialEndingRepository,
        paid: SubscriptionPlanLookup,
        usage: TrialUsageLookup,
        notice: TrialEndingNotice,
        whatsApp: TrialEndingWhatsApp,
        clock: Clock,
    ) = DailyTrialEndingReminders(TrialEndingReminders(trials, paid, usage, notice, whatsApp, clock))
}

class DailyTrialEndingReminders(private val reminders: TrialEndingReminders) {
    /** 10h de São Paulo, todos os dias. */
    @Scheduled(cron = "\${saqz.notifications.trial-ending.cron:0 0 10 * * *}", zone = "America/Sao_Paulo")
    fun run() {
        val sent = reminders.run()
        if (sent > 0) log.info("trial_ending_reminders_sent count={}", sent)
    }

    private companion object {
        val log = LoggerFactory.getLogger(DailyTrialEndingReminders::class.java)
    }
}

/**
 * WhatsApp particular do dono, direto pela Uazapi das notificações (como a confirmação de conta): sem fila, e só
 * se o WhatsApp estiver ligado no servidor e a pessoa tiver telefone. Falha de envio só entra no log.
 */
class PrivateTrialEndingWhatsApp(
    private val jdbc: JdbcClient,
    private val sender: () -> NotificationWhatsAppSender?,
) : TrialEndingWhatsApp {
    override fun send(ownerUserId: UUID, body: String) {
        val whatsApp = sender() ?: return
        val phone = jdbc.sql("SELECT phone FROM access_users WHERE id = :id").param("id", ownerUserId)
            .query(String::class.java).optional().orElse(null)?.takeIf { it.isNotBlank() } ?: return
        val delivery = whatsApp.send(WhatsAppNotification(notificationId = 0, phone = phone, body = "Saqz: $body"))
        if (delivery != br.com.saqz.groups.application.communication.WhatsAppDelivery.Accepted) {
            LoggerFactory.getLogger(PrivateTrialEndingWhatsApp::class.java).warn("trial_ending_whatsapp_failed delivery={}", delivery)
        }
    }
}
