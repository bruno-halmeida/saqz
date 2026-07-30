package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.max

object SubscriptionPricing {
    fun Plan.priceCents(cycle: SubscriptionCycle): Long =
        when (cycle) {
            SubscriptionCycle.MONTHLY -> monthlyPriceCents
            SubscriptionCycle.ANNUAL -> annualPriceCents
        }

    fun applyDiscount(priceCents: Long, discountPercent: Int): Long {
        require(discountPercent in 1..100)
        return priceCents - (priceCents * discountPercent / 100)
    }

    fun discountedPriceCents(plan: Plan, cycle: SubscriptionCycle, coupon: Coupon?): Long {
        val full = plan.priceCents(cycle)
        if (coupon == null) return full
        return applyDiscount(full, coupon.discountPercent)
    }

    /**
     * Whether [couponId]/[couponCyclesRemaining] still grant a discount:
     * - no couponId → inactive
     * - couponId set + cycles null → permanent discount
     * - couponId set + cycles > 0 → finite remaining
     * - couponId set + cycles <= 0 → exhausted
     */
    fun hasActiveCouponDiscount(couponId: java.util.UUID?, couponCyclesRemaining: Int?): Boolean {
        if (couponId == null) return false
        if (couponCyclesRemaining == null) return true
        return couponCyclesRemaining > 0
    }

    /**
     * Remaining-period upgrade charge: (target − current) × remainingFraction of the cycle.
     * Floors at 0 (same or cheaper plan is not an upgrade charge).
     */
    fun prorataUpgradeCents(
        currentPriceCents: Long,
        targetPriceCents: Long,
        now: Instant,
        currentPeriodEnd: Instant,
        cycle: SubscriptionCycle,
    ): Long {
        val delta = targetPriceCents - currentPriceCents
        if (delta <= 0L) return 0L
        if (!now.isBefore(currentPeriodEnd)) return delta
        val remaining = Duration.between(now, currentPeriodEnd).seconds.coerceAtLeast(0)
        val total = cycleLengthSeconds(currentPeriodEnd, cycle).coerceAtLeast(1)
        return max(0L, delta * remaining / total)
    }

    private fun cycleLengthSeconds(periodEnd: Instant, cycle: SubscriptionCycle): Long {
        val end = periodEnd.atZone(ZoneOffset.UTC)
        val start = when (cycle) {
            SubscriptionCycle.MONTHLY -> end.minusMonths(1)
            SubscriptionCycle.ANNUAL -> end.minusYears(1)
        }
        return Duration.between(start.toInstant(), periodEnd).seconds
    }

    fun initialPeriodEnd(now: Instant, cycle: SubscriptionCycle): Instant {
        val zoned = now.atZone(ZoneOffset.UTC)
        return when (cycle) {
            SubscriptionCycle.MONTHLY -> zoned.plusMonths(1).toInstant()
            SubscriptionCycle.ANNUAL -> zoned.plusYears(1).toInstant()
        }
    }
}
