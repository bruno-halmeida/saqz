package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class SubscriptionPricingTest {
    @Test
    fun `applyDiscount floors percent off list price`() {
        assertEquals(3_192L, SubscriptionPricing.applyDiscount(3_990, 20))
        assertEquals(0L, SubscriptionPricing.applyDiscount(100, 100))
    }

    @Test
    fun `hasActiveCouponDiscount treats null cycles with couponId as permanent`() {
        val id = java.util.UUID.randomUUID()
        assertEquals(false, SubscriptionPricing.hasActiveCouponDiscount(null, null))
        assertEquals(false, SubscriptionPricing.hasActiveCouponDiscount(null, 3))
        assertEquals(true, SubscriptionPricing.hasActiveCouponDiscount(id, null))
        assertEquals(true, SubscriptionPricing.hasActiveCouponDiscount(id, 2))
        assertEquals(false, SubscriptionPricing.hasActiveCouponDiscount(id, 0))
    }

    @Test
    fun `prorata is zero when target is not more expensive`() {
        val now = Instant.parse("2026-07-15T00:00:00Z")
        val end = Instant.parse("2026-07-30T00:00:00Z")
        assertEquals(
            0L,
            SubscriptionPricing.prorataUpgradeCents(5_990, 3_990, now, end, SubscriptionCycle.MONTHLY),
        )
    }

    @Test
    fun `prorata uses remaining fraction of cycle`() {
        val now = Instant.parse("2026-07-15T12:00:00Z")
        val end = Instant.parse("2026-07-30T12:00:00Z")
        val cents = SubscriptionPricing.prorataUpgradeCents(
            currentPriceCents = Plan.TITULAR.monthlyPriceCents,
            targetPriceCents = Plan.ORGANIZADOR.monthlyPriceCents,
            now = now,
            currentPeriodEnd = end,
            cycle = SubscriptionCycle.MONTHLY,
        )
        // delta 2000 over ~30d remaining 15d → ~1000
        assertEquals(1_000L, cents)
    }
}
