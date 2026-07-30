package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.subscriptions.adapter.input.http.AsaasWebhookController
import br.com.saqz.subscriptions.adapter.input.http.ReceiptController
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionCommandController
import br.com.saqz.subscriptions.adapter.output.asaas.AsaasClientSettings
import br.com.saqz.subscriptions.adapter.output.asaas.HttpAsaasGateway
import br.com.saqz.groups.adapter.output.jdbc.plan.JdbcOwnerPlanUsageLookup
import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAsaasIdempotencyStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionEventStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionsTransactionRunner
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.AsaasIdempotencyStore
import br.com.saqz.subscriptions.application.CancelSubscription
import br.com.saqz.subscriptions.application.ChangePlan
import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.application.CreateSubscription
import br.com.saqz.subscriptions.application.ListReceipts
import br.com.saqz.subscriptions.application.ProcessAsaasWebhook
import br.com.saqz.subscriptions.application.SubscriptionEventStore
import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.application.SubscriptionsTransactionRunner
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.time.Clock
import javax.sql.DataSource

/**
 * Webhook Asaas + cliente HTTP + write endpoints de assinatura. Só sobe quando o
 * token do webhook está configurado e não-vazio (`saqz.asaas.webhook-token` /
 * `SAQZ_ASAAS_WEBHOOK_TOKEN`), para não exigir chave Asaas nos testes que não
 * exercitam billing.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
@Conditional(AsaasWebhookTokenConfiguredCondition::class)
class AsaasWebhookConfiguration {
    @Bean
    fun asaasClientSettings(environment: Environment): AsaasClientSettings =
        AsaasClientSettings.fromProperties { key ->
            environment.getProperty(key) ?: System.getenv(key)
        }

    @Bean
    fun asaasIdempotencyStore(dataSource: DataSource): AsaasIdempotencyStore =
        JdbcAsaasIdempotencyStore(dataSource)

    @Bean
    fun asaasGateway(
        settings: AsaasClientSettings,
        store: AsaasIdempotencyStore,
    ): AsaasGateway = HttpAsaasGateway(settings, store)

    @Bean
    fun subscriptionEventStore(dataSource: DataSource): SubscriptionEventStore =
        JdbcSubscriptionEventStore(dataSource)

    @Bean
    fun subscriptionsTransactionRunner(dataSource: DataSource): SubscriptionsTransactionRunner =
        JdbcSubscriptionsTransactionRunner(dataSource)

    @Bean
    fun ownerPlanUsageLookup(dataSource: DataSource, clock: Clock): OwnerPlanUsageLookup =
        JdbcOwnerPlanUsageLookup(dataSource, clock)

    @Bean
    fun processAsaasWebhook(
        @Value("\${saqz.asaas.webhook-token}") webhookToken: String,
        events: SubscriptionEventStore,
        subscriptions: SubscriptionRepository,
        asaasGateway: AsaasGateway,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
        coupons: CouponRepository,
    ) = ProcessAsaasWebhook(
        expectedToken = webhookToken,
        events = events,
        subscriptions = subscriptions,
        asaasGateway = asaasGateway,
        transaction = transaction,
        clock = clock,
        coupons = coupons,
    )

    @Bean
    fun asaasWebhookController(processAsaasWebhook: ProcessAsaasWebhook) =
        AsaasWebhookController(processAsaasWebhook)

    @Bean
    fun subscriptionActorResolver(bootstrapSession: BootstrapSession) = SubscriptionActorResolver { identity ->
        when (val result = bootstrapSession.execute(identity)) {
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            is BootstrapSessionResult.Success -> result.session.user.id
        }
    }

    @Bean
    fun createSubscription(
        subscriptions: SubscriptionRepository,
        coupons: CouponRepository,
        asaasGateway: AsaasGateway,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
    ) = CreateSubscription(subscriptions, coupons, asaasGateway, transaction, clock)

    @Bean
    fun changePlan(
        subscriptions: SubscriptionRepository,
        asaasGateway: AsaasGateway,
        usageLookup: OwnerPlanUsageLookup,
        coupons: CouponRepository,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
    ) = ChangePlan(subscriptions, asaasGateway, usageLookup, coupons, transaction, clock)

    @Bean
    fun cancelSubscription(
        subscriptions: SubscriptionRepository,
        asaasGateway: AsaasGateway,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
    ) = CancelSubscription(subscriptions, asaasGateway, transaction, clock)

    @Bean
    fun listReceipts(
        subscriptions: SubscriptionRepository,
        events: SubscriptionEventStore,
    ) = ListReceipts(subscriptions, events)

    @Bean
    fun subscriptionCommandController(
        actors: SubscriptionActorResolver,
        createSubscription: CreateSubscription,
        changePlan: ChangePlan,
        cancelSubscription: CancelSubscription,
    ) = SubscriptionCommandController(actors, createSubscription, changePlan, cancelSubscription)

    @Bean
    fun receiptController(
        actors: SubscriptionActorResolver,
        listReceipts: ListReceipts,
    ) = ReceiptController(actors, listReceipts)
}
