package br.com.saqz.bootstrap.configuration

import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionPastDueRepository
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.SubscriptionPastDueReminders
import br.com.saqz.subscriptions.application.SubscriptionPastDueRepository
import br.com.saqz.subscriptions.application.TrialEndingWhatsApp
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import javax.sql.DataSource

/**
 * Aviso diário de pagamento atrasado (vencimento, 3 dias depois e último dia da carência). Nasce junto com os avisos de fim do teste
 * (o mesmo liga/desliga) e usa os beans de aviso e de WhatsApp deles, com o horário em `saqz.notifications.past-due.cron`.
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
        whatsApp: TrialEndingWhatsApp,
        gateway: ObjectProvider<AsaasGateway>,
        clock: Clock,
    ) = DailySubscriptionPastDueReminders(
        SubscriptionPastDueReminders(repository, notice, whatsApp, { gateway.ifAvailable }, clock),
    )
}

class DailySubscriptionPastDueReminders(private val reminders: SubscriptionPastDueReminders) {
    /** 10h de São Paulo, todos os dias. */
    @Scheduled(cron = "\${saqz.notifications.past-due.cron:0 0 10 * * *}", zone = "America/Sao_Paulo")
    fun run() {
        JobLog.cron("subscription_past_due", { reminders.run() }) { mapOf("sent" to it) }
    }
}
