package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class ValidateCouponResult {
    data class Applied(
        val code: String,
        val planId: Plan,
        val cycle: SubscriptionCycle,
        val discountPercent: Int,
        val listPriceCents: Long,
        val finalPriceCents: Long,
        val campaign: String? = null,
        val durationCycles: Int? = null,
        val storePriceCents: Long? = null,
        val googlePlayOfferId: String? = null,
        val appStoreOfferCode: String? = null,
    ) : ValidateCouponResult()

    data object NotFound : ValidateCouponResult()

    data class Expired(
        val code: String,
        val validUntil: Instant,
    ) : ValidateCouponResult()

    /** planId que não é um plano (pedido malformado). */
    data object InvalidPlan : ValidateCouponResult()

    /** cycle que não é um ciclo (pedido malformado). */
    data object InvalidCycle : ValidateCouponResult()

    /** O cupom não vale para este plano. */
    data object PlanNotCovered : ValidateCouponResult()

    /** O cupom vale para o plano, mas não neste ciclo. */
    data object CycleNotCovered : ValidateCouponResult()

    data object NotEligible : ValidateCouponResult()

    data object Exhausted : ValidateCouponResult()

    /** Cupom só do site, ou o plano/ciclo não está no cupom para essa loja. */
    data object NotAvailableOnPlatform : ValidateCouponResult()

    /** A oferta ainda não está ativa nessa loja. */
    data object PendingOnPlatform : ValidateCouponResult()
}

class ValidateCoupon(
    private val coupons: CouponRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val offers: CouponStoreOfferRepository? = null,
    private val eligibility: CouponEligibilityLookup? = null,
) {
    fun execute(
        code: String,
        planId: String,
        cycle: String? = null,
        platform: CouponPlatform = CouponPlatform.WEB,
        userId: UUID? = null,
    ): ValidateCouponResult {
        val plan = runCatching { Plan.valueOf(planId.trim().uppercase()) }.getOrNull()
            ?: return ValidateCouponResult.InvalidPlan
        val subscriptionCycle = parseCycle(cycle) ?: return ValidateCouponResult.InvalidCycle
        val normalized = code.trim()
        if (normalized.isEmpty()) return ValidateCouponResult.NotFound

        val coupon = coupons.findByCode(normalized) ?: return ValidateCouponResult.NotFound
        val now = clock.instant()
        closed(coupon, now)?.let { return it }

        if (platform != CouponPlatform.WEB && !coupon.stores) return ValidateCouponResult.NotAvailableOnPlatform
        if (!coupon.covers(plan, subscriptionCycle)) {
            return when {
                platform != CouponPlatform.WEB -> ValidateCouponResult.NotAvailableOnPlatform
                coupon.products.none { it.plan == plan } -> ValidateCouponResult.PlanNotCovered
                else -> ValidateCouponResult.CycleNotCovered
            }
        }
        usage(coupon, userId)?.let { return it }

        val listPriceCents = listPriceCents(plan, subscriptionCycle)
        val applied = ValidateCouponResult.Applied(
            code = coupon.code,
            planId = plan,
            cycle = subscriptionCycle,
            discountPercent = coupon.discountPercent,
            listPriceCents = listPriceCents,
            finalPriceCents = discountedPriceCents(listPriceCents, coupon.discountPercent),
            campaign = coupon.campaign,
            durationCycles = coupon.durationCycles,
        )
        if (platform == CouponPlatform.WEB) return applied

        val store = if (platform == CouponPlatform.ANDROID) CouponStore.GOOGLE_PLAY else CouponStore.APP_STORE
        val offer = offers?.find(coupon.id, store, plan.name, subscriptionCycle.name)
            ?: return ValidateCouponResult.NotAvailableOnPlatform
        if (offer.state != CouponStoreOfferState.ACTIVE) return ValidateCouponResult.PendingOnPlatform
        return applied.copy(
            listPriceCents = br.com.saqz.subscriptions.domain.StoreListPrices.cents(offer.product),
            finalPriceCents = offer.storePriceCents ?: applied.finalPriceCents,
            storePriceCents = offer.storePriceCents,
            googlePlayOfferId = offer.offerId.takeIf { store == CouponStore.GOOGLE_PLAY },
            appStoreOfferCode = offer.customCode.takeIf { store == CouponStore.APP_STORE },
        )
    }

    private fun closed(coupon: Coupon, now: Instant): ValidateCouponResult? {
        val endedAt = coupon.endedAt
        val validUntil = coupon.validUntil
        return when {
            validUntil != null && !validUntil.isAfter(now) -> ValidateCouponResult.Expired(coupon.code, validUntil)
            endedAt != null && !endedAt.isAfter(now) -> ValidateCouponResult.Expired(coupon.code, endedAt)
            else -> null
        }
    }

    /** Quem já usou não usa de novo; limite de usos e "quem pode usar" valem para quem ainda não usou. */
    private fun usage(coupon: Coupon, userId: UUID?): ValidateCouponResult? {
        if (userId != null && coupons.hasRedemption(coupon.id, userId)) return ValidateCouponResult.NotEligible
        val max = coupon.maxUses
        if (max != null && coupons.countRedemptions(coupon.id) >= max) return ValidateCouponResult.Exhausted
        if (CouponCampaignRules.eligibility(coupon, userId, eligibility) == CouponCampaignRules.Eligibility.NotEligible) {
            return ValidateCouponResult.NotEligible
        }
        return null
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
