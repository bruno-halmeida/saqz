package br.com.saqz.subscriptions.presentation.appstore

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.port.AppStoreProduct
import br.com.saqz.subscriptions.domain.port.AppStoreProductsResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchaseResult
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.presentation.changeplan.benefits
import kotlinx.coroutines.launch

/**
 * Compra da assinatura pela App Store. O preço vem do StoreKit; plano, ciclo e benefícios vêm
 * de `GET /plans`; quem diz se a conta tem acesso é a resposta do backend à transação.
 */
class AppStorePaywallViewModel(
    private val port: AppStorePurchasesPort,
    private val sync: AppStoreTransactionSync,
    private val appStore: AppStoreSubscriptionGateway,
    private val subscriptions: SubscriptionGateway,
) : MviViewModel<AppStorePaywallState, AppStorePaywallIntent, AppStorePaywallEffect>(AppStorePaywallState()) {

    private var loadGeneration = 0

    init {
        load()
        // Ask to Buy aprovado ou compra concluída fora desta tela chegam por aqui.
        viewModelScope.launch {
            sync.deliveries.collect { subscription -> if (subscription.entitled) subscribed() }
        }
    }

    override fun handleIntent(intent: AppStorePaywallIntent) {
        when (intent) {
            AppStorePaywallIntent.Retry -> if (state.value.phase == AppStorePaywallPhase.LoadFailed) load()
            is AppStorePaywallIntent.SelectCycle -> if (!state.value.isBusy) update { it.copy(cycle = intent.cycle) }
            is AppStorePaywallIntent.Purchase -> purchase(intent.productId)
            AppStorePaywallIntent.Restore -> restore()
            AppStorePaywallIntent.RetryConfirmation -> retryConfirmation()
            AppStorePaywallIntent.DismissNotice -> update { it.copy(notice = null) }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(phase = AppStorePaywallPhase.Loading, notice = null) }
        viewModelScope.launch {
            val next = loadPhase()
            if (generation != loadGeneration) return@launch
            update { it.copy(phase = next.phase, plans = next.plans) }
        }
    }

    private suspend fun loadPhase(): LoadedPaywall {
        if (!port.canMakeAppStorePayments()) return LoadedPaywall(AppStorePaywallPhase.Unavailable)
        val current = when (val result = subscriptions.mySubscription()) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> if (result.error == SubscriptionError.NotFound) null else return LoadFailed
        }
        // Quem já assina pela web não compra de novo aqui: seriam duas cobranças.
        val webSubscriber = current != null && current.entitled && current.provider == SubscriptionProvider.Asaas
        return if (webSubscriber) LoadedPaywall(AppStorePaywallPhase.Unavailable) else loadCatalog(current)
    }

    private suspend fun loadCatalog(current: MySubscription?): LoadedPaywall {
        val catalog = when (val result = subscriptions.listPlans()) {
            is SaqzResult.Success -> result.value.filter { it.appStoreProductIds != null }
            is SaqzResult.Failure -> return LoadFailed
        }
        val ids = catalog.flatMap { listOfNotNull(it.appStoreProductIds?.monthly, it.appStoreProductIds?.annual) }
        val products = when (val result = if (ids.isEmpty()) null else port.products(ids)) {
            null -> emptyMap()
            is AppStoreProductsResult.Loaded -> result.products.associateBy(AppStoreProduct::id)
            is AppStoreProductsResult.Failed -> return LoadFailed
        }
        val plans = catalog.mapNotNull { it.toPaywallPlan(products, current) }
        return LoadedPaywall(if (plans.isEmpty()) AppStorePaywallPhase.Unavailable else AppStorePaywallPhase.Ready, plans)
    }

    private fun purchase(productId: String) {
        val current = state.value
        if (current.phase != AppStorePaywallPhase.Ready || current.isBusy) return
        val offer = current.plans.firstNotNullOfOrNull { plan ->
            listOfNotNull(plan.monthly, plan.annual).firstOrNull { it.productId == productId }
        } ?: return
        if (offer.isCurrent) return
        update { it.copy(purchasingProductId = productId, notice = null) }
        viewModelScope.launch {
            val notice = when (val token = appStore.appAccountToken()) {
                is SaqzResult.Failure -> AppStorePaywallNotice.PurchaseFailed
                is SaqzResult.Success -> when (val result = port.purchase(productId, token.value)) {
                    is AppStorePurchaseResult.Purchased -> sync.deliver(result.transaction).toNotice()
                    AppStorePurchaseResult.Pending -> AppStorePaywallNotice.Pending
                    AppStorePurchaseResult.Cancelled -> null
                    is AppStorePurchaseResult.Failed -> AppStorePaywallNotice.PurchaseFailed
                }
            }
            update { it.copy(purchasingProductId = null, notice = notice ?: it.notice) }
        }
    }

    private fun restore() {
        val current = state.value
        if (current.phase != AppStorePaywallPhase.Ready || current.isBusy) return
        update { it.copy(isRestoring = true, notice = null) }
        viewModelScope.launch {
            val notice = when (val outcome = sync.restore()) {
                AppStoreRestoreOutcome.Failed -> AppStorePaywallNotice.RestoreFailed
                is AppStoreRestoreOutcome.Restored -> outcome.deliveries.toNotice()
            }
            update { it.copy(isRestoring = false, notice = notice ?: it.notice) }
        }
    }

    private fun retryConfirmation() {
        val current = state.value
        if (current.notice != AppStorePaywallNotice.ConfirmationPending || current.isBusy) return
        update { it.copy(isConfirming = true) }
        viewModelScope.launch {
            val notice = sync.drainUnfinished().toNotice() ?: AppStorePaywallNotice.ConfirmationPending
            update { it.copy(isConfirming = false, notice = notice) }
        }
    }

    /** Nulo quando a entrega deu acesso: a tela segue pelo efeito, sem aviso. */
    private fun AppStoreDelivery.toNotice(): AppStorePaywallNotice? = when (this) {
        is AppStoreDelivery.Delivered -> if (subscription.entitled) {
            subscribed()
            null
        } else {
            AppStorePaywallNotice.Invalid
        }
        AppStoreDelivery.OwnedByAnotherAccount -> AppStorePaywallNotice.OwnedByAnotherAccount
        AppStoreDelivery.Rejected -> AppStorePaywallNotice.Invalid
        is AppStoreDelivery.Deferred -> AppStorePaywallNotice.ConfirmationPending
    }

    /**
     * Restaurar devolve todas as assinaturas do Apple ID: basta uma com acesso. Sem nenhuma,
     * a mais informativa ganha — outra conta, depois confirmação pendente, depois "nada".
     */
    private fun List<AppStoreDelivery>.toNotice(): AppStorePaywallNotice? = when {
        any { it is AppStoreDelivery.Delivered && it.subscription.entitled } -> {
            subscribed()
            null
        }
        any { it == AppStoreDelivery.OwnedByAnotherAccount } -> AppStorePaywallNotice.OwnedByAnotherAccount
        any { it is AppStoreDelivery.Deferred } -> AppStorePaywallNotice.ConfirmationPending
        else -> AppStorePaywallNotice.NothingToRestore
    }

    private fun subscribed() {
        if (state.value.isSubscribed) return
        update { it.copy(isSubscribed = true, notice = null) }
        emit(AppStorePaywallEffect.Subscribed)
    }

    private class LoadedPaywall(val phase: AppStorePaywallPhase, val plans: List<AppStorePaywallPlanUi> = emptyList())

    private companion object {
        val LoadFailed = LoadedPaywall(AppStorePaywallPhase.LoadFailed)
    }
}

private fun PlanCatalogItem.toPaywallPlan(
    products: Map<String, AppStoreProduct>,
    current: MySubscription?,
): AppStorePaywallPlanUi? {
    val ids = appStoreProductIds ?: return null
    val ownsAppStorePlan = current != null && current.entitled && current.provider == SubscriptionProvider.AppStore
    fun offer(productId: String, cycle: SubscriptionCycle) = products[productId]?.let { product ->
        AppStorePaywallOfferUi(
            productId = product.id,
            displayPrice = product.displayPrice,
            isCurrent = ownsAppStorePlan && current?.plan == id && current.cycle == cycle,
        )
    }
    val monthly = offer(ids.monthly, SubscriptionCycle.Monthly)
    val annual = offer(ids.annual, SubscriptionCycle.Annual)
    if (monthly == null && annual == null) return null
    return AppStorePaywallPlanUi(plan = id, name = id.name, benefits = benefits(), monthly = monthly, annual = annual)
}
