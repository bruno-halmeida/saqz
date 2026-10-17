package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.output.jdbc.session.JdbcAccountDeletionJobs
import br.com.saqz.access.application.session.AccountDeletionProviders
import br.com.saqz.access.application.session.CompleteAccountDeletion
import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.subscriptions.application.CancelSubscription
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class AccountDeletionConfiguration {
    @Bean fun accountDeletionJobs(dataSource: DataSource) = JdbcAccountDeletionJobs(dataSource)

    @Bean fun accountDeletionProviders(
        cancelSubscription: ObjectProvider<CancelSubscription>, firebaseApp: FirebaseApp, dataSource: DataSource,
    ): AccountDeletionProviders = object : AccountDeletionProviders {
        override fun cancelSubscription(userId: UUID) {
            cancelDeletedAccountSubscription(JdbcClient.create(dataSource), userId, cancelSubscription.ifAvailable)
        }

        override fun deleteIdentity(subject: String) {
            deleteFirebaseIdentity(FirebaseAuth.getInstance(firebaseApp), subject)
        }
    }

    @Bean fun completeAccountDeletion(jobs: JdbcAccountDeletionJobs, providers: AccountDeletionProviders) =
        CompleteAccountDeletion(jobs, providers)

    @Bean
    @Profile("!test")
    fun accountDeletionWorker(useCase: CompleteAccountDeletion, clock: Clock) = AccountDeletionWorker(useCase, clock)
}

internal fun deleteFirebaseIdentity(auth: FirebaseAuth, subject: String) {
    try {
        auth.revokeRefreshTokens(subject)
        auth.deleteUser(subject)
    } catch (failure: FirebaseAuthException) {
        if (failure.authErrorCode != AuthErrorCode.USER_NOT_FOUND) throw failure
    }
}

/** Billing can be unconfigured; an existing uncancelled subscription must still block identity removal. */
internal fun cancelDeletedAccountSubscription(jdbc: JdbcClient, userId: UUID, cancel: CancelSubscription?) {
    if (cancel != null) {
        cancel.execute(userId)
    } else {
        val needsCancellation = jdbc.sql("""SELECT EXISTS (SELECT 1 FROM subscriptions
            WHERE owner_user_id = :id AND status <> 'CANCELED' AND canceled_at IS NULL)""")
            .param("id", userId).query(Boolean::class.java).single()
        check(!needsCancellation) { "Subscription provider unavailable for account deletion" }
    }
    jdbc.sql("""UPDATE subscriptions SET asaas_credit_card_token = NULL,
        credit_card_last4 = NULL, credit_card_brand = NULL WHERE owner_user_id = :id""")
        .param("id", userId).update()
    // A Apple e o Google cobram por conta própria: o servidor não cancela, e isso não pode travar a exclusão.
    // A pessoa cancela na loja (aviso na confirmação); aqui só fica o registro de que a cobrança segue.
    leftActiveStoreSubscriptions(jdbc, userId).forEach {
        JobLog.item("account_deletion", "store_subscription_left_active", "userId" to userId, "provider" to it)
    }
}

/** Assinaturas de loja ainda valendo (provedor, na ordem App Store, Google Play); as da Asaas não entram. */
internal fun leftActiveStoreSubscriptions(jdbc: JdbcClient, userId: UUID): List<String> = jdbc.sql("""
    SELECT provider FROM (
        SELECT 'APP_STORE' AS provider, 1 AS position FROM app_store_subscriptions
        WHERE owner_user_id = :id AND revoked_at IS NULL
          AND (expires_at > now() OR grace_period_expires_at > now())
        UNION ALL
        SELECT 'GOOGLE_PLAY', 2 FROM google_play_subscriptions
        WHERE owner_user_id = :id AND superseded_at IS NULL
          AND state IN ('ACTIVE', 'CANCELED', 'IN_GRACE_PERIOD') AND expires_at > now()
    ) live ORDER BY position""").param("id", userId).query(String::class.java).list().filterNotNull().distinct()

class AccountDeletionWorker(private val useCase: CompleteAccountDeletion, private val clock: Clock) {
    @Scheduled(fixedDelayString = "\${saqz.account-deletion.delay-ms:15000}")
    fun run() {
        JobLog.frequent("account_deletion", {
            var processed = 0
            repeat(10) { if (!useCase.runNext(clock.instant())) return@frequent processed else processed++ }
            processed
        }) { mapOf("processed" to it) }
    }
}
