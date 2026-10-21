package br.com.saqz.subscriptions.presentation.store

import br.com.saqz.domain.DataError
import br.com.saqz.subscriptions.domain.coupon.CampaignCoupon
import br.com.saqz.subscriptions.domain.coupon.CouponPlatform
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanCatalogItem
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import kotlinx.coroutines.flow.SharedFlow

/** A loja que vende neste aparelho: muda o texto da tela de compra, não o fluxo. */
enum class PaywallStoreKind { AppStore, GooglePlay }

data class PlanCycle(val plan: Plan, val cycle: SubscriptionCycle)

/** Uma assinatura à venda: o id que a compra usa e o preço como a loja formatou. */
data class StoreOffer(val id: String, val displayPrice: String)

/** O que aconteceu com uma compra entregue ao backend. */
sealed interface StoreDelivery {
    data class Delivered(val subscription: MySubscription) : StoreDelivery
    data object OwnedByAnotherAccount : StoreDelivery
    data object Rejected : StoreDelivery

    /** Rede ou 5xx: a loja guarda a compra e a entrega é refeita depois. */
    data class Deferred(val error: DataError) : StoreDelivery
}

sealed interface StorePurchaseOutcome {
    data class Completed(val delivery: StoreDelivery) : StorePurchaseOutcome

    /** Ask to Buy ou pagamento pendente: a compra conclui depois, em segundo plano. */
    data object Pending : StorePurchaseOutcome
    data object Cancelled : StorePurchaseOutcome
    data object Failed : StorePurchaseOutcome
}

sealed interface StoreRestoreOutcome {
    data class Restored(val deliveries: List<StoreDelivery>) : StoreRestoreOutcome
    data object Failed : StoreRestoreOutcome
}

/** Como comprar com um cupom de campanha nesta loja. */
sealed interface CouponCheckout {
    /** Google Play: a oferta do cupom no plano base, com o preço das fases do Play. */
    data class InApp(val offer: StoreOffer) : CouponCheckout

    /** App Store: a página de resgate abre com o código preenchido; a compra volta por `Transaction.updates`. */
    data class Redeem(val url: String) : CouponCheckout

    /** A loja ainda não mostra a oferta do cupom: libera em alguns minutos. */
    data object NotReady : CouponCheckout
}

/**
 * Leva as compras da loja ao backend durante a vida do processo: escuta desde a abertura,
 * reentrega o que ficou pendente no login e anuncia cada assinatura que o backend devolveu.
 */
interface StorePurchaseSync {
    val deliveries: SharedFlow<MySubscription>
    fun start()
    fun onAuthenticated()
    fun onSignedOut()
}

/**
 * A loja do aparelho vista pela tela de compra. App Store no iOS, Google Play no Android: o
 * fluxo é o mesmo — preço da loja, compra presa à conta, backend decide o acesso.
 */
interface PaywallStore {
    val kind: PaywallStoreKind

    /** O `provider` que o backend devolve para uma assinatura desta loja. */
    val provider: SubscriptionProvider
    val deliveries: SharedFlow<MySubscription>

    /** Como o backend chama esta loja ao validar um cupom. */
    val couponPlatform: CouponPlatform

    fun canPurchase(): Boolean

    /** Ofertas por plano e ciclo, só das que a loja conhece; `null` quando a loja não respondeu. */
    suspend fun offers(catalog: List<PlanCatalogItem>): Map<PlanCycle, StoreOffer>?

    suspend fun purchase(offerId: String): StorePurchaseOutcome

    /** [offerId] é a oferta do plano e ciclo do cupom, de [offers]. */
    suspend fun couponCheckout(offerId: String, coupon: CampaignCoupon): CouponCheckout

    suspend fun restore(): StoreRestoreOutcome

    /** Reentrega o que a loja confirmou e o backend ainda não aceitou. */
    suspend fun retryPending(): List<StoreDelivery>
}
