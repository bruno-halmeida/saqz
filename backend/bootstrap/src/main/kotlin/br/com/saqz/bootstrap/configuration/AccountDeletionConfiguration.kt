package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.output.jdbc.session.JdbcAccountDeletionJobs
import br.com.saqz.access.application.session.AccountDeletionProviders
import br.com.saqz.access.application.session.CompleteAccountDeletion
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
}

class AccountDeletionWorker(private val useCase: CompleteAccountDeletion, private val clock: Clock) {
    @Scheduled(fixedDelayString = "\${saqz.account-deletion.delay-ms:15000}")
    fun run() { repeat(10) { if (!useCase.runNext(clock.instant())) return } }
}
