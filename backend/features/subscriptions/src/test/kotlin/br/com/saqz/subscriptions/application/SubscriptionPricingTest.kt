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

    @Test
    fun `applyDiscount accepts 1 to 100 percent and refuses 0 and 101`() {
        assertEquals(990L, SubscriptionPricing.applyDiscount(1_000, 1))
        assertEquals(0L, SubscriptionPricing.applyDiscount(1_000, 100))
        kotlin.test.assertFailsWith<IllegalArgumentException> { SubscriptionPricing.applyDiscount(1_000, 0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { SubscriptionPricing.applyDiscount(1_000, 101) }
    }

    @Test
    fun `discountedPriceCents applies the coupon only when there is one`() {
        val coupon = br.com.saqz.subscriptions.domain.Coupon(java.util.UUID.randomUUID(), "DEZ", discountPercent = 10)

        assertEquals(5_990L, SubscriptionPricing.discountedPriceCents(Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY, null))
        assertEquals(5_391L, SubscriptionPricing.discountedPriceCents(Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY, coupon))
        assertEquals(48_519L, SubscriptionPricing.discountedPriceCents(Plan.ORGANIZADOR, SubscriptionCycle.ANNUAL, coupon))
    }

    @Test
    fun `prorata is zero when the target costs the same`() {
        val now = Instant.parse("2026-07-15T00:00:00Z")
        val end = Instant.parse("2026-07-30T00:00:00Z")

        assertEquals(0L, SubscriptionPricing.prorataUpgradeCents(3_990, 3_990, now, end, SubscriptionCycle.MONTHLY))
        // Diferença de 1 centavo com 1 segundo restante: a fração arredonda para baixo, zero.
        assertEquals(0L, SubscriptionPricing.prorataUpgradeCents(3_990, 3_991, end.minusSeconds(1), end, SubscriptionCycle.MONTHLY))
    }

    @Test
    fun `prorata at or after the period end is the whole difference`() {
        val end = Instant.parse("2026-07-30T00:00:00Z")

        assertEquals(2_000L, SubscriptionPricing.prorataUpgradeCents(3_990, 5_990, end, end, SubscriptionCycle.MONTHLY))
        assertEquals(2_000L, SubscriptionPricing.prorataUpgradeCents(3_990, 5_990, end.plusSeconds(1), end, SubscriptionCycle.MONTHLY))
    }

    @Test
    fun `annual prorata uses the year as the cycle length`() {
        val end = Instant.parse("2027-07-01T00:00:00Z")
        val now = Instant.parse("2026-12-31T12:00:00Z")

        // delta 18000 (53910 - 35910) × 181,5 dias restantes ÷ 365 dias do ciclo = 8950,68 → 8950.
        assertEquals(8_950L, SubscriptionPricing.prorataUpgradeCents(35_910, 53_910, now, end, SubscriptionCycle.ANNUAL))
    }

    @Test
    fun `initial period end is one cycle after now`() {
        val now = Instant.parse("2026-01-31T10:00:00Z")

        assertEquals(Instant.parse("2026-02-28T10:00:00Z"), SubscriptionPricing.initialPeriodEnd(now, SubscriptionCycle.MONTHLY))
        assertEquals(Instant.parse("2027-01-31T10:00:00Z"), SubscriptionPricing.initialPeriodEnd(now, SubscriptionCycle.ANNUAL))
    }

    @Test
    fun `plan price depends on the cycle`() {
        with(SubscriptionPricing) {
            assertEquals(3_990L, Plan.TITULAR.priceCents(SubscriptionCycle.MONTHLY))
            assertEquals(35_910L, Plan.TITULAR.priceCents(SubscriptionCycle.ANNUAL))
        }
    }
}
