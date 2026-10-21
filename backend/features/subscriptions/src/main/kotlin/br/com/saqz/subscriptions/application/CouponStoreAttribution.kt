package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreTransaction
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import java.time.Instant
import java.util.UUID

/**
 * Compra das lojas que veio de uma oferta de cupom vira resgate da campanha, com a plataforma.
 * Roda dentro da transação que grava a compra; repetir (reenvio, webhook) não conta de novo.
 */
class CouponStoreAttribution(
    private val offers: CouponStoreOfferRepository,
    private val coupons: CouponRepository,
) {
    fun appStore(transaction: AppStoreTransaction, ownerUserId: UUID) {
        val identifier = transaction.offerCodeIdentifier ?: return
        val offer = offers.findByAppStoreOffer(transaction.productId, identifier) ?: return
        coupons.saveRedemption(CouponRedemption(offer.couponId, ownerUserId, transaction.purchaseDate, CouponPlatform.IOS))
    }

    fun googlePlay(purchase: GooglePlayPurchase, product: GooglePlayProduct, ownerUserId: UUID, now: Instant) {
        val offerId = purchase.offerId ?: return
        val offer = offers.findByGooglePlayOffer(product.productId, product.basePlanId, offerId) ?: return
        coupons.saveRedemption(CouponRedemption(offer.couponId, ownerUserId, now, CouponPlatform.ANDROID))
    }
}
