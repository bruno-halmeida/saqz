package br.com.saqz.subscriptions.presentation.appstore

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.port.AppStoreProductsResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchaseResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
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

/** StoreKit atrás da tela de compra. O id da oferta é o product ID da App Store. */
class AppStorePaywallStore(
    private val port: AppStorePurchasesPort,
    private val sync: AppStoreTransactionSync,
    private val appStore: AppStoreSubscriptionGateway,
) : PaywallStore {
    override val kind = PaywallStoreKind.AppStore
    override val provider = SubscriptionProvider.AppStore
    override val deliveries: SharedFlow<MySubscription> get() = sync.deliveries

    override fun canPurchase(): Boolean = port.canMakeAppStorePayments()

    override suspend fun offers(catalog: List<PlanCatalogItem>): Map<PlanCycle, StoreOffer>? {
        val ids = catalog.flatMap { listOfNotNull(it.appStoreProductIds?.monthly, it.appStoreProductIds?.annual) }
        if (ids.isEmpty()) return emptyMap()
        val products = when (val result = port.products(ids)) {
            is AppStoreProductsResult.Loaded -> result.products.associateBy { it.id }
            is AppStoreProductsResult.Failed -> return null
        }
        return buildMap {
            catalog.forEach { item ->
                val ids = item.appStoreProductIds ?: return@forEach
                products[ids.monthly]?.let { put(PlanCycle(item.id, SubscriptionCycle.Monthly), StoreOffer(it.id, it.displayPrice)) }
                products[ids.annual]?.let { put(PlanCycle(item.id, SubscriptionCycle.Annual), StoreOffer(it.id, it.displayPrice)) }
            }
        }
    }

    override suspend fun purchase(offerId: String): StorePurchaseOutcome {
        val token = when (val result = appStore.appAccountToken()) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> return StorePurchaseOutcome.Failed
        }
        return when (val result = port.purchase(offerId, token)) {
            is AppStorePurchaseResult.Purchased -> StorePurchaseOutcome.Completed(sync.deliver(result.transaction).toStore())
            AppStorePurchaseResult.Pending -> StorePurchaseOutcome.Pending
            AppStorePurchaseResult.Cancelled -> StorePurchaseOutcome.Cancelled
            is AppStorePurchaseResult.Failed -> StorePurchaseOutcome.Failed
        }
    }

    override suspend fun restore(): StoreRestoreOutcome = when (val outcome = sync.restore()) {
        AppStoreRestoreOutcome.Failed -> StoreRestoreOutcome.Failed
        is AppStoreRestoreOutcome.Restored -> StoreRestoreOutcome.Restored(outcome.deliveries.map { it.toStore() })
    }

    override suspend fun retryPending(): List<StoreDelivery> = sync.drainUnfinished().map { it.toStore() }
}

internal fun AppStoreDelivery.toStore(): StoreDelivery = when (this) {
    is AppStoreDelivery.Delivered -> StoreDelivery.Delivered(subscription)
    AppStoreDelivery.OwnedByAnotherAccount -> StoreDelivery.OwnedByAnotherAccount
    AppStoreDelivery.Rejected -> StoreDelivery.Rejected
    is AppStoreDelivery.Deferred -> StoreDelivery.Deferred(error)
}
