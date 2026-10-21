package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOffer
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import java.time.Instant
import java.util.UUID

internal class InMemoryCoupons(vararg coupons: Coupon) : CouponRepository {
    val coupons = coupons.associateBy { it.id }.toMutableMap()
    val redemptions = mutableListOf<CouponRedemption>()

    override fun findByCode(code: String) = coupons.values.firstOrNull { it.code.equals(code, ignoreCase = true) }
    override fun findById(couponId: UUID) = coupons[couponId]
    override fun hasRedemption(couponId: UUID, userId: UUID) = redemptions.any { it.couponId == couponId && it.userId == userId }
    override fun saveRedemption(redemption: CouponRedemption) {
        if (!hasRedemption(redemption.couponId, redemption.userId)) redemptions += redemption
    }
    override fun countRedemptions(couponId: UUID) = redemptions.count { it.couponId == couponId }
}

internal class InMemoryOffers(offers: List<CouponStoreOffer> = emptyList()) : CouponStoreOfferRepository {
    val offers = offers.associateBy { it.id }.toMutableMap()
    val saves = mutableListOf<CouponStoreOffer>()

    override fun insertAll(offers: List<CouponStoreOffer>) = offers.forEach { this.offers[it.id] = it }
    override fun findByCoupon(couponId: UUID) = offers.values.filter { it.couponId == couponId }
    override fun find(couponId: UUID, store: CouponStore, plan: String, cycle: String) = offers.values.firstOrNull {
        it.couponId == couponId && it.store == store && it.product.plan.name == plan && it.product.cycle.name == cycle
    }
    override fun findWork(now: Instant, limit: Int) = offers.values.filter {
        it.state in setOf(CouponStoreOfferState.ACTIVE, CouponStoreOfferState.PENDING, CouponStoreOfferState.CREATING, CouponStoreOfferState.WAITING_STORE, CouponStoreOfferState.DEACTIVATING) &&
            (it.nextAttemptAt == null || !it.nextAttemptAt!!.isAfter(now))
    }.take(limit)
    override fun save(offer: CouponStoreOffer) {
        offers[offer.id] = offer
        saves += offer
    }
    override fun findByGooglePlayOffer(productId: String, basePlanId: String, offerId: String) = offers.values.firstOrNull {
        it.store == CouponStore.GOOGLE_PLAY && it.productId == productId && it.basePlanId == basePlanId && it.offerId == offerId
    }
    override fun findByAppStoreOffer(productId: String, offerIdentifier: String) = offers.values.firstOrNull {
        it.store == CouponStore.APP_STORE && it.productId == productId && it.customCode.equals(offerIdentifier, ignoreCase = true)
    }
}

internal class FixedEligibility(private val paidBefore: Boolean, private val paidNow: Boolean) : CouponEligibilityLookup {
    override fun hasPaidHistory(userId: UUID) = paidBefore
    override fun hasPaidAccessNow(userId: UUID) = paidNow
}
