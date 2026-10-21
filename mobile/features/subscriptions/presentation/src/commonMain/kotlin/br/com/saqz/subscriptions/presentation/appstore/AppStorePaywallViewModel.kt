package br.com.saqz.subscriptions.presentation.appstore

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubscriptionGateway
import br.com.saqz.subscriptions.domain.coupon.CampaignCoupon
import br.com.saqz.subscriptions.domain.coupon.CampaignCouponGateway
import br.com.saqz.subscriptions.domain.coupon.CouponPlatform
import br.com.saqz.subscriptions.domain.coupon.CouponRejection
import br.com.saqz.subscriptions.domain.coupon.CouponValidation
import br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.presentation.changeplan.benefits
import br.com.saqz.subscriptions.presentation.store.CouponCheckout
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
    private val coupons: CampaignCouponGateway,
) : MviViewModel<AppStorePaywallState, AppStorePaywallIntent, AppStorePaywallEffect>(
    AppStorePaywallState(store = store.kind),
) {
    constructor(
        port: AppStorePurchasesPort,
        sync: AppStoreTransactionSync,
        appStore: AppStoreSubscriptionGateway,
        subscriptions: SubscriptionGateway,
        coupons: CampaignCouponGateway,
    ) : this(AppStorePaywallStore(port, sync, appStore), subscriptions, coupons)

    private var loadGeneration = 0
    private var couponGeneration = 0

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
            is AppStorePaywallIntent.SelectCycle -> selectCycle(intent.cycle)
            is AppStorePaywallIntent.Purchase -> purchase(intent.productId)
            AppStorePaywallIntent.Restore -> restore()
            AppStorePaywallIntent.RetryConfirmation -> retryConfirmation()
            AppStorePaywallIntent.DismissNotice -> update { it.copy(notice = null) }
            AppStorePaywallIntent.OpenCoupon -> openCoupon()
            AppStorePaywallIntent.CloseCoupon -> {
                couponGeneration++
                update { it.copy(couponSheet = null) }
            }
            is AppStorePaywallIntent.CouponCodeChanged -> update { current ->
                current.copy(couponSheet = current.couponSheet?.copy(code = normalizeCouponCode(intent.code), message = null))
            }
            is AppStorePaywallIntent.CouponPlanChanged -> update { current ->
                current.copy(couponSheet = current.couponSheet?.copy(plan = intent.plan, message = null))
            }
            AppStorePaywallIntent.ApplyCoupon -> applyCoupon()
            AppStorePaywallIntent.PurchaseWithCoupon -> purchaseWithCoupon()
            AppStorePaywallIntent.Resumed -> resumed()
        }
    }

    private fun selectCycle(cycle: SubscriptionCycle) {
        val current = state.value
        if (current.isBusy || current.cycle == cycle) return
        update { it.copy(cycle = cycle) }
        // O cupom vale para um plano e um ciclo: no outro ciclo ele é validado de novo.
        val coupon = current.coupon ?: return
        update { it.copy(coupon = null, couponSheet = PaywallCouponSheet(code = coupon.code, plan = coupon.plan)) }
        applyCoupon()
    }

    private fun openCoupon() {
        val current = state.value
        if (current.phase != AppStorePaywallPhase.Ready || current.isBusy) return
        val plan = current.coupon?.plan ?: current.plans.firstOrNull { it.offer(current.cycle) != null }?.plan ?: return
        update { it.copy(couponSheet = PaywallCouponSheet(code = current.coupon?.code.orEmpty(), plan = plan)) }
    }

    private fun applyCoupon() {
        val current = state.value
        val sheet = current.couponSheet ?: return
        if (sheet.isValidating || sheet.code.isEmpty()) return
        val offer = current.plans.firstOrNull { it.plan == sheet.plan }?.offer(current.cycle)
        if (offer == null) {
            update { it.copy(couponSheet = sheet.copy(message = PaywallCouponMessage.InvalidCycle)) }
            return
        }
        val generation = ++couponGeneration
        update { it.copy(couponSheet = sheet.copy(isValidating = true, message = null)) }
        viewModelScope.launch {
            val result = validate(sheet.code, sheet.plan, current.cycle, offer.productId)
            if (generation != couponGeneration) return@launch
            when (result) {
                is CouponResult.Applied -> {
                    update { it.copy(coupon = result.coupon, couponSheet = null) }
                    SaqzAnalytics.event(
                        "coupon_applied",
                        "platform" to if (store.couponPlatform == CouponPlatform.Ios) "ios" else "android",
                        "plan" to result.coupon.plan.name.uppercase(),
                        "cycle" to if (result.coupon.cycle == SubscriptionCycle.Annual) "ANNUAL" else "MONTHLY",
                        "discount_percent" to result.coupon.discountPercent.toString(),
                    )
                }
                is CouponResult.Message -> update { state ->
                    state.copy(couponSheet = state.couponSheet?.copy(isValidating = false, message = result.message))
                }
            }
        }
    }

    private suspend fun validate(code: String, plan: Plan, cycle: SubscriptionCycle, offerId: String): CouponResult {
        val validation = when (val result = coupons.validate(code, plan, cycle, store.couponPlatform)) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> return CouponResult.Message(PaywallCouponMessage.Failed)
        }
        val coupon = when (validation) {
            is CouponValidation.Applied -> validation.coupon
            is CouponValidation.Rejected -> return CouponResult.Message(validation.reason.toMessage())
        }
        return when (val checkout = store.couponCheckout(offerId, coupon)) {
            CouponCheckout.NotReady -> CouponResult.Message(PaywallCouponMessage.Pending)
            is CouponCheckout.InApp -> CouponResult.Applied(
                coupon.toUi(plan, cycle).copy(discountedPrice = checkout.offer.displayPrice, purchaseOfferId = checkout.offer.id),
            )
            is CouponCheckout.Redeem -> CouponResult.Applied(coupon.toUi(plan, cycle).copy(redeemUrl = checkout.url))
        }
    }

    private fun purchaseWithCoupon() {
        val current = state.value
        val coupon = current.coupon?.takeIf { it.cycle == current.cycle } ?: return
        if (current.phase != AppStorePaywallPhase.Ready || current.isBusy) return
        coupon.purchaseOfferId?.let { return purchase(it) }
        val url = coupon.redeemUrl ?: return
        update { it.copy(awaitingRedeem = true, notice = AppStorePaywallNotice.CouponRedeemOpened) }
        emit(AppStorePaywallEffect.OpenUrl(url))
    }

    /**
     * Volta da App Store depois do resgate do código: `Transaction.updates` costuma entregar a
     * compra sozinho; reentregar o pendente e reler o plano cobre o caso em que ela já chegou.
     */
    private fun resumed() {
        val current = state.value
        if (!current.awaitingRedeem || current.isBusy) return
        viewModelScope.launch {
            store.retryPending().toNotice()
            if (state.value.isSubscribed) return@launch
            val subscription = (subscriptions.mySubscription() as? SaqzResult.Success)?.value
            if (subscription != null && subscription.entitled && subscription.provider == store.provider) subscribed()
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
        val couponOffer = current.coupon?.purchaseOfferId == offerId
        val offer = current.plans.firstNotNullOfOrNull { plan ->
            listOfNotNull(plan.monthly, plan.annual).firstOrNull { it.productId == offerId }
        }
        if (offer == null && !couponOffer) return
        if (offer?.isCurrent == true) return
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

    private sealed interface CouponResult {
        data class Applied(val coupon: PaywallCouponUi) : CouponResult
        data class Message(val message: PaywallCouponMessage) : CouponResult
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
    return AppStorePaywallPlanUi(
        plan = id,
        name = id.name,
        benefits = benefits(),
        monthly = monthly,
        annual = annual,
        maxGroups = maxGroups,
    )
}

/** Maiúsculas, só letras e números, como o admin cria o código. */
internal fun normalizeCouponCode(raw: String): String =
    raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(MaxCouponCodeLength)

private const val MaxCouponCodeLength = 32

private fun CampaignCoupon.toUi(plan: Plan, cycle: SubscriptionCycle) = PaywallCouponUi(
    code = code,
    plan = plan,
    cycle = cycle,
    discountPercent = discountPercent,
    durationCycles = durationCycles,
)

private fun CouponRejection.toMessage() = when (this) {
    CouponRejection.NotFound -> PaywallCouponMessage.NotFound
    CouponRejection.Expired -> PaywallCouponMessage.Expired
    CouponRejection.InvalidPlan -> PaywallCouponMessage.InvalidPlan
    CouponRejection.InvalidCycle -> PaywallCouponMessage.InvalidCycle
    CouponRejection.NotEligible -> PaywallCouponMessage.NotEligible
    CouponRejection.Exhausted -> PaywallCouponMessage.Exhausted
    CouponRejection.NotAvailableOnPlatform -> PaywallCouponMessage.OnlyOnWeb
    CouponRejection.PendingOnPlatform -> PaywallCouponMessage.Pending
}
