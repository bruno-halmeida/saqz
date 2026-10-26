package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcGroupChargeLedger
import br.com.saqz.receivables.adapter.input.http.AthletePaymentsController
import br.com.saqz.receivables.adapter.input.http.PaymentReviewsController
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcReceivablesNotices
import br.com.saqz.sharedkernel.group.ReceivablesNotices
import br.com.saqz.sharedkernel.job.JobLog
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
            environment.getRequiredProperty("saqz.receivables.platform-wallet-id"), credentials,
            environment.getProperty("saqz.receivables.checkout-callback-base-url")?.let(::URI))
    @Bean fun pixKeys(dataSource: DataSource, provider: HttpAsaasPaymentCodes) = JdbcPixKeys(dataSource, provider)
    @Bean fun athletePayments(store: JdbcPaymentCodeStore, ledger: GroupChargeLedger, conditions: FinancialConditions,
        eligibility: ReceivablesEligibility, rollout: ReceivablesRollout, provider: HttpAsaasPaymentCodes, clock: Clock,
        cards: org.springframework.beans.factory.ObjectProvider<CardSubscriptions>) =
        AthletePayments(store, ledger, conditions, eligibility, rollout, provider, clock,
            recurrence = { group, member -> cards.ifAvailable?.view(group, member) })
    @Bean fun settleLinks(dataSource: DataSource, environment: Environment, clock: Clock) =
        JdbcSettleLinks(dataSource, URI(environment.getRequiredProperty("saqz.links.domain")), clock)
    @Bean fun subscriptionStore(dataSource: DataSource) = JdbcSubscriptionStore(dataSource)
    @Bean fun cardSubscriptions(store: JdbcSubscriptionStore, codes: JdbcPaymentCodeStore, athletes: AthletePayments,
        ledger: GroupChargeLedger, pricing: br.com.saqz.sharedkernel.group.GroupReceivables, conditions: FinancialConditions,
        provider: HttpAsaasPaymentCodes, notices: ReceivablesNotices, links: JdbcSettleLinks, clock: Clock) =
        CardSubscriptions(store, codes, athletes, ledger, pricing, conditions, provider, notices, links, clock)
    @Bean fun cardSubscriptionsController(actors: SubscriptionActorResolver, service: CardSubscriptions) =
        br.com.saqz.receivables.adapter.input.http.CardSubscriptionsController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun athletePaymentsController(actors: SubscriptionActorResolver, service: AthletePayments) =
        AthletePaymentsController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun receivablesNotices(dataSource: DataSource): ReceivablesNotices = JdbcReceivablesNotices(dataSource)
    @Bean fun commissionTransfers(dataSource: DataSource, operations: JdbcFinancialOperationStore, provider: HttpAsaasPaymentCodes,
        clock: Clock) = JdbcCommissionTransfers(dataSource, operations, provider, clock)
    @Bean fun paymentSettlement(dataSource: DataSource, codes: JdbcPaymentCodeStore, ledger: GroupChargeLedger,
        notices: ReceivablesNotices, provider: HttpAsaasPaymentCodes, commissions: JdbcCommissionTransfers, clock: Clock,
        subscriptions: JdbcSubscriptionStore, cards: CardSubscriptions) =
        PaymentSettlement(JdbcSettlementStore(dataSource), codes, ledger, notices, provider, commissions, clock,
            subscriptions = subscriptions, cards = cards)
    @Bean fun paymentReviews(dataSource: DataSource, ledger: GroupChargeLedger, clock: Clock) =
        PaymentReviews(JdbcReviewStore(dataSource), ledger, clock)
    @Bean fun paymentReviewsController(actors: SubscriptionActorResolver, service: PaymentReviews) =
        PaymentReviewsController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun paymentCodeCancellations(dataSource: DataSource, operations: JdbcFinancialOperationStore, provider: HttpAsaasPaymentCodes,
        clock: Clock) = JdbcPaymentCodeCancellations(dataSource, operations, provider, clock)
    @Bean fun receivablesV1Jobs(pixKeys: JdbcPixKeys, commissions: JdbcCommissionTransfers, cancellations: JdbcPaymentCodeCancellations) =
        ReceivablesV1Jobs(pixKeys, commissions, cancellations)
    @Bean fun cardSubscriptionJobs(cards: CardSubscriptions) = CardSubscriptionJobs(cards)
    @Bean fun settleTime(ledger: GroupChargeLedger, codes: JdbcPaymentCodeStore, athletes: AthletePayments,
        subscriptions: JdbcSubscriptionStore, links: JdbcSettleLinks, notices: ReceivablesNotices, clock: Clock) =
        SettleTime(ledger, codes, athletes, subscriptions, links, notices, clock)
    @Bean fun settleLinksController(actors: SubscriptionActorResolver, service: SettleTime) =
        br.com.saqz.receivables.adapter.input.http.SettleLinksController(FinancialActorResolver { actors.resolve(it) }, service)
    @Bean fun settleTimeJob(settle: SettleTime) = SettleTimeJob(settle)
}

/** Hora do acerto (F4): roda a cada minuto; o link anunciado uma vez garante uma mensagem por jogo ou mês. */
class SettleTimeJob(private val settle: SettleTime) {
    @Scheduled(fixedDelayString = "\${saqz.receivables.settle-time-delay-ms:60000}")
    fun run() { JobLog.frequent("settle_time", { settle.run() }) { mapOf("ran" to 1) } }
}

/** Assinaturas no cartão (F3): ativação do checkout, corte, reajuste e isenção. Tudo idempotente; rodar de novo não duplica. */
class CardSubscriptionJobs(private val cards: CardSubscriptions) {
    @Scheduled(fixedDelayString = "\${saqz.receivables.subscription-delay-ms:300000}")
    fun run() {
        JobLog.frequent("card_subscriptions", {
            cards.activatePending(); cards.enforceCutoffs(); cards.realignDueDates(); cards.adjustValues(); cards.removeExemptMonths()
        }) { mapOf("ran" to 1) }
    }
}

class ReceivablesV1Jobs(private val pixKeys: JdbcPixKeys, private val commissions: JdbcCommissionTransfers,
    private val cancellations: JdbcPaymentCodeCancellations) {
    @Scheduled(fixedDelayString = "\${saqz.receivables.v1-recovery-delay-ms:60000}")
    fun recover() { pixKeys.recoverPending(); commissions.recoverPending(); cancellations.recoverPending() }
}
