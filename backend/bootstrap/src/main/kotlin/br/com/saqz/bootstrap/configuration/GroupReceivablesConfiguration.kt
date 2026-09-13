package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.group.JdbcGroupFinancialSetupLookup
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.input.http.GroupReceivablesController
import br.com.saqz.receivables.adapter.output.jdbc.JdbcGroupReceivablesStore
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.sharedkernel.group.GroupFinancialSetupLookup
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class GroupReceivablesConfiguration {
    @Bean fun groupFinancialSetupLookup(dataSource: DataSource): GroupFinancialSetupLookup = JdbcGroupFinancialSetupLookup(dataSource)
    @Bean fun groupReceivablesStore(dataSource: DataSource): GroupReceivablesStore = JdbcGroupReceivablesStore(dataSource)
    @Bean fun manageGroupReceivables(accounts: FinancialAccountRepository, admins: GroupAdministrationDirectory,
        groups: GroupFinancialSetupLookup, store: GroupReceivablesStore, conditions: FinancialConditions,
        eligibility: ReceivablesEligibility, clock: Clock, rollout: ReceivablesRollout) = ManageGroupReceivables(accounts, admins, groups, store, conditions, eligibility, clock, rollout)
    @Bean fun groupReceivablesController(actors: SubscriptionActorResolver, service: ManageGroupReceivables) =
        GroupReceivablesController(FinancialActorResolver { actors.resolve(it) }, service)
}
