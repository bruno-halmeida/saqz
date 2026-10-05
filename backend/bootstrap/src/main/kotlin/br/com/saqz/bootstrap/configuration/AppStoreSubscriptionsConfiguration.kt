package br.com.saqz.bootstrap.configuration

import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.adapter.input.http.AppStoreNotificationController
import br.com.saqz.subscriptions.adapter.input.http.AppStoreSubscriptionController
import br.com.saqz.subscriptions.adapter.output.appstore.AppStoreVerifierSettings
import br.com.saqz.subscriptions.adapter.output.appstore.AppleSignedDataVerifier
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAppStoreNotificationStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAppStoreSubscriptionRepository
import br.com.saqz.subscriptions.application.AppStoreNotificationStore
import br.com.saqz.subscriptions.application.AppStoreSignedDataVerifier
import br.com.saqz.subscriptions.application.AppStoreSubscriptionRepository
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.ProcessAppStoreNotification
import br.com.saqz.subscriptions.application.SubmitAppStoreTransaction
import br.com.saqz.subscriptions.application.SubscriptionsTransactionRunner
import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/** Assinatura pela App Store (In-App Purchase). Contrato em docs/subscriptions/app-store.md. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class AppStoreSubscriptionsConfiguration {
    @Bean
    fun appStoreSubscriptionRepository(dataSource: DataSource): AppStoreSubscriptionRepository =
        JdbcAppStoreSubscriptionRepository(dataSource)

    @Bean
    fun appStoreNotificationStore(dataSource: DataSource): AppStoreNotificationStore =
        JdbcAppStoreNotificationStore(dataSource)

    @Bean
    fun appStoreSignedDataVerifier(
        @Value("\${saqz.app-store.bundle-id}") bundleId: String,
        @Value("\${saqz.app-store.app-apple-id}") appAppleId: String,
        @Value("\${saqz.app-store.environments}") environments: String,
        @Value("\${saqz.app-store.online-checks}") onlineChecks: Boolean,
    ): AppStoreSignedDataVerifier = AppleSignedDataVerifier(
        AppStoreVerifierSettings(
            bundleId = bundleId,
            appAppleId = appAppleId.trim().takeIf(String::isNotEmpty)?.toLong(),
            environments = environments.split(',').map(String::trim).filter(String::isNotEmpty)
                .map(AppStoreEnvironment::valueOf).toSet(),
            onlineChecks = onlineChecks,
        ),
    )

    @Bean
    fun submitAppStoreTransaction(
        verifier: AppStoreSignedDataVerifier,
        subscriptions: AppStoreSubscriptionRepository,
        transaction: SubscriptionsTransactionRunner,
    ) = SubmitAppStoreTransaction(verifier, subscriptions, transaction)

    @Bean
    fun processAppStoreNotification(
        verifier: AppStoreSignedDataVerifier,
        subscriptions: AppStoreSubscriptionRepository,
        notifications: AppStoreNotificationStore,
        transaction: SubscriptionsTransactionRunner,
    ) = ProcessAppStoreNotification(verifier, subscriptions, notifications, transaction)

    @Bean
    fun appStoreSubscriptionController(
        actors: AuthenticatedActorResolver,
        submitAppStoreTransaction: SubmitAppStoreTransaction,
        getMySubscription: GetMySubscription,
    ) = AppStoreSubscriptionController(actors, submitAppStoreTransaction, getMySubscription)

    @Bean
    fun appStoreNotificationController(processAppStoreNotification: ProcessAppStoreNotification) =
        AppStoreNotificationController(processAppStoreNotification)
}
