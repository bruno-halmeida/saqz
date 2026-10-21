package br.com.saqz.subscriptions.presentation.googleplay

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.coupon.CampaignCoupon
import br.com.saqz.subscriptions.domain.coupon.CouponPlatform
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubscriptionGateway
import br.com.saqz.subscriptions.domain.port.GooglePlayProduct
import br.com.saqz.subscriptions.domain.port.GooglePlayProductsResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesPort
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.presentation.store.CouponCheckout
import br.com.saqz.subscriptions.presentation.store.PaywallStore
import br.com.saqz.subscriptions.presentation.store.PaywallStoreKind
import br.com.saqz.subscriptions.presentation.store.PlanCycle
import br.com.saqz.subscriptions.presentation.store.StoreDelivery
import br.com.saqz.subscriptions.presentation.store.StoreOffer
import br.com.saqz.subscriptions.presentation.store.StorePurchaseOutcome
import br.com.saqz.subscriptions.presentation.store.StoreRestoreOutcome
import kotlinx.coroutines.flow.SharedFlow

/**
 * Play Billing atrás da tela de compra. No Play cada plano é uma assinatura com dois planos
 * base; o id da oferta é `productId:basePlanId`, e o `offerToken` fica guardado aqui até a compra.
 */
class GooglePlayPaywallStore(
    private val port: GooglePlayPurchasesPort,
    private val sync: GooglePlayPurchaseSync,
    private val gateway: GooglePlaySubscriptionGateway,
) : PaywallStore {
    private val purchasable = mutableMapOf<String, PlayOffer>()
    private val products = mutableMapOf<String, GooglePlayProduct>()

    override val kind = PaywallStoreKind.GooglePlay
    override val provider = SubscriptionProvider.GooglePlay
    override val couponPlatform = CouponPlatform.Android
    override val deliveries: SharedFlow<MySubscription> get() = sync.deliveries

    override fun canPurchase(): Boolean = port.canMakeGooglePlayPayments()

    override suspend fun offers(catalog: List<PlanCatalogItem>): Map<PlanCycle, StoreOffer>? {
        val productIds = catalog.mapNotNull { it.googlePlay?.productId }.distinct()
        if (productIds.isEmpty()) return emptyMap()
        when (val result = port.products(productIds)) {
            is GooglePlayProductsResult.Loaded -> result.products.forEach { products[it.productId] = it }
            is GooglePlayProductsResult.Failed -> return null
        }
        return buildMap {
            catalog.forEach { item ->
                val ids = item.googlePlay ?: return@forEach
                val product = products[ids.productId] ?: return@forEach
                offer(product, ids.monthlyBasePlanId)?.let { put(PlanCycle(item.id, SubscriptionCycle.Monthly), it) }
                offer(product, ids.annualBasePlanId)?.let { put(PlanCycle(item.id, SubscriptionCycle.Annual), it) }
            }
        }
    }

    private fun offer(product: GooglePlayProduct, basePlanId: String): StoreOffer? {
        val basePlan = product.basePlans.firstOrNull { it.basePlanId == basePlanId } ?: return null
        val id = "${product.productId}:$basePlanId"
        purchasable[id] = PlayOffer(product.productId, basePlan.offerToken)
        return StoreOffer(id = id, displayPrice = basePlan.formattedPrice)
    }

    /**
     * A oferta do cupom é criada no Play pelo backend e leva alguns minutos para aparecer no
     * `ProductDetails`: sem ela na lista (nem depois de reler o produto), nada de comprar sem o
     * desconto. O preço mostrado é o da primeira fase da oferta, como o Play vai cobrar.
     */
    override suspend fun couponCheckout(offerId: String, coupon: CampaignCoupon): CouponCheckout {
        val playOfferId = coupon.googlePlayOfferId ?: return CouponCheckout.NotReady
        val base = purchasable[offerId] ?: return CouponCheckout.NotReady
        val basePlanId = offerId.substringAfter(':')
        fun find() = products[base.productId]?.offers?.firstOrNull { it.basePlanId == basePlanId && it.offerId == playOfferId }
        val offer = find() ?: run {
            (port.products(listOf(base.productId)) as? GooglePlayProductsResult.Loaded)
                ?.products?.forEach { products[it.productId] = it }
            find()
        } ?: return CouponCheckout.NotReady
        val id = "$offerId:$playOfferId"
        purchasable[id] = PlayOffer(base.productId, offer.offerToken)
        return CouponCheckout.InApp(StoreOffer(id = id, displayPrice = offer.formattedPrice))
    }

    override suspend fun purchase(offerId: String): StorePurchaseOutcome {
        val offer = purchasable[offerId] ?: return StorePurchaseOutcome.Failed
        val account = when (val result = gateway.obfuscatedAccountId()) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> return StorePurchaseOutcome.Failed
        }
        return when (val result = port.purchase(offer.productId, offer.offerToken, account)) {
            is GooglePlayPurchaseResult.Purchased -> if (result.purchase.pending) {
                StorePurchaseOutcome.Pending
            } else {
                StorePurchaseOutcome.Completed(sync.deliver(result.purchase))
            }
            GooglePlayPurchaseResult.Pending -> StorePurchaseOutcome.Pending
            GooglePlayPurchaseResult.Cancelled -> StorePurchaseOutcome.Cancelled
            GooglePlayPurchaseResult.OwnedByAnotherAccount -> StorePurchaseOutcome.Completed(StoreDelivery.OwnedByAnotherAccount)
            is GooglePlayPurchaseResult.Failed -> StorePurchaseOutcome.Failed
        }
    }

    override suspend fun restore(): StoreRestoreOutcome = sync.restore()

    override suspend fun retryPending(): List<StoreDelivery> = sync.drainUnacknowledged()

    private data class PlayOffer(val productId: String, val offerToken: String)
}
