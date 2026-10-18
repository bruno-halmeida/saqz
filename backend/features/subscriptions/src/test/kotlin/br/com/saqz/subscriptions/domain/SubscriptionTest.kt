package br.com.saqz.subscriptions.domain

import br.com.saqz.subscriptions.application.AsaasBillingType
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionTest {

    @Test
    fun `past due keeps access inside the grace window`() {
        val since = Instant.parse("2026-07-30T12:00:00Z")
        val sub = pastDue(pastDueSince = since, firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"))

        assertTrue(sub.isEntitlingAt(since.plus(6, ChronoUnit.DAYS)))
    }

    @Test
    fun `past due loses access once the grace window expires`() {
        // Antes disto a regra era so `firstConfirmedAt != null`: quem pagasse uma vez ficava
        // com o plano para sempre sem nunca mais pagar.
        val since = Instant.parse("2026-07-30T12:00:00Z")
        val sub = pastDue(pastDueSince = since, firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"))

        assertFalse(sub.isEntitlingAt(since.plus(8, ChronoUnit.DAYS)))
    }

    @Test
    fun `access ends at the close of the 7th day counted from the due date in Sao Paulo`() {
        // Vencimento em 30/07 (SP). O 7º dia é 06/08; o acesso vai até 23h59 e a meia-noite seguinte (07/08 00:00 SP = 03:00Z) corta.
        val since = Instant.parse("2026-07-30T03:00:00Z")
        val sub = pastDue(pastDueSince = since, firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"))

        assertEquals(Instant.parse("2026-08-07T03:00:00Z"), Subscription.pastDueFreezeAt(since))
        assertTrue(sub.isEntitlingAt(Instant.parse("2026-08-07T02:59:59Z")))
        assertFalse(sub.isEntitlingAt(Instant.parse("2026-08-07T03:00:00Z")))
    }

    @Test
    fun `the due day is the Sao Paulo day whatever the hour the overdue was recorded`() {
        // 02h UTC de 31/07 ainda é 23h de 30/07 em São Paulo: o dia 0 é 30/07 e o congelamento não anda para 31/07.
        val lateEvening = Instant.parse("2026-07-31T02:00:00Z")
        val morning = Instant.parse("2026-07-30T10:00:00Z")

        assertEquals(Subscription.pastDueFreezeAt(morning), Subscription.pastDueFreezeAt(lateEvening))
        assertEquals(java.time.LocalDate.parse("2026-07-30"), Subscription.pastDueDay(lateEvening))
    }

    @Test
    fun `past due without a first payment never entitles, grace or not`() {
        val since = Instant.parse("2026-07-30T12:00:00Z")
        val sub = pastDue(pastDueSince = since, firstConfirmedAt = null)

        assertFalse(sub.isEntitlingAt(since.plus(1, ChronoUnit.DAYS)))
    }

    @Test
    fun `legacy row without pastDueSince keeps access`() {
        val sub = pastDue(pastDueSince = null, firstConfirmedAt = Instant.parse("2026-06-01T00:00:00Z"))

        assertTrue(sub.isEntitlingAt(Instant.parse("2027-01-01T00:00:00Z")))
    }

    private fun pastDue(pastDueSince: Instant?, firstConfirmedAt: Instant?) = Subscription(
        ownerUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        billingType = null,
        currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"),
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = pastDueSince,
        firstConfirmedAt = firstConfirmedAt,
    )

    @Test
    fun `new subscription defaults to active with no pending plan coupon or past due state`() {
        val subscription = Subscription(
            ownerUserId = UUID.randomUUID(),
            plan = Plan.TITULAR,
            cycle = SubscriptionCycle.MONTHLY,
            asaasCustomerId = "cus_123",
            asaasSubscriptionId = "sub_123",
            billingType = AsaasBillingType.PIX,
            currentPeriodEnd = Instant.parse("2026-08-30T00:00:00Z"),
        )

        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertNull(subscription.canceledAt)
        assertNull(subscription.pendingPlan)
        assertNull(subscription.pendingPlanEffectiveAt)
        assertNull(subscription.couponId)
        assertNull(subscription.couponCyclesRemaining)
        assertNull(subscription.pastDueSince)
    }
}
