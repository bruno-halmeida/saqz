package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
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
