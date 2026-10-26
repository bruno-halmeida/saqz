package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcGroupChargeLedger
import br.com.saqz.receivables.adapter.input.http.AthletePaymentsController
import br.com.saqz.receivables.adapter.input.http.PaymentReviewsController
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcReceivablesNotices
import br.com.saqz.sharedkernel.group.ReceivablesNotices
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasPaymentCodes
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.group.GroupChargeLedger
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import java.net.URI
import java.time.Clock
import javax.sql.DataSource

/** Recebimento pelo app v1 (DEB-140): código do atleta, baixa automática, revisão, comissão e caixa. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("saqz.receivables.payments-enabled", havingValue = "true")
class ReceivablesV1Configuration {
    @Bean fun groupChargeLedger(dataSource: DataSource): GroupChargeLedger = JdbcGroupChargeLedger(dataSource)
    @Bean fun paymentCodeStore(dataSource: DataSource, secrets: FinancialSecrets) = JdbcPaymentCodeStore(dataSource, secrets)
    @Bean fun paymentCodeProvider(environment: Environment, credentials: JdbcPaymentProviderCredentials) =
        HttpAsaasPaymentCodes(URI(environment.getRequiredProperty("saqz.receivables.asaas-base-url")),
            environment.getRequiredProperty("saqz.receivables.platform-wallet-id"), credentials)
    @Bean fun pixKeys(dataSource: DataSource, provider: HttpAsaasPaymentCodes) = JdbcPixKeys(dataSource, provider)
    @Bean fun athletePayments(store: JdbcPaymentCodeStore, ledger: GroupChargeLedger, conditions: FinancialConditions,
        eligibility: ReceivablesEligibility, rollout: ReceivablesRollout, provider: HttpAsaasPaymentCodes, clock: Clock) =
        AthletePayments(store, ledger, conditions, eligibility, rollout, provider, clock)
    @Bean fun athletePaymentsController(actors: SubscriptionActorResolver, service: AthletePayments) =
        AthletePaymentsController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun receivablesNotices(dataSource: DataSource): ReceivablesNotices = JdbcReceivablesNotices(dataSource)
    @Bean fun commissionTransfers(dataSource: DataSource, operations: JdbcFinancialOperationStore, provider: HttpAsaasPaymentCodes,
        clock: Clock) = JdbcCommissionTransfers(dataSource, operations, provider, clock)
    @Bean fun paymentSettlement(dataSource: DataSource, codes: JdbcPaymentCodeStore, ledger: GroupChargeLedger,
        notices: ReceivablesNotices, provider: HttpAsaasPaymentCodes, commissions: JdbcCommissionTransfers, clock: Clock) =
        PaymentSettlement(JdbcSettlementStore(dataSource), codes, ledger, notices, provider, commissions, clock)
    @Bean fun paymentReviews(dataSource: DataSource, ledger: GroupChargeLedger, clock: Clock) =
        PaymentReviews(JdbcReviewStore(dataSource), ledger, clock)
    @Bean fun paymentReviewsController(actors: SubscriptionActorResolver, service: PaymentReviews) =
        PaymentReviewsController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun receivablesV1Jobs(pixKeys: JdbcPixKeys, commissions: JdbcCommissionTransfers) = ReceivablesV1Jobs(pixKeys, commissions)
}

class ReceivablesV1Jobs(private val pixKeys: JdbcPixKeys, private val commissions: JdbcCommissionTransfers) {
    @Scheduled(fixedDelayString = "\${saqz.receivables.v1-recovery-delay-ms:60000}")
    fun recover() { pixKeys.recoverPending(); commissions.recoverPending() }
}
