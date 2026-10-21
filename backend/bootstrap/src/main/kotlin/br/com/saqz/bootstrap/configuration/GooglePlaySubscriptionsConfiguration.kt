package br.com.saqz.bootstrap.configuration

import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.adapter.input.http.GooglePlayNotificationController
import br.com.saqz.subscriptions.adapter.input.http.GooglePlaySubscriptionController
import br.com.saqz.subscriptions.adapter.output.googleplay.HttpGooglePlayPurchasesApi
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcGooglePlayNotificationStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcGooglePlaySubscriptionRepository
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.CancelUnpaidWebCheckout
import br.com.saqz.subscriptions.application.CouponStoreAttribution
import br.com.saqz.subscriptions.application.GooglePlayNotificationStore
import br.com.saqz.subscriptions.application.GooglePlayOrderAmounts
import br.com.saqz.subscriptions.application.GooglePlayOrdersApi
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
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import javax.sql.DataSource

/** Assinatura pelo Google Play (Play Billing). Contrato em docs/subscriptions/google-play.md. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class GooglePlaySubscriptionsConfiguration {
    @Bean
    fun googlePlaySubscriptionRepository(dataSource: DataSource) = JdbcGooglePlaySubscriptionRepository(dataSource)

    @Bean
    fun googlePlayNotificationStore(dataSource: DataSource): GooglePlayNotificationStore =
        JdbcGooglePlayNotificationStore(dataSource)

    @Bean
    fun httpGooglePlayPurchasesApi(@Value("\${saqz.google-play.package-name}") packageName: String) =
        HttpGooglePlayPurchasesApi(packageName, GooglePlayAccessTokens())

    @Bean
    fun googlePlayOrderAmounts(api: GooglePlayOrdersApi, repository: JdbcGooglePlaySubscriptionRepository, clock: Clock) =
        GooglePlayOrderAmounts(api, repository, clock)

    @Bean
    fun googlePlayOrderAmountsJob(orderAmounts: GooglePlayOrderAmounts) = GooglePlayOrderAmountsJob(orderAmounts)

    @Bean
    fun submitGooglePlayPurchase(
        api: GooglePlayPurchasesApi,
        subscriptions: GooglePlaySubscriptionRepository,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
        unpaidCheckout: CancelUnpaidWebCheckout,
        couponAttribution: CouponStoreAttribution,
        orderAmounts: GooglePlayOrderAmounts,
    ) = SubmitGooglePlayPurchase(api, subscriptions, transaction, clock, unpaidCheckout, couponAttribution, orderAmounts)

    @Bean
    fun processGooglePlayNotification(
        @Value("\${saqz.google-play.webhook-token}") webhookToken: String,
        @Value("\${saqz.google-play.package-name}") packageName: String,
        api: GooglePlayPurchasesApi,
        subscriptions: GooglePlaySubscriptionRepository,
        notifications: GooglePlayNotificationStore,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
        unpaidCheckout: CancelUnpaidWebCheckout,
        couponAttribution: CouponStoreAttribution,
        orderAmounts: GooglePlayOrderAmounts,
    ) = ProcessGooglePlayNotification(
        webhookToken, packageName, api, subscriptions, notifications, transaction, clock, unpaidCheckout, couponAttribution, orderAmounts,
    )

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

/** Pedidos sem valor (compra em que a Orders API falhou e os anteriores à V182), um lote por rodada. */
class GooglePlayOrderAmountsJob(private val orderAmounts: GooglePlayOrderAmounts) {
    @Scheduled(
        fixedDelayString = "\${saqz.google-play.order-amounts-delay-ms:600000}",
        initialDelayString = "\${saqz.google-play.order-amounts-initial-delay-ms:120000}",
    )
    fun run() {
        orderAmounts.backfill()
    }
}

/**
 * Conta de serviço do Firebase Admin (GOOGLE_APPLICATION_CREDENTIALS) com o escopo do Play. Lida
 * só na primeira chamada: ambiente sem credencial (dev, testes) sobe e a chamada falha com 503.
 */
internal class GooglePlayAccessTokens : () -> String {
    private val credentials by lazy {
        GoogleCredentials.getApplicationDefault().createScoped(HttpGooglePlayPurchasesApi.SCOPE)
    }

    override fun invoke(): String {
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}
