package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcGroupAdministrationDirectory
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialAccountRepository
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialDelegationStore
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.input.http.FinancialAccountDirectoryController
import br.com.saqz.receivables.adapter.input.http.FinancialDelegationsController
import br.com.saqz.receivables.application.ManageFinancialDelegations
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.sharedkernel.group.GroupAdministrationRevocation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

/** Revocation remains wired even if financial provider configuration is temporarily unavailable. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class FinancialDelegationConfiguration {
    @Bean
    fun groupAdministrationDirectory(dataSource: DataSource): GroupAdministrationDirectory = JdbcGroupAdministrationDirectory(dataSource)

    @Bean
    fun financialAccountRepository(dataSource: DataSource) = JdbcFinancialAccountRepository(dataSource)

    @Bean
    fun financialDelegationStore(dataSource: DataSource) = JdbcFinancialDelegationStore(dataSource)

    @Bean
    fun manageFinancialDelegations(accounts: JdbcFinancialAccountRepository, groups: GroupAdministrationDirectory,
                                    store: JdbcFinancialDelegationStore) = ManageFinancialDelegations(accounts, groups, store)

    @Bean
    fun financialDelegationsController(actors: SubscriptionActorResolver, service: ManageFinancialDelegations, clock: Clock) =
        FinancialDelegationsController(FinancialActorResolver { actors.resolve(it) }, service, clock)

    @Bean
    fun financialAccountDirectoryController(actors: SubscriptionActorResolver, service: ManageFinancialDelegations) =
        FinancialAccountDirectoryController(FinancialActorResolver { actors.resolve(it) }, service)

    @Bean
    fun financialAdministrationRevocation(groups: GroupAdministrationDirectory, accounts: JdbcFinancialAccountRepository,
                                         clock: Clock) = GroupAdministrationRevocation { groupId, userId ->
        groups.ownerOf(groupId)?.let { owner -> accounts.revokeForOwner(owner, userId, clock.instant()) }
    }
}
