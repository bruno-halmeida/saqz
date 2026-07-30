package br.com.saqz.bootstrap.configuration

import br.com.saqz.subscriptions.adapter.input.http.AsaasWebhookController
import br.com.saqz.subscriptions.adapter.output.asaas.AsaasClientSettings
import br.com.saqz.subscriptions.adapter.output.asaas.HttpAsaasGateway
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAsaasIdempotencyStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionEventStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionsTransactionRunner
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.AsaasIdempotencyStore
import br.com.saqz.subscriptions.application.ProcessAsaasWebhook
import br.com.saqz.subscriptions.application.SubscriptionEventStore
import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.application.SubscriptionsTransactionRunner
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.time.Clock
import javax.sql.DataSource

/**
 * Webhook Asaas + cliente HTTP. Só sobe quando o token do webhook está configurado
 * e não-vazio (`saqz.asaas.webhook-token` / `SAQZ_ASAAS_WEBHOOK_TOKEN`), para não
 * exigir chave Asaas nos testes que não exercitam billing.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('\${saqz.asaas.webhook-token:}')")
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
    fun subscriptionRepository(dataSource: DataSource): SubscriptionRepository =
        JdbcSubscriptionRepository(dataSource)

    @Bean
    fun subscriptionEventStore(dataSource: DataSource): SubscriptionEventStore =
        JdbcSubscriptionEventStore(dataSource)

    @Bean
    fun subscriptionsTransactionRunner(dataSource: DataSource): SubscriptionsTransactionRunner =
        JdbcSubscriptionsTransactionRunner(dataSource)

    @Bean
    fun processAsaasWebhook(
        @Value("\${saqz.asaas.webhook-token}") webhookToken: String,
        events: SubscriptionEventStore,
        subscriptions: SubscriptionRepository,
        asaasGateway: AsaasGateway,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
    ) = ProcessAsaasWebhook(webhookToken, events, subscriptions, asaasGateway, transaction, clock)

    @Bean
    fun asaasWebhookController(processAsaasWebhook: ProcessAsaasWebhook) =
        AsaasWebhookController(processAsaasWebhook)
}
