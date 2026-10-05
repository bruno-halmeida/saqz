package br.com.saqz.subscriptions.presentation.googleplay

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubscriptionGateway
import br.com.saqz.subscriptions.domain.port.GooglePlayProduct
import br.com.saqz.subscriptions.domain.port.GooglePlayProductsResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesPort
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
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

    override val kind = PaywallStoreKind.GooglePlay
    override val provider = SubscriptionProvider.GooglePlay
    override val deliveries: SharedFlow<MySubscription> get() = sync.deliveries

    override fun canPurchase(): Boolean = port.canMakeGooglePlayPayments()

    override suspend fun offers(catalog: List<PlanCatalogItem>): Map<PlanCycle, StoreOffer>? {
        val productIds = catalog.mapNotNull { it.googlePlay?.productId }.distinct()
        if (productIds.isEmpty()) return emptyMap()
        val products = when (val result = port.products(productIds)) {
            is GooglePlayProductsResult.Loaded -> result.products.associateBy(GooglePlayProduct::productId)
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
            is GooglePlayPurchaseResult.Failed -> StorePurchaseOutcome.Failed
        }
    }

    override suspend fun restore(): StoreRestoreOutcome = sync.restore()

    override suspend fun retryPending(): List<StoreDelivery> = sync.drainUnacknowledged()

    private data class PlayOffer(val productId: String, val offerToken: String)
}
