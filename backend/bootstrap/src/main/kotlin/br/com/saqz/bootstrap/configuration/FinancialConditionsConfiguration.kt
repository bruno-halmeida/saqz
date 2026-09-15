package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.adapter.input.http.FinancialConditionsController
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditions
import br.com.saqz.receivables.application.FinancialConditions
import br.com.saqz.receivables.application.FinancialFeeProvider
import br.com.saqz.receivables.application.FinancialOnboardingStore
import br.com.saqz.receivables.application.ProviderFinancialConditions
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasFees
import br.com.saqz.receivables.application.SimulateFinancialCharge
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditionsPublisher
import br.com.saqz.receivables.application.FinancialConditionsPublisher
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.adminweb.http.AdminReceivableConditionsController
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.env.Environment
import java.net.URI
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class FinancialConditionsConfiguration {
    @Bean fun financialConditionsPublisher(dataSource: DataSource): FinancialConditionsPublisher = JdbcFinancialConditionsPublisher(dataSource)
    @Bean fun financialFeeProvider(environment: Environment, onboarding: ObjectProvider<FinancialOnboardingStore>): FinancialFeeProvider =
        HttpAsaasFees(URI(environment.getProperty("saqz.receivables.asaas-base-url", "https://api-sandbox.asaas.com/v3")), { accountId ->
            if (accountId == null) environment.getProperty("saqz.receivables.asaas-platform-key")
            else onboarding.ifAvailable?.credentials(accountId)?.apiKey
        })
    @Bean fun adminReceivableConditionsController(admins: PlatformAdminLookup, publisher: FinancialConditionsPublisher,
        clock: Clock, provider: FinancialFeeProvider) = AdminReceivableConditionsController(admins, publisher, clock, provider)
    @Bean fun financialConditions(dataSource: DataSource, provider: FinancialFeeProvider): FinancialConditions =
        ProviderFinancialConditions(JdbcFinancialConditions(dataSource), provider)
    @Bean fun simulateFinancialCharge(conditions: FinancialConditions) = SimulateFinancialCharge(conditions)
    @Bean fun financialConditionsController(conditions: FinancialConditions, simulation: SimulateFinancialCharge, clock: Clock) =
        FinancialConditionsController(conditions, simulation, clock)
}
