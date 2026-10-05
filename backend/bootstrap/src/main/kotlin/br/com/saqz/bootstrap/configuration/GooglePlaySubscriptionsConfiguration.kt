package br.com.saqz.bootstrap.configuration

import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.adapter.input.http.GooglePlayNotificationController
import br.com.saqz.subscriptions.adapter.input.http.GooglePlaySubscriptionController
import br.com.saqz.subscriptions.adapter.output.googleplay.HttpGooglePlayPurchasesApi
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcGooglePlayNotificationStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcGooglePlaySubscriptionRepository
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.GooglePlayNotificationStore
import br.com.saqz.subscriptions.application.GooglePlayPurchasesApi
import br.com.saqz.subscriptions.application.GooglePlaySubscriptionRepository
import br.com.saqz.subscriptions.application.ProcessGooglePlayNotification
import br.com.saqz.subscriptions.application.SubmitGooglePlayPurchase
import br.com.saqz.subscriptions.application.SubscriptionsTransactionRunner
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

/** Assinatura pelo Google Play (Play Billing). Contrato em docs/subscriptions/google-play.md. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class GooglePlaySubscriptionsConfiguration {
    @Bean
    fun googlePlaySubscriptionRepository(dataSource: DataSource): GooglePlaySubscriptionRepository =
        JdbcGooglePlaySubscriptionRepository(dataSource)

    @Bean
    fun googlePlayNotificationStore(dataSource: DataSource): GooglePlayNotificationStore =
        JdbcGooglePlayNotificationStore(dataSource)

    @Bean
    fun googlePlayPurchasesApi(@Value("\${saqz.google-play.package-name}") packageName: String): GooglePlayPurchasesApi =
        HttpGooglePlayPurchasesApi(packageName, GooglePlayAccessTokens())

    @Bean
    fun submitGooglePlayPurchase(
        api: GooglePlayPurchasesApi,
        subscriptions: GooglePlaySubscriptionRepository,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
    ) = SubmitGooglePlayPurchase(api, subscriptions, transaction, clock)

    @Bean
    fun processGooglePlayNotification(
        @Value("\${saqz.google-play.webhook-token}") webhookToken: String,
        @Value("\${saqz.google-play.package-name}") packageName: String,
        api: GooglePlayPurchasesApi,
        subscriptions: GooglePlaySubscriptionRepository,
        notifications: GooglePlayNotificationStore,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
    ) = ProcessGooglePlayNotification(webhookToken, packageName, api, subscriptions, notifications, transaction, clock)

    @Bean
    fun googlePlaySubscriptionController(
        actors: AuthenticatedActorResolver,
        submitGooglePlayPurchase: SubmitGooglePlayPurchase,
        getMySubscription: GetMySubscription,
    ) = GooglePlaySubscriptionController(actors, submitGooglePlayPurchase, getMySubscription)

    @Bean
    fun googlePlayNotificationController(processGooglePlayNotification: ProcessGooglePlayNotification) =
        GooglePlayNotificationController(processGooglePlayNotification)
}

/**
 * Conta de serviço do Firebase Admin (GOOGLE_APPLICATION_CREDENTIALS) com o escopo do Play. Lida
 * só na primeira chamada: ambiente sem credencial (dev, testes) sobe e a chamada falha com 503.
 */
private class GooglePlayAccessTokens : () -> String {
    private val credentials by lazy {
        GoogleCredentials.getApplicationDefault().createScoped(HttpGooglePlayPurchasesApi.SCOPE)
    }

    override fun invoke(): String {
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}
