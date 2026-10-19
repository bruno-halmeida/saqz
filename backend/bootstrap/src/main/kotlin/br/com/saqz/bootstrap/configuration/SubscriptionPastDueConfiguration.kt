package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionPastDueRepository
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.SubscriptionPastDueReminders
import br.com.saqz.subscriptions.application.SubscriptionPastDueRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import javax.sql.DataSource

/**
 * Aviso diário de pagamento atrasado (vencimento, 3 dias depois e último dia da carência). Nasce junto com os avisos de fim do teste
 * (o mesmo liga/desliga) e usa o bean de aviso dele e o mesmo WhatsApp particular, com o horário em `saqz.notifications.past-due.cron`.
 *
 * O Asaas é opcional: só existe com o token do webhook configurado, e sem ele o aviso sai pelo link do webhook guardado
 * (ou sem link). O job não pode impedir o backend de subir na pilha local, onde o token vem vazio.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.reminder", name = ["enabled"], havingValue = "true")
class SubscriptionPastDueConfiguration {
    @Bean fun subscriptionPastDueRepository(dataSource: DataSource): SubscriptionPastDueRepository =
        JdbcSubscriptionPastDueRepository(dataSource)

    @Bean fun dailySubscriptionPastDueReminders(
        repository: SubscriptionPastDueRepository,
        notice: TrialEndingNotice,
        dataSource: DataSource,
        sender: ObjectProvider<NotificationWhatsAppSender>,
        gateway: ObjectProvider<AsaasGateway>,
        clock: Clock,
    ) = DailySubscriptionPastDueReminders(
        // Não é o bean do fim do teste: a falha de envio do atraso entra no log com o rótulo do próprio aviso.
        SubscriptionPastDueReminders(
            repository, notice, PrivateTrialEndingWhatsApp(JdbcClient.create(dataSource), { sender.ifAvailable }, "subscription_past_due"),
            { gateway.ifAvailable }, clock,
        ),
    )
}

class DailySubscriptionPastDueReminders(private val reminders: SubscriptionPastDueReminders) {
    /** 10h de São Paulo, todos os dias. */
    @Scheduled(cron = "\${saqz.notifications.past-due.cron:0 0 10 * * *}", zone = "America/Sao_Paulo")
    fun run() {
        JobLog.cron("subscription_past_due", { reminders.run() }) { mapOf("sent" to it) }
    }
}
