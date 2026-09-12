package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.adapter.input.http.FinancialConditionsController
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditions
import br.com.saqz.receivables.application.FinancialConditions
import br.com.saqz.receivables.application.SimulateFinancialCharge
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditionsPublisher
import br.com.saqz.receivables.application.FinancialConditionsPublisher
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.adminweb.http.AdminReceivableConditionsController
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class FinancialConditionsConfiguration {
    @Bean fun financialConditionsPublisher(dataSource: DataSource): FinancialConditionsPublisher = JdbcFinancialConditionsPublisher(dataSource)
    @Bean fun adminReceivableConditionsController(admins: PlatformAdminLookup, publisher: FinancialConditionsPublisher, clock: Clock) =
        AdminReceivableConditionsController(admins, publisher, clock)
    @Bean fun financialConditions(dataSource: DataSource): FinancialConditions = JdbcFinancialConditions(dataSource)
    @Bean fun simulateFinancialCharge(conditions: FinancialConditions) = SimulateFinancialCharge(conditions)
    @Bean fun financialConditionsController(conditions: FinancialConditions, simulation: SimulateFinancialCharge, clock: Clock) =
        FinancialConditionsController(conditions, simulation, clock)
}
