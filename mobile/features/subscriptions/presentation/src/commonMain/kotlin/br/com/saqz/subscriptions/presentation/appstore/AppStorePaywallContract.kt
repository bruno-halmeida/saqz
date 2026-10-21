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

    /** O código do cupom foi aberto na App Store: a compra volta sozinha quando a pessoa retorna. */
    CouponRedeemOpened,
}

/** Resposta curta da folha do cupom. */
enum class PaywallCouponMessage {
    NotFound,
    Expired,
    InvalidPlan,
    InvalidCycle,
    NotEligible,
    Exhausted,
    OnlyOnWeb,

    /** A oferta ainda está sendo criada na loja. */
    Pending,
    Failed,
}

/** A folha "Tenho um cupom": o código digitado e o plano a que ele vai se aplicar. */
@Immutable
data class PaywallCouponSheet(
    val code: String = "",
    val plan: Plan,
    val isValidating: Boolean = false,
    val message: PaywallCouponMessage? = null,
)

/**
 * Cupom aceito para um plano e ciclo. No Play, [purchaseOfferId] é a oferta com desconto e
 * [discountedPrice] o preço da primeira fase; na App Store, [redeemUrl] abre o resgate do código.
 */
@Immutable
data class PaywallCouponUi(
    val code: String,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val discountPercent: Int,
    val durationCycles: Int,
    val discountedPrice: String? = null,
    val purchaseOfferId: String? = null,
    val redeemUrl: String? = null,
)

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
    /** Teto de grupos do plano; nulo é sem teto. Diz a quem bateu o limite quais planos não cabem. */
    val maxGroups: Int? = null,
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
    val coupon: PaywallCouponUi? = null,
    val couponSheet: PaywallCouponSheet? = null,
    /** O resgate do código foi aberto na App Store: na volta ao app, o plano é reconsultado. */
    val awaitingRedeem: Boolean = false,
) {
    fun couponFor(plan: Plan): PaywallCouponUi? = coupon?.takeIf { it.plan == plan && it.cycle == cycle }

    val isBusy: Boolean get() = purchasingProductId != null || isRestoring || isConfirming || isSubscribed
}

sealed interface AppStorePaywallIntent {
    data object Retry : AppStorePaywallIntent
    data class SelectCycle(val cycle: SubscriptionCycle) : AppStorePaywallIntent
    data class Purchase(val productId: String) : AppStorePaywallIntent
    data object Restore : AppStorePaywallIntent
    data object RetryConfirmation : AppStorePaywallIntent
    data object DismissNotice : AppStorePaywallIntent
    data object OpenCoupon : AppStorePaywallIntent
    data object CloseCoupon : AppStorePaywallIntent
    data class CouponCodeChanged(val code: String) : AppStorePaywallIntent
    data class CouponPlanChanged(val plan: Plan) : AppStorePaywallIntent
    data object ApplyCoupon : AppStorePaywallIntent
    data object PurchaseWithCoupon : AppStorePaywallIntent

    /** O app voltou ao primeiro plano (da App Store, depois do resgate do código). */
    data object Resumed : AppStorePaywallIntent
}

sealed interface AppStorePaywallEffect {
    /** O backend confirmou uma assinatura com acesso: quem hospeda segue o fluxo autorizado. */
    data object Subscribed : AppStorePaywallEffect

    /** Abrir fora do app: a página de resgate do código na App Store. */
    data class OpenUrl(val url: String) : AppStorePaywallEffect
}
