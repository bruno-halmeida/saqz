package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.OverdueInvoice
import br.com.saqz.subscriptions.application.PastDueProvider
import br.com.saqz.subscriptions.application.PastDueSubscription
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcSubscriptionPastDueRepositoryIntegrationTest {
    private val source = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val subscriptions = JdbcSubscriptionRepository(source)
    private val repository = JdbcSubscriptionPastDueRepository(source)
    private val now = Instant.now()

    @Test
    fun `lista so atraso do site de quem ja pagou, nao cancelou e ainda esta dentro da carencia`() {
        val late = site(since = now.minus(Duration.ofDays(3)))
        site(since = now.minus(Duration.ofDays(3)), firstConfirmedAt = null)          // nunca pagou: checkout pendente
        site(since = now.minus(Duration.ofDays(3)), canceledAt = now.minusSeconds(60)) // cancelou
        site(since = now.minus(Duration.ofDays(3)), status = SubscriptionStatus.ACTIVE) // em dia
        site(since = now.minus(Duration.ofDays(40)))                                   // carência já acabou
        site(since = null)                                                             // linha legada

        val found = repository.pastDue(now)

        assertEquals(listOf(late), found.map { it.ownerUserId })
        val one = found.single()
        assertEquals(PastDueProvider.ASAAS, one.provider)
        assertEquals(Subscription.pastDueDay(one.since), one.dueDay)
        assertEquals(one.dueDay!!.plusDays(7), one.lastDay)
    }

    @Test
    fun `quem tem plano de loja em dia nao recebe o aviso do site`() {
        val owner = site(since = now.minus(Duration.ofDays(3)))
        appStore(owner, expiresAt = now.plus(Duration.ofDays(20)))

        assertTrue(repository.pastDue(now).none { it.ownerUserId == owner })
    }

    @Test
    fun `App Store vencida em billing retry e ainda na carencia da Apple entra com o ultimo dia na carencia`() {
        val owner = user()
        appStore(owner, expiresAt = now.minus(Duration.ofDays(1)), billingRetry = true, grace = now.plus(Duration.ofDays(2)))
        user().also { appStore(it, expiresAt = now.minus(Duration.ofDays(1)), billingRetry = true, grace = null) }       // sem carência: já sem acesso
        user().also { appStore(it, expiresAt = now.minus(Duration.ofDays(1)), billingRetry = false, grace = now.plus(Duration.ofDays(2))) }
        user().also { appStore(it, expiresAt = now.minus(Duration.ofDays(1)), billingRetry = true, grace = now.plus(Duration.ofDays(2)), autoRenew = false) }

        val found = repository.pastDue(now)

        assertEquals(listOf(owner), found.map { it.ownerUserId })
        assertEquals(PastDueProvider.APP_STORE, found.single().provider)
        assertEquals(Subscription.pastDueDay(now.minus(Duration.ofDays(1))), found.single().dueDay)
        assertEquals(Subscription.pastDueDay(now.plus(Duration.ofDays(2))), found.single().lastDay)
    }

    @Test
    fun `Google Play em carencia entra sem data de vencimento e os outros estados nao`() {
        val owner = user()
        play(owner, "IN_GRACE_PERIOD", now.plus(Duration.ofDays(3)))
        play(user(), "ON_HOLD", now.plus(Duration.ofDays(3)))
        play(user(), "ACTIVE", now.plus(Duration.ofDays(3)))
        play(user(), "IN_GRACE_PERIOD", now.minus(Duration.ofDays(1)))

        val found = repository.pastDue(now)

        assertEquals(listOf(owner), found.map { it.ownerUserId })
        assertEquals(PastDueProvider.GOOGLE_PLAY, found.single().provider)
        assertNull(found.single().dueDay)
        assertEquals(Subscription.pastDueDay(now.plus(Duration.ofDays(3))), found.single().lastDay)
    }

    @Test
    fun `reserva e idempotente por dono, origem, atraso e marco, e o mais apertado e o maior`() {
        val s = candidate(user(), now.minus(Duration.ofDays(3)))

        assertTrue(repository.claim(s, listOf(0)))
        assertFalse(repository.claim(s, listOf(0)))
        assertTrue(repository.claim(s, listOf(0, 3)))
        assertFalse(repository.claim(s, listOf(0, 3)))
        assertEquals(setOf<Short>(0, 3), milestones(s.ownerUserId))

        repository.release(s, 3)
        assertEquals(setOf<Short>(0), milestones(s.ownerUserId))
        assertTrue(repository.claim(s, listOf(0, 3)))
    }

    @Test
    fun `rodada perdida reserva todos os marcos e avisa uma vez`() {
        val s = candidate(user(), now.minus(Duration.ofDays(8)))

        assertTrue(repository.claim(s, listOf(0, 3, 7)))
        assertFalse(repository.claim(s, listOf(0, 3, 7)))
        assertEquals(setOf<Short>(0, 3, 7), milestones(s.ownerUserId))
    }

    @Test
    fun `atraso novo depois de pagar tem outra ancora e recomeca os marcos`() {
        val owner = user()
        val first = candidate(owner, now.minus(Duration.ofDays(30)))
        val second = candidate(owner, now.minus(Duration.ofDays(1)))

        assertTrue(repository.claim(first, listOf(0)))
        assertTrue(repository.claim(second, listOf(0)))
        assertFalse(repository.claim(second, listOf(0)))
    }

    @Test
    fun `a trava recusa marco fora de 0, 3 e 7 e origem desconhecida`() {
        val s = candidate(user(), now)
        for (milestone in listOf(1, 5, 8, -3)) {
            val failed = runCatching { repository.claim(s, listOf(milestone)) }.isFailure
            assertTrue(failed, "marco $milestone")
        }
        val failed = runCatching {
            jdbc.sql("INSERT INTO subscription_past_due_notices (owner_user_id, provider, past_due_since, milestone) VALUES (:o, 'OUTRO', now(), 0)")
                .param("o", s.ownerUserId).update()
        }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `a fatura vem da mensalidade vencida do payload, nunca da cobranca de upgrade`() {
        val owner = site(since = now.minus(Duration.ofDays(3)))
        val subscription = repository.pastDue(now).single { it.ownerUserId == owner }
        val dueDay = subscription.dueDay!!
        event(owner, "evt_monthly", overdue("pay_monthly", subscription.asaasSubscriptionId, dueDay, "https://www.asaas.com/i/monthly"), ago = Duration.ofDays(2))
        // Upgrade avulso vencido depois: não tem `subscription` no payload e chegou mais tarde.
        event(owner, "evt_upgrade", overdue("pay_upgrade", null, dueDay.plusDays(1), "https://www.asaas.com/i/upgrade"), ago = Duration.ofHours(1))

        assertEquals(OverdueInvoice("pay_monthly", "https://www.asaas.com/i/monthly"), repository.overdueInvoice(subscription))
    }

    @Test
    fun `evento velho de vencimento anterior ao atraso nao vira link`() {
        val owner = site(since = now.minus(Duration.ofDays(3)))
        val subscription = repository.pastDue(now).single { it.ownerUserId == owner }
        event(owner, "evt_old", overdue("pay_old", subscription.asaasSubscriptionId, subscription.dueDay!!.minusDays(30), "https://www.asaas.com/i/old"), ago = Duration.ofDays(1))
        event(owner, "evt_other_sub", overdue("pay_other", "sub_de_outra", subscription.dueDay!!, "https://www.asaas.com/i/other"), ago = Duration.ofDays(1))
        event(owner, "evt_broken", "isto não é json", ago = Duration.ofDays(1))

        assertNull(repository.overdueInvoice(subscription))
    }

    @Test
    fun `de duas mensalidades vencidas vale a mais recente`() {
        val owner = site(since = now.minus(Duration.ofDays(40)))
        val subscription = repository.pastDue(now.minus(Duration.ofDays(38))).single { it.ownerUserId == owner }
        val due = subscription.dueDay!!
        event(owner, "evt_a", overdue("pay_a", subscription.asaasSubscriptionId, due, "https://www.asaas.com/i/a"), ago = Duration.ofDays(30))
        event(owner, "evt_b", overdue("pay_b", subscription.asaasSubscriptionId, due.plusDays(30), "https://www.asaas.com/i/b"), ago = Duration.ofDays(2))

        assertEquals("pay_b", repository.overdueInvoice(subscription)?.paymentId)
    }

    @Test
    fun `loja nao tem fatura`() {
        assertNull(repository.overdueInvoice(candidate(user(), now, PastDueProvider.APP_STORE)))
    }

    private fun candidate(owner: UUID, since: Instant, provider: PastDueProvider = PastDueProvider.ASAAS) =
        PastDueSubscription(owner, provider, since, Subscription.pastDueDay(since), Subscription.pastDueDay(since).plusDays(7), "sub_x")

    private fun milestones(owner: UUID) =
        jdbc.sql("SELECT milestone FROM subscription_past_due_notices WHERE owner_user_id = :o").param("o", owner).query(Short::class.java).set()

    private fun user(): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'Owner', now(), now())")
            .param("id", id).param("subject", id.toString()).update()
    }

    private fun site(
        since: Instant?,
        status: SubscriptionStatus = SubscriptionStatus.PAST_DUE,
        firstConfirmedAt: Instant? = now.minus(Duration.ofDays(60)),
        canceledAt: Instant? = null,
    ): UUID = user().also { owner ->
        subscriptions.insert(
            Subscription(
                ownerUserId = owner, plan = Plan.TITULAR, cycle = SubscriptionCycle.MONTHLY, asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_$owner", billingType = AsaasBillingType.PIX, currentPeriodEnd = now.plus(Duration.ofDays(20)),
                status = status, canceledAt = canceledAt, pastDueSince = since, firstConfirmedAt = firstConfirmedAt,
            ),
        )
    }

    private fun appStore(owner: UUID, expiresAt: Instant, billingRetry: Boolean = false, grace: Instant? = null, autoRenew: Boolean? = true) {
        jdbc.sql(
            """
            INSERT INTO app_store_subscriptions (original_transaction_id, owner_user_id, environment, product_id, plan, cycle,
                latest_transaction_id, latest_purchase_date, latest_signed_at, expires_at, auto_renew, in_billing_retry, grace_period_expires_at)
            VALUES (:id, :owner, 'PRODUCTION', 'app.saqz.titular.mensal', 'TITULAR', 'MONTHLY', :id, now(), now(), :expires, :autoRenew, :retry, :grace)
            """.trimIndent(),
        ).param("id", "otx-${UUID.randomUUID()}").param("owner", owner).param("expires", Timestamp.from(expiresAt))
            .param("autoRenew", autoRenew).param("retry", billingRetry).param("grace", grace?.let(Timestamp::from)).update()
    }

    private fun play(owner: UUID, state: String, expiresAt: Instant) {
        jdbc.sql(
            """
            INSERT INTO google_play_subscriptions (purchase_token, owner_user_id, product_id, base_plan_id, plan, cycle, state, expires_at)
            VALUES (:token, :owner, 'app.saqz.titular', 'mensal', 'TITULAR', 'MONTHLY', :state, :expires)
            """.trimIndent(),
        ).param("token", "tok-${UUID.randomUUID()}").param("owner", owner).param("state", state).param("expires", Timestamp.from(expiresAt)).update()
    }

    private fun event(owner: UUID, eventId: String, payload: String, ago: Duration) {
        jdbc.sql(
            """
            INSERT INTO subscription_events (id, asaas_event_id, type, payload, owner_user_id, processed_at, created_at)
            VALUES (:id, :eventId, 'PAYMENT_OVERDUE', :payload, :owner, :at, :at)
            """.trimIndent(),
        ).param("id", UUID.randomUUID()).param("eventId", eventId).param("payload", payload).param("owner", owner)
            .param("at", Timestamp.from(now.minus(ago))).update()
    }

    private fun overdue(paymentId: String, subscriptionId: String?, due: LocalDate, invoiceUrl: String): String {
        val subscription = subscriptionId?.let { ""","subscription":"$it"""" } ?: ""
        return """{"event":"PAYMENT_OVERDUE","payment":{"id":"$paymentId"$subscription,"dueDate":"$due","invoiceUrl":"$invoiceUrl"}}"""
    }
}
