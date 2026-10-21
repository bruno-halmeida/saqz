package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

/** Quem pode usar o cupom. Um terceiro valor (assina hoje) entra depois sem mudar o schema. */
enum class CouponEligibility {
    /** Nenhuma assinatura paga em nenhuma plataforma; teste grátis não conta. */
    NEVER_SUBSCRIBED,

    /** Teve assinatura paga e hoje não tem acesso pago. */
    FORMER_SUBSCRIBER,
}

/** Onde o cupom foi usado. */
enum class CouponPlatform { WEB, ANDROID, IOS }

/** Um plano num ciclo, como o cupom guarda em `coupons.products` (`PLANO:CICLO`). */
data class CouponProduct(val plan: Plan, val cycle: SubscriptionCycle) {
    fun key(): String = "${plan.name}:${cycle.name}"

    companion object {
        val ALL: List<CouponProduct> = Plan.entries.flatMap { plan -> SubscriptionCycle.entries.map { CouponProduct(plan, it) } }

        fun parse(raw: String): CouponProduct? {
            val (plan, cycle) = raw.split(':').takeIf { it.size == 2 } ?: return null
            return CouponProduct(
                runCatching { Plan.valueOf(plan) }.getOrNull() ?: return null,
                runCatching { SubscriptionCycle.valueOf(cycle) }.getOrNull() ?: return null,
            )
        }
    }
}

data class Coupon(
    val id: UUID,
    val code: String,
    val discountPercent: Int,
    val durationCycles: Int? = null,
    val validUntil: Instant? = null,
    val campaign: String? = null,
    val maxUses: Int? = null,
    /** Vazio = qualquer pessoa (cupons de antes das campanhas). */
    val eligibility: Set<CouponEligibility> = emptySet(),
    /** Vazio = todos os planos e ciclos. */
    val products: Set<CouponProduct> = emptySet(),
    /** Tem ofertas no Google Play e na App Store. */
    val stores: Boolean = false,
    val endedAt: Instant? = null,
) {
    init {
        require(discountPercent in 1..100) { "discountPercent must be between 1 and 100" }
        require(durationCycles == null || durationCycles >= 1) { "durationCycles must be at least 1 when present" }
        require(maxUses == null || maxUses >= 1) { "maxUses must be at least 1 when present" }
    }

    fun covers(plan: Plan, cycle: SubscriptionCycle): Boolean = products.isEmpty() || CouponProduct(plan, cycle) in products

    fun coveredProducts(): List<CouponProduct> = if (products.isEmpty()) CouponProduct.ALL else CouponProduct.ALL.filter { it in products }
}
