package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.plan.JdbcOwnerGroupHistory
import br.com.saqz.sharedkernel.subscription.GroupCreationTrial
import br.com.saqz.sharedkernel.subscription.OwnerGroupHistory
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcOrganizerTrialRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcPaidSubscriptionHistory
import br.com.saqz.subscriptions.application.OrganizerTrialRepository
import br.com.saqz.subscriptions.application.PaidSubscriptionHistory
import br.com.saqz.subscriptions.application.StartOrganizerTrial
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class OrganizerTrialConfiguration {
    @Bean
    fun organizerTrialRepository(dataSource: DataSource): OrganizerTrialRepository = JdbcOrganizerTrialRepository(dataSource)

    @Bean
    fun ownerGroupHistory(dataSource: DataSource): OwnerGroupHistory = JdbcOwnerGroupHistory(dataSource)

    @Bean
    fun paidSubscriptionHistory(dataSource: DataSource): PaidSubscriptionHistory = JdbcPaidSubscriptionHistory(dataSource)

    @Bean
    fun groupCreationTrial(trials: OrganizerTrialRepository, history: OwnerGroupHistory, paid: PaidSubscriptionHistory, clock: Clock): GroupCreationTrial =
        StartOrganizerTrial(trials, history, paid, clock)
}
