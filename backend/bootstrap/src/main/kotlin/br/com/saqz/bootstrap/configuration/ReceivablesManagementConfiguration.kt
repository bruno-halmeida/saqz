package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.input.http.FinancialManagementController
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasFinancialAccounts
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialManagement
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.net.URI
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("saqz.receivables.encryption-key")
class ReceivablesManagementConfiguration {
    @Bean fun financialManagementStore(dataSource: DataSource, secrets: FinancialSecrets) =
        JdbcFinancialManagement(dataSource, secrets)
    @Bean fun financialAccountsProvider(environment: Environment) = HttpAsaasFinancialAccounts(URI(
        environment.getProperty("saqz.receivables.asaas-base-url", "https://api-sandbox.asaas.com/v3")))
    @Bean fun manageFinancialRegistration(accounts: FinancialAccountRepository, groups: GroupAdministrationDirectory,
        onboarding: FinancialOnboardingStore, store: JdbcFinancialManagement, provider: HttpAsaasFinancialAccounts) =
        ManageFinancialRegistration(accounts, groups, onboarding, store, provider)
    @Bean fun financialManagementController(actors: SubscriptionActorResolver, service: ManageFinancialRegistration,
        clock: Clock) = FinancialManagementController(FinancialActorResolver { actors.resolve(it) }, service, clock)
}
