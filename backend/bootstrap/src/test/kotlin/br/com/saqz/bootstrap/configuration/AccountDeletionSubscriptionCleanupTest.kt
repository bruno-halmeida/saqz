package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.adapter.output.jdbc.session.JdbcAccountDeletionJobs
import br.com.saqz.access.application.session.AccountDeletionProviders
import br.com.saqz.access.application.session.CompleteAccountDeletion
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.domain.AccessName
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.CancelSubscription
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionsTransactionRunner
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountDeletionSubscriptionCleanupTest {
    private val db = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
    private val jdbc = JdbcClient.create(db)
    private val sessions = JdbcSessionRepository(db)
    private val user = sessions.upsertAndLoad(SessionUpsert("provider-cleanup", null, true, AccessName.from("Test Person"))).user.id

    @Test fun `without billing configured an account without subscription can finish deletion`() {
        sessions.softDelete("provider-cleanup", user)
        cancelDeletedAccountSubscription(jdbc, user, null)
        assertTrue(sessions.deletionRequested("provider-cleanup"))
    }

    @Test fun `missing billing configuration cannot forget an uncancelled subscription`() {
        subscription()
        sessions.softDelete("provider-cleanup", user)
        assertFailsWith<IllegalStateException> { cancelDeletedAccountSubscription(jdbc, user, null) }
        assertEquals("test-card-token", token())
        assertEquals("ACTIVE", jdbc.sql("SELECT status FROM subscriptions WHERE owner_user_id = :id")
            .param("id", user).query(String::class.java).single())
    }

    @Test fun `already cancelled subscription scrubs payment credentials without billing configured`() {
        subscription()
        jdbc.sql("UPDATE subscriptions SET canceled_at = now() WHERE owner_user_id = :id").param("id", user).update()
        sessions.softDelete("provider-cleanup", user)
        cancelDeletedAccountSubscription(jdbc, user, null)
        assertPurged()
    }

    @Test fun `configured provider receives the owner and credentials are scrubbed only on success`() {
        subscription()
        sessions.softDelete("provider-cleanup", user)
        val gateway = mock(AsaasGateway::class.java)
        val now = Instant.parse("2026-09-27T12:00:00Z")
        val subscriptions = JdbcSubscriptionRepository(db)
        val cancellation = CancelSubscription(subscriptions, gateway,
            JdbcSubscriptionsTransactionRunner(db), Clock.fixed(now, ZoneOffset.UTC))
        doThrow(IllegalStateException("temporary provider failure")).`when`(gateway).cancelSubscription("subscription")
        assertFailsWith<IllegalStateException> { cancelDeletedAccountSubscription(jdbc, user, cancellation) }
        assertEquals("test-card-token", token())
        kotlin.test.assertNull(subscriptions.findByOwnerUserId(user)?.canceledAt)
        doNothing().`when`(gateway).cancelSubscription("subscription")
        cancelDeletedAccountSubscription(jdbc, user, cancellation)
        verify(gateway, times(2)).cancelSubscription("subscription")
        assertEquals(now, subscriptions.findByOwnerUserId(user)?.canceledAt)
        assertPurged()
        cancelDeletedAccountSubscription(jdbc, user, cancellation)
        verifyNoMoreInteractions(gateway)
        assertPurged()
    }

    @Test fun `store only subscriber finishes deletion without touching asaas and the store rows stay live`() {
        storeSubscriptions()
        sessions.softDelete("provider-cleanup", user)
        val gateway = mock(AsaasGateway::class.java)
        val cancellation = CancelSubscription(JdbcSubscriptionRepository(db), gateway,
            JdbcSubscriptionsTransactionRunner(db), Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC))

        cancelDeletedAccountSubscription(jdbc, user, cancellation)
        cancelDeletedAccountSubscription(jdbc, user, null)

        verifyNoInteractions(gateway)
        assertEquals(1, count("SELECT count(*) FROM app_store_subscriptions WHERE owner_user_id = :id AND revoked_at IS NULL"))
        assertEquals(1, count("SELECT count(*) FROM google_play_subscriptions WHERE owner_user_id = :id AND state = 'ACTIVE'"))
        assertEquals(listOf("APP_STORE", "GOOGLE_PLAY"), leftActiveStoreSubscriptions(jdbc, user))
    }

    @Test fun `deletion job completes for a store only subscriber on the first attempt`() {
        storeSubscriptions()
        sessions.softDelete("provider-cleanup", user)
        val gateway = mock(AsaasGateway::class.java)
        val cancellation = CancelSubscription(JdbcSubscriptionRepository(db), gateway,
            JdbcSubscriptionsTransactionRunner(db), Clock.systemUTC())
        val providers = object : AccountDeletionProviders {
            override fun cancelSubscription(userId: java.util.UUID) = cancelDeletedAccountSubscription(jdbc, userId, cancellation)
            override fun deleteIdentity(subject: String) = Unit
        }

        val ran = CompleteAccountDeletion(JdbcAccountDeletionJobs(db), providers).runNext(Instant.now().plusSeconds(1))

        assertTrue(ran)
        verifyNoInteractions(gateway)
        assertEquals(1, count("SELECT count(*) FROM account_deletion_requests WHERE user_id = :id AND completed_at IS NOT NULL"))
        assertEquals(1, count("SELECT attempts FROM account_deletion_requests WHERE user_id = :id"))
    }

    @Test fun `expired or revoked store subscriptions are not reported as left active`() {
        storeSubscriptions()
        jdbc.sql("UPDATE app_store_subscriptions SET revoked_at = now() WHERE owner_user_id = :id").param("id", user).update()
        jdbc.sql("UPDATE google_play_subscriptions SET expires_at = now() - interval '1 day' WHERE owner_user_id = :id").param("id", user).update()
        assertEquals(emptyList(), leftActiveStoreSubscriptions(jdbc, user))
    }

    private fun count(sql: String) = jdbc.sql(sql).param("id", user).query(Int::class.java).single()

    private fun storeSubscriptions() {
        jdbc.sql("""INSERT INTO app_store_subscriptions(original_transaction_id, owner_user_id, environment, product_id,
            plan, cycle, latest_transaction_id, latest_purchase_date, latest_signed_at, expires_at)
            VALUES ('apple-original', :id, 'PRODUCTION', 'app.saqz.titular.mensal', 'TITULAR', 'MONTHLY', 'apple-latest',
                now(), now(), now() + interval '20 days')""").param("id", user).update()
        jdbc.sql("""INSERT INTO google_play_subscriptions(purchase_token, owner_user_id, product_id, base_plan_id,
            plan, cycle, state, expires_at)
            VALUES ('google-token', :id, 'app.saqz.titular', 'mensal', 'TITULAR', 'MONTHLY', 'ACTIVE',
                now() + interval '20 days')""").param("id", user).update()
    }

    private fun subscription() {
        jdbc.sql("""INSERT INTO subscriptions(owner_user_id, plan, cycle, status, asaas_customer_id,
            asaas_subscription_id, current_period_end, created_at, updated_at, asaas_credit_card_token,
            credit_card_last4, credit_card_brand)
            VALUES (:id, 'TITULAR', 'MONTHLY', 'ACTIVE', 'customer', 'subscription', now(), now(), now(),
                'test-card-token', '1234', 'VISA')""").param("id", user).update()
    }

    private fun token() = jdbc.sql("SELECT asaas_credit_card_token FROM subscriptions WHERE owner_user_id = :id")
        .param("id", user).query(String::class.java).single()

    private fun assertPurged() {
        assertTrue(jdbc.sql("""SELECT asaas_credit_card_token IS NULL AND credit_card_last4 IS NULL AND
            credit_card_brand IS NULL FROM subscriptions WHERE owner_user_id = :id""")
            .param("id", user).query(Boolean::class.java).single())
    }
}
