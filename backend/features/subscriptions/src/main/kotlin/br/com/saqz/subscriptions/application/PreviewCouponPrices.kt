package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.StoreListPrices
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import java.util.concurrent.ConcurrentHashMap

data class CouponPricePreviewRow(
    val plan: String,
    val cycle: SubscriptionCycle,
    val siteListPriceCents: Long,
    val sitePriceCents: Long,
    val storeListPriceCents: Long?,
    val googlePlayPriceCents: Long?,
    val appStorePriceCents: Long?,
    /** true quando a Apple não pôde ser consultada e o preço é o alvo, não o price point. */
    val appStoreEstimated: Boolean,
)

data class CouponPricePreview(
    val stores: Boolean,
    /** Motivo de o cupom não ir às lojas como está; null quando vai (ou é só do site). */
    val storeRejection: String?,
    val rows: List<CouponPricePreviewRow>,
)

/** Prévia do admin: quanto cada plataforma cobra por produto com o desconto. */
class PreviewCouponPrices(private val appStore: AppStoreOffersApi?) {
    private val pricePoints = ConcurrentHashMap<String, List<AppStorePricePoint>>()

    fun execute(
        discountPercent: Int,
        durationCycles: Int?,
        products: Set<CouponProduct>,
        maxUses: Int?,
        validUntil: java.time.Instant? = null,
        now: java.time.Instant? = null,
    ): CouponPricePreview {
        val covered = if (products.isEmpty()) CouponProduct.ALL else CouponProduct.ALL.filter { it in products }
        val stores = durationCycles != null
        val rejection = if (stores) CouponCampaignRules.storeRejection(discountPercent, durationCycles, covered, maxUses, validUntil, now) else null
        val rows = covered.map { product ->
            val siteList = ValidateCoupon.listPriceCents(product.plan, product.cycle)
            val target = CouponCampaignRules.appStoreTargetCents(product, discountPercent)
            val apple = if (stores) applePrice(product, target) else null
            CouponPricePreviewRow(
                plan = product.plan.name,
                cycle = product.cycle,
                siteListPriceCents = siteList,
                sitePriceCents = ValidateCoupon.discountedPriceCents(siteList, discountPercent),
                storeListPriceCents = StoreListPrices.cents(product).takeIf { stores },
                googlePlayPriceCents = CouponCampaignRules.googlePlayPriceCents(product, discountPercent).takeIf { stores },
                appStorePriceCents = apple?.first,
                appStoreEstimated = apple?.second ?: false,
            )
        }
        return CouponPricePreview(stores, rejection, rows)
    }

    private fun applePrice(product: CouponProduct, target: Long): Pair<Long, Boolean> {
        val api = appStore ?: return target to true
        return try {
            val productId = AppStoreProduct.of(product.plan, product.cycle).productId
            val points = pricePoints.getOrPut(productId) { api.brazilPricePoints(api.subscriptionId(productId)) }
            CouponCampaignRules.nearestPricePoint(points, target)?.let { it.customerPriceCents to false } ?: (target to true)
        } catch (_: CouponStoreException) {
            target to true
        }
    }
}
