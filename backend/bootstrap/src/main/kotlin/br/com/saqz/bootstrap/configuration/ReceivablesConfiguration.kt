package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.adapter.input.http.FinancialAccountsController
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasOnboarding
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialOnboardingStore
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialOperationStore
import br.com.saqz.receivables.application.OnboardFinancialAccount
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.net.URI
import java.time.Clock
import java.util.Base64
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("saqz.receivables.encryption-key")
class ReceivablesConfiguration {
    @Bean
    fun financialSecrets(environment: Environment): FinancialSecrets {
        val keyId = environment.getRequiredProperty("saqz.receivables.encryption-key-id")
        return FinancialSecrets(keyId, mapOf(keyId to Base64.getDecoder().decode(
            environment.getRequiredProperty("saqz.receivables.encryption-key"))), Base64.getDecoder().decode(
            environment.getRequiredProperty("saqz.receivables.identity-lookup-key")))
    }

    @Bean
    fun financialOnboardingStore(dataSource: DataSource, secrets: FinancialSecrets) = JdbcFinancialOnboardingStore(dataSource, secrets)

    @Bean
    fun financialOperationStore(dataSource: DataSource) = JdbcFinancialOperationStore(dataSource)

    @Bean
    fun financialOnboardingProvider(environment: Environment) = HttpAsaasOnboarding(
        URI(environment.getProperty("saqz.receivables.asaas-base-url", "https://api-sandbox.asaas.com/v3")),
        environment.getRequiredProperty("saqz.receivables.asaas-platform-key"),
        environment.getProperty("saqz.receivables.baas-enabled", Boolean::class.java, false),
    )

    @Bean
    fun financialOnboarding(store: JdbcFinancialOnboardingStore, operations: JdbcFinancialOperationStore,
                            provider: HttpAsaasOnboarding, clock: Clock) = OnboardFinancialAccount(store, operations, provider, clock)

    @Bean
    fun financialAccountsController(actors: SubscriptionActorResolver, onboarding: OnboardFinancialAccount,
                                     store: JdbcFinancialOnboardingStore, clock: Clock) =
        FinancialAccountsController(FinancialActorResolver { actors.resolve(it) }, onboarding, store, clock)
}
