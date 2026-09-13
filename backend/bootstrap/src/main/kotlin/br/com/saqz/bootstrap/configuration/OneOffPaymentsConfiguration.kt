package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcGroupChargePayments
import br.com.saqz.receivables.adapter.input.http.*
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasPayments
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import br.com.saqz.sharedkernel.group.GroupChargePayments
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import java.net.URI
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("saqz.receivables.payments-enabled", havingValue = "true")
class OneOffPaymentsConfiguration {
    @Bean fun payableGroupCharges(dataSource: DataSource): GroupChargePayments = JdbcGroupChargePayments(dataSource)
    @Bean fun paymentStore(dataSource: DataSource, secrets: FinancialSecrets) = JdbcPaymentStore(dataSource, secrets)
    @Bean fun paymentProviderCredentials(dataSource: DataSource, secrets: FinancialSecrets) = JdbcPaymentProviderCredentials(dataSource, secrets)
    @Bean fun oneOffPaymentProvider(environment: Environment, credentials: JdbcPaymentProviderCredentials): HttpAsaasPayments =
        HttpAsaasPayments(URI(environment.getRequiredProperty("saqz.receivables.asaas-base-url")),
            environment.getRequiredProperty("saqz.receivables.platform-wallet-id"), credentials,
            environment.getRequiredProperty("saqz.receivables.webhook-base-url"), environment.getRequiredProperty("saqz.receivables.webhook-email"))
    @Bean fun paymentWebhookRegistration(dataSource: DataSource, store: JdbcPaymentStore, secrets: FinancialSecrets,
        provider: HttpAsaasPayments, clock: Clock) = JdbcPaymentWebhookRegistration(dataSource, store, secrets, provider, clock)
    @Bean fun paymentWebhookSetupController(actors: SubscriptionActorResolver, registration: JdbcPaymentWebhookRegistration) =
        PaymentWebhookSetupController(FinancialActorResolver { actors.resolve(it) }, registration)
    @Bean fun paymentExecution(dataSource: DataSource, store: JdbcPaymentStore, charges: GroupChargePayments,
        operations: JdbcFinancialOperationStore, provider: OneOffPaymentProvider, secrets: FinancialSecrets, clock: Clock,
        reconciler: ReconcileExternalResidualCost) =
        JdbcPaymentExecution(dataSource, store, charges, operations, provider, secrets, clock, reconciler)
    @Bean fun oneOffPayments(store: JdbcPaymentStore, charges: GroupChargePayments, accounts: FinancialAccountRepository,
        groups: GroupReceivablesStore, admins: GroupAdministrationDirectory, conditions: FinancialConditions,
        eligibility: ReceivablesEligibility, rollout: ReceivablesRollout, execution: JdbcPaymentExecution, clock: Clock) =
        OneOffPayments(store, charges, accounts, groups, admins, conditions, eligibility, rollout, execution, clock)
    @Bean fun oneOffPaymentsController(actors: SubscriptionActorResolver, service: OneOffPayments) =
        OneOffPaymentsController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun paymentEvents(dataSource: DataSource, secrets: FinancialSecrets, execution: JdbcPaymentExecution, clock: Clock) =
        JdbcPaymentEvents(dataSource, secrets, execution, clock)
    @Bean fun paymentWebhookController(events: JdbcPaymentEvents) = PaymentWebhookController(events)
    @Bean fun paymentRecovery(events: JdbcPaymentEvents) = PaymentRecovery(events)
}

class PaymentRecovery(private val events: JdbcPaymentEvents) {
    @Scheduled(fixedDelayString = "\${saqz.receivables.recovery-delay-ms:60000}")
    fun recover() { events.processPending(); events.recoverPending() }
}
