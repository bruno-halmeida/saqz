package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOffer
import java.util.UUID

interface CouponRepository {
    fun findByCode(code: String): Coupon?

    fun findById(couponId: UUID): Coupon?

    fun hasRedemption(couponId: UUID, userId: UUID): Boolean

    /** Idempotente: a mesma pessoa conta uma vez por cupom (a primeira plataforma fica). */
    fun saveRedemption(redemption: CouponRedemption)

    /** Usos do cupom em todas as plataformas, para o limite de usos. */
    fun countRedemptions(couponId: UUID): Int = 0
}

/** Ofertas dos cupons nas lojas (`coupon_store_offers`). */
interface CouponStoreOfferRepository {
    fun insertAll(offers: List<CouponStoreOffer>)

    fun findByCoupon(couponId: UUID): List<CouponStoreOffer>

    fun find(couponId: UUID, store: CouponStore, plan: String, cycle: String): CouponStoreOffer?

    /** Ofertas que o job precisa mexer agora (pendentes, criando, esperando a loja, desativando). */
    fun findWork(now: java.time.Instant, limit: Int): List<CouponStoreOffer>

    fun save(offer: CouponStoreOffer)

    /** Oferta do Play pela linha da assinatura v2 (`offerDetails.offerId`). */
    fun findByGooglePlayOffer(productId: String, basePlanId: String, offerId: String): CouponStoreOffer?

    /** Código de oferta da Apple pelo `offerIdentifier` da transação (nome da oferta = código personalizado). */
    fun findByAppStoreOffer(productId: String, offerIdentifier: String): CouponStoreOffer?
}

/** Histórico de assinatura paga para "quem pode usar" o cupom. */
interface CouponEligibilityLookup {
    /** Teve assinatura paga em qualquer plataforma (teste grátis não conta). */
    fun hasPaidHistory(userId: UUID): Boolean

    /** Tem acesso pago agora em qualquer plataforma. */
    fun hasPaidAccessNow(userId: UUID): Boolean
}

class PaidHistoryCouponEligibility(
    private val history: PaidSubscriptionHistory,
    private val plans: SubscriptionPlanLookup,
) : CouponEligibilityLookup {
    override fun hasPaidHistory(userId: UUID): Boolean = history.hasPaidHistory(userId)

    override fun hasPaidAccessNow(userId: UUID): Boolean = plans.findEntitlingPlan(userId) != null
}
