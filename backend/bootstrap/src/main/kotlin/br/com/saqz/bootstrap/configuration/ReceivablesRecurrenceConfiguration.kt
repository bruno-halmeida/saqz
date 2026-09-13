package br.com.saqz.bootstrap.configuration

import br.com.saqz.receivables.adapter.input.http.RecurrencePaymentsController
import br.com.saqz.receivables.adapter.input.http.RecurrencePixRenewalController
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasRecurrenceProvider
import br.com.saqz.receivables.adapter.output.jdbc.JdbcPaymentExecution
import br.com.saqz.receivables.adapter.output.jdbc.JdbcPaymentProviderCredentials
import br.com.saqz.receivables.adapter.output.jdbc.JdbcRecurrenceExecution
import br.com.saqz.receivables.adapter.output.jdbc.JdbcRecurrenceStore
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.application.*
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import java.net.URI
import java.time.Clock
import java.time.ZoneId
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("saqz.receivables.payments-enabled", havingValue = "true")
class ReceivablesRecurrenceConfiguration {
    @Bean fun recurrenceStore(dataSource: DataSource, secrets: FinancialSecrets) = JdbcRecurrenceStore(dataSource, secrets)
    @Bean fun recurrenceProvider(environment: Environment, credentials: JdbcPaymentProviderCredentials) =
        HttpAsaasRecurrenceProvider(URI(environment.getRequiredProperty("saqz.receivables.asaas-base-url")),
            environment.getRequiredProperty("saqz.receivables.platform-wallet-id"),
            URI(environment.getRequiredProperty("saqz.receivables.checkout-callback-base-url")), credentials)
    @Bean fun recurrenceExecution(store: JdbcRecurrenceStore, provider: HttpAsaasRecurrenceProvider,
        payments: JdbcPaymentExecution, accounts: FinancialAccountRepository,
        eligibility: ReceivablesEligibility, clock: Clock, environment: Environment) =
        JdbcRecurrenceExecution(store, provider, payments, accounts, eligibility, clock,
            ZoneId.of(environment.getProperty("saqz.finance.monthly-charges.zone", "America/Sao_Paulo")))
    @Bean fun recurrencePayments(store: JdbcRecurrenceStore, accounts: FinancialAccountRepository,
        groups: GroupReceivablesStore, conditions: FinancialConditions, eligibility: ReceivablesEligibility,
        rollout: ReceivablesRollout, execution: JdbcRecurrenceExecution, clock: Clock) =
        RecurrencePayments(store, accounts, groups, conditions, eligibility, rollout, execution, clock)
    @Bean fun recurrencePaymentsController(actors: SubscriptionActorResolver, service: RecurrencePayments) =
        RecurrencePaymentsController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun recurrencePixRenewalController(actors: SubscriptionActorResolver, payments: OneOffPayments) =
        RecurrencePixRenewalController(FinancialActorResolver { actors.resolve(it) }, payments)
    @Bean fun recurrenceJobs(execution: JdbcRecurrenceExecution, payments: JdbcPaymentExecution) =
        ReceivablesRecurrenceJobs(execution, payments)
}

class ReceivablesRecurrenceJobs(private val execution: RecurrenceExecution,
    private val payments: JdbcPaymentExecution) {
    @Scheduled(fixedDelayString = "\${saqz.receivables.recurrence-recovery-delay-ms:60000}")
    fun recover() { execution.recoverDue(); payments.recoverPixRenewals() }
    @Scheduled(fixedDelayString = "\${saqz.receivables.recurrence-sync-delay-ms:300000}")
    fun synchronize() { execution.synchronize() }
    @Scheduled(fixedDelayString = "\${saqz.receivables.recurrence-cutoff-delay-ms:60000}")
    fun enforceCutoffs() { execution.enforceCutoffs() }
}
