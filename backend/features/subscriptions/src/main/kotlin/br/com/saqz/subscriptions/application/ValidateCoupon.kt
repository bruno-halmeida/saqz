package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import java.time.Clock
import java.time.Instant

sealed class ValidateCouponResult {
    data class Applied(
        val code: String,
        val planId: Plan,
        val cycle: SubscriptionCycle,
        val discountPercent: Int,
        val listPriceCents: Long,
        val finalPriceCents: Long,
    ) : ValidateCouponResult()

    data object NotFound : ValidateCouponResult()

    data class Expired(
        val code: String,
        val validUntil: Instant,
    ) : ValidateCouponResult()

    data object InvalidPlan : ValidateCouponResult()

    data object InvalidCycle : ValidateCouponResult()
}

class ValidateCoupon(
    private val coupons: CouponRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun execute(
        code: String,
        planId: String,
        cycle: String? = null,
    ): ValidateCouponResult {
        val plan = runCatching { Plan.valueOf(planId.trim().uppercase()) }.getOrNull()
            ?: return ValidateCouponResult.InvalidPlan
        val subscriptionCycle = parseCycle(cycle) ?: return ValidateCouponResult.InvalidCycle
        val normalized = code.trim()
        if (normalized.isEmpty()) return ValidateCouponResult.NotFound

        val coupon = coupons.findByCode(normalized) ?: return ValidateCouponResult.NotFound
        val now = clock.instant()
        val validUntil = coupon.validUntil
        if (validUntil != null && !validUntil.isAfter(now)) {
            return ValidateCouponResult.Expired(code = coupon.code, validUntil = validUntil)
        }

        val listPriceCents = listPriceCents(plan, subscriptionCycle)
        val finalPriceCents = discountedPriceCents(listPriceCents, coupon.discountPercent)
        return ValidateCouponResult.Applied(
            code = coupon.code,
            planId = plan,
            cycle = subscriptionCycle,
            discountPercent = coupon.discountPercent,
            listPriceCents = listPriceCents,
            finalPriceCents = finalPriceCents,
        )
    }

    companion object {
        fun discountedPriceCents(listPriceCents: Long, discountPercent: Int): Long {
            val discounted = listPriceCents * (100 - discountPercent) / 100
            return discounted.coerceAtLeast(0)
        }

        fun listPriceCents(plan: Plan, cycle: SubscriptionCycle): Long = when (cycle) {
            SubscriptionCycle.MONTHLY -> plan.monthlyPriceCents
            SubscriptionCycle.ANNUAL -> plan.annualPriceCents
        }

        fun parseCycle(cycle: String?): SubscriptionCycle? {
            if (cycle.isNullOrBlank()) return SubscriptionCycle.MONTHLY
            return runCatching { SubscriptionCycle.valueOf(cycle.trim().uppercase()) }.getOrNull()
        }
    }
}
