package br.com.saqz.subscriptions.presentation.appstore

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.presentation.changeplan.benefits
import br.com.saqz.subscriptions.presentation.store.PaywallStore
import br.com.saqz.subscriptions.presentation.store.PlanCycle
import br.com.saqz.subscriptions.presentation.store.StoreDelivery
import br.com.saqz.subscriptions.presentation.store.StoreOffer
import br.com.saqz.subscriptions.presentation.store.StorePurchaseOutcome
import br.com.saqz.subscriptions.presentation.store.StoreRestoreOutcome
import kotlinx.coroutines.launch

/**
 * Compra da assinatura pela loja do aparelho — App Store no iOS, Google Play no Android. O
 * preço vem da loja; plano, ciclo e benefícios vêm de `GET /plans`; quem diz se a conta tem
 * acesso é a resposta do backend à compra.
 */
class AppStorePaywallViewModel(
    private val store: PaywallStore,
    private val subscriptions: SubscriptionGateway,
) : MviViewModel<AppStorePaywallState, AppStorePaywallIntent, AppStorePaywallEffect>(
    AppStorePaywallState(store = store.kind),
) {
    constructor(
        port: AppStorePurchasesPort,
        sync: AppStoreTransactionSync,
        appStore: AppStoreSubscriptionGateway,
        subscriptions: SubscriptionGateway,
    ) : this(AppStorePaywallStore(port, sync, appStore), subscriptions)

    private var loadGeneration = 0

    init {
        load()
        // Ask to Buy aprovado, pagamento pendente que caiu ou compra concluída fora desta tela.
        viewModelScope.launch {
            store.deliveries.collect { subscription -> if (subscription.entitled) subscribed() }
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
        if (!store.canPurchase()) return LoadedPaywall(AppStorePaywallPhase.Unavailable)
        val current = when (val result = subscriptions.mySubscription()) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> if (result.error == SubscriptionError.NotFound) null else return LoadFailed
        }
        // Quem já assina pela web ou pela outra loja não compra de novo aqui: seriam duas cobranças.
        val elsewhere = current != null && current.entitled && current.provider != store.provider
        return if (elsewhere) LoadedPaywall(AppStorePaywallPhase.Unavailable) else loadCatalog(current)
    }

    private suspend fun loadCatalog(current: MySubscription?): LoadedPaywall {
        val catalog = when (val result = subscriptions.listPlans()) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> return LoadFailed
        }
        val offers = store.offers(catalog) ?: return LoadFailed
        val plans = catalog.mapNotNull { it.toPaywallPlan(offers, current?.takeIf { sub -> sub.provider == store.provider }) }
        return LoadedPaywall(if (plans.isEmpty()) AppStorePaywallPhase.Unavailable else AppStorePaywallPhase.Ready, plans)
    }

    private fun purchase(offerId: String) {
        val current = state.value
        if (current.phase != AppStorePaywallPhase.Ready || current.isBusy) return
        val offer = current.plans.firstNotNullOfOrNull { plan ->
            listOfNotNull(plan.monthly, plan.annual).firstOrNull { it.productId == offerId }
        } ?: return
        if (offer.isCurrent) return
        update { it.copy(purchasingProductId = offerId, notice = null) }
        viewModelScope.launch {
            val notice = when (val outcome = store.purchase(offerId)) {
                is StorePurchaseOutcome.Completed -> outcome.delivery.toNotice()
                StorePurchaseOutcome.Pending -> AppStorePaywallNotice.Pending
                StorePurchaseOutcome.Cancelled -> null
                StorePurchaseOutcome.Failed -> AppStorePaywallNotice.PurchaseFailed
            }
            update { it.copy(purchasingProductId = null, notice = notice ?: it.notice) }
        }
    }

    private fun restore() {
        val current = state.value
        if (current.phase != AppStorePaywallPhase.Ready || current.isBusy) return
        update { it.copy(isRestoring = true, notice = null) }
        viewModelScope.launch {
            val notice = when (val outcome = store.restore()) {
                StoreRestoreOutcome.Failed -> AppStorePaywallNotice.RestoreFailed
                is StoreRestoreOutcome.Restored -> outcome.deliveries.toNotice()
            }
            update { it.copy(isRestoring = false, notice = notice ?: it.notice) }
        }
    }

    private fun retryConfirmation() {
        val current = state.value
        if (current.notice != AppStorePaywallNotice.ConfirmationPending || current.isBusy) return
        update { it.copy(isConfirming = true) }
        viewModelScope.launch {
            val notice = store.retryPending().toNotice() ?: AppStorePaywallNotice.ConfirmationPending
            update { it.copy(isConfirming = false, notice = notice) }
        }
    }

    /** Nulo quando a entrega deu acesso: a tela segue pelo efeito, sem aviso. */
    private fun StoreDelivery.toNotice(): AppStorePaywallNotice? = when (this) {
        is StoreDelivery.Delivered -> if (subscription.entitled) {
            subscribed()
            null
        } else {
            AppStorePaywallNotice.Invalid
        }
        StoreDelivery.OwnedByAnotherAccount -> AppStorePaywallNotice.OwnedByAnotherAccount
        StoreDelivery.Rejected -> AppStorePaywallNotice.Invalid
        is StoreDelivery.Deferred -> AppStorePaywallNotice.ConfirmationPending
    }

    /**
     * Restaurar devolve todas as assinaturas da conta da loja: basta uma com acesso. Sem
     * nenhuma, a mais informativa ganha — outra conta, depois confirmação pendente, depois "nada".
     */
    private fun List<StoreDelivery>.toNotice(): AppStorePaywallNotice? = when {
        any { it is StoreDelivery.Delivered && it.subscription.entitled } -> {
            subscribed()
            null
        }
        any { it == StoreDelivery.OwnedByAnotherAccount } -> AppStorePaywallNotice.OwnedByAnotherAccount
        any { it is StoreDelivery.Deferred } -> AppStorePaywallNotice.ConfirmationPending
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

/** [current] é a assinatura desta mesma loja (ou nula): é ela que marca o plano atual. */
private fun PlanCatalogItem.toPaywallPlan(
    offers: Map<PlanCycle, StoreOffer>,
    current: MySubscription?,
): AppStorePaywallPlanUi? {
    val ownsStorePlan = current != null && current.entitled
    fun offer(cycle: SubscriptionCycle) = offers[PlanCycle(id, cycle)]?.let { offer ->
        AppStorePaywallOfferUi(
            productId = offer.id,
            displayPrice = offer.displayPrice,
            isCurrent = ownsStorePlan && current?.plan == id && current.cycle == cycle,
        )
    }
    val monthly = offer(SubscriptionCycle.Monthly)
    val annual = offer(SubscriptionCycle.Annual)
    if (monthly == null && annual == null) return null
    return AppStorePaywallPlanUi(plan = id, name = id.name, benefits = benefits(), monthly = monthly, annual = annual)
}
