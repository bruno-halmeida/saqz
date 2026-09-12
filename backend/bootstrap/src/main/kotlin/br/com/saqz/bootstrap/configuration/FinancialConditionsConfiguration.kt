package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.adapter.input.http.FinancialConditionsController
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialConditions
import br.com.saqz.receivables.application.FinancialConditions
import br.com.saqz.receivables.application.SimulateFinancialCharge
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class FinancialConditionsConfiguration {
    @Bean fun financialConditions(dataSource: DataSource): FinancialConditions = JdbcFinancialConditions(dataSource)
    @Bean fun simulateFinancialCharge(conditions: FinancialConditions) = SimulateFinancialCharge(conditions)
    @Bean fun financialConditionsController(conditions: FinancialConditions, simulation: SimulateFinancialCharge, clock: Clock) =
        FinancialConditionsController(conditions, simulation, clock)
}
