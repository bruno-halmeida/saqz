package br.com.saqz.subscriptions.presentation.appstore

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.store.PaywallStoreKind

enum class AppStorePaywallPhase {
    Loading,
    Ready,
    LoadFailed,

    /**
     * Sem compra pela App Store nesta conta ou neste aparelho: compras bloqueadas, produtos
     * ainda não publicados, ou a pessoa já assina pela web (comprar de novo cobraria duas
     * vezes). Quem hospeda a tela mostra o portão de assinatura no lugar.
     */
    Unavailable,
}

enum class AppStorePaywallNotice {
    /** Ask to Buy: a compra espera aprovação e chega depois, em segundo plano. */
    Pending,
    PurchaseFailed,
    OwnedByAnotherAccount,
    Invalid,

    /** A App Store cobrou, mas o backend ainda não confirmou: a entrega é refeita. */
    ConfirmationPending,
    NothingToRestore,
    RestoreFailed,
}

/** Uma assinatura de um ciclo: o id da oferta na loja e o preço que a loja devolveu. */
@Immutable
data class AppStorePaywallOfferUi(
    val productId: String,
    val displayPrice: String,
    val isCurrent: Boolean,
)

@Immutable
data class AppStorePaywallPlanUi(
    val plan: Plan,
    val name: String,
    val benefits: List<UiText>,
    val monthly: AppStorePaywallOfferUi?,
    val annual: AppStorePaywallOfferUi?,
) {
    fun offer(cycle: SubscriptionCycle): AppStorePaywallOfferUi? =
        if (cycle == SubscriptionCycle.Annual) annual else monthly
}

@Immutable
data class AppStorePaywallState(
    /** App Store ou Google Play: muda o texto (onde é cobrado, onde cancelar), não o fluxo. */
    val store: PaywallStoreKind = PaywallStoreKind.AppStore,
    val phase: AppStorePaywallPhase = AppStorePaywallPhase.Loading,
    val cycle: SubscriptionCycle = SubscriptionCycle.Monthly,
    val plans: List<AppStorePaywallPlanUi> = emptyList(),
    val purchasingProductId: String? = null,
    val isRestoring: Boolean = false,
    val isConfirming: Boolean = false,
    val isSubscribed: Boolean = false,
    val notice: AppStorePaywallNotice? = null,
) {
    val isBusy: Boolean get() = purchasingProductId != null || isRestoring || isConfirming || isSubscribed
}

sealed interface AppStorePaywallIntent {
    data object Retry : AppStorePaywallIntent
    data class SelectCycle(val cycle: SubscriptionCycle) : AppStorePaywallIntent
    data class Purchase(val productId: String) : AppStorePaywallIntent
    data object Restore : AppStorePaywallIntent
    data object RetryConfirmation : AppStorePaywallIntent
    data object DismissNotice : AppStorePaywallIntent
}

sealed interface AppStorePaywallEffect {
    /** O backend confirmou uma assinatura com acesso: quem hospeda segue o fluxo autorizado. */
    data object Subscribed : AppStorePaywallEffect
}
