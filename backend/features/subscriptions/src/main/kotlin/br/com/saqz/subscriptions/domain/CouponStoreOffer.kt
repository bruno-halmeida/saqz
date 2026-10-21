package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

enum class CouponStore { GOOGLE_PLAY, APP_STORE }

/**
 * Estado de uma oferta de cupom numa loja. PENDING → CREATING → WAITING_STORE → ACTIVE; FAILED com
 * motivo (o admin tenta de novo); DEACTIVATING → DEACTIVATED quando o cupom é encerrado.
 */
enum class CouponStoreOfferState(val severity: Int) {
    ACTIVE(0),
    DEACTIVATED(1),
    WAITING_STORE(2),
    CREATING(3),
    PENDING(4),
    DEACTIVATING(5),
    FAILED(6),
}

/** Uma oferta: cupom × loja × produto (plano + ciclo). Os ids externos são preenchidos passo a passo. */
data class CouponStoreOffer(
    val id: UUID,
    val couponId: UUID,
    val store: CouponStore,
    val product: CouponProduct,
    val productId: String,
    val basePlanId: String? = null,
    val offerId: String? = null,
    val ascSubscriptionId: String? = null,
    val ascOfferCodeId: String? = null,
    val customCode: String? = null,
    val ascCustomCodeId: String? = null,
    val storePriceCents: Long? = null,
    val state: CouponStoreOfferState = CouponStoreOfferState.PENDING,
    val message: String? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Instant? = null,
    val storeCreatedAt: Instant? = null,
    val activatedAt: Instant? = null,
    val deactivatedAt: Instant? = null,
) {
    companion object {
        /** O pior estado entre as ofertas de uma plataforma é o estado dela na lista do admin. */
        fun worst(offers: Collection<CouponStoreOffer>): CouponStoreOfferState? = offers.maxByOrNull { it.state.severity }?.state
    }
}

/** Preço de tabela de cada produto nas lojas (centavos). Os anuais diferem do site: ponto ",90" da Apple. */
object StoreListPrices {
    fun cents(product: CouponProduct): Long = when (product.cycle) {
        SubscriptionCycle.MONTHLY -> product.plan.monthlyPriceCents
        SubscriptionCycle.ANNUAL -> when (product.plan) {
            Plan.TITULAR -> 35_990
            Plan.ORGANIZADOR -> 53_990
            Plan.ILIMITADO -> 80_990
        }
    }
}
