package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import java.time.Clock
import java.time.Instant

sealed class ValidateCouponResult {
    data class Applied(
        val code: String,
        val planId: Plan,
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
}

class ValidateCoupon(
    private val coupons: CouponRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun execute(code: String, planId: String): ValidateCouponResult {
        val plan = runCatching { Plan.valueOf(planId.trim().uppercase()) }.getOrNull()
            ?: return ValidateCouponResult.InvalidPlan
        val normalized = code.trim()
        if (normalized.isEmpty()) return ValidateCouponResult.NotFound

        val coupon = coupons.findByCode(normalized) ?: return ValidateCouponResult.NotFound
        val now = clock.instant()
        val validUntil = coupon.validUntil
        if (validUntil != null && !validUntil.isAfter(now)) {
            return ValidateCouponResult.Expired(code = coupon.code, validUntil = validUntil)
        }

        val listPriceCents = plan.monthlyPriceCents
        val finalPriceCents = discountedPriceCents(listPriceCents, coupon.discountPercent)
        return ValidateCouponResult.Applied(
            code = coupon.code,
            planId = plan,
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
    }
}
