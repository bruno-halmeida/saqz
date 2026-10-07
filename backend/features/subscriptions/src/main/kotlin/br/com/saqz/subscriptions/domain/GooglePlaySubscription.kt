package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

/**
 * Assinaturas cadastradas no Play Console: um produto por plano, um plano base por ciclo. Os IDs
 * não podem ser reaproveitados depois de criados no Play.
 */
enum class GooglePlayProduct(
    val productId: String,
    val basePlanId: String,
    val plan: Plan,
    val cycle: SubscriptionCycle,
) {
    TITULAR_MENSAL("app.saqz.titular", "mensal", Plan.TITULAR, SubscriptionCycle.MONTHLY),
    TITULAR_ANUAL("app.saqz.titular", "anual", Plan.TITULAR, SubscriptionCycle.ANNUAL),
    ORGANIZADOR_MENSAL("app.saqz.organizador", "mensal", Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY),
    ORGANIZADOR_ANUAL("app.saqz.organizador", "anual", Plan.ORGANIZADOR, SubscriptionCycle.ANNUAL),
    ILIMITADO_MENSAL("app.saqz.ilimitado", "mensal", Plan.ILIMITADO, SubscriptionCycle.MONTHLY),
    ILIMITADO_ANUAL("app.saqz.ilimitado", "anual", Plan.ILIMITADO, SubscriptionCycle.ANNUAL),
    ;

    companion object {
        fun of(productId: String?, basePlanId: String?): GooglePlayProduct? =
            entries.firstOrNull { it.productId == productId && it.basePlanId == basePlanId }

        fun of(plan: Plan, cycle: SubscriptionCycle): GooglePlayProduct =
            entries.first { it.plan == plan && it.cycle == cycle }
    }
}

/** `subscriptionState` da subscriptionsv2, sem o prefixo `SUBSCRIPTION_STATE_`. */
enum class GooglePlayState {
    PENDING,
    ACTIVE,
    PAUSED,
    IN_GRACE_PERIOD,
    ON_HOLD,
    CANCELED,
    EXPIRED,
    PENDING_PURCHASE_CANCELED,
    ;

    companion object {
        fun fromApi(raw: String?): GooglePlayState? =
            entries.firstOrNull { "SUBSCRIPTION_STATE_${it.name}" == raw }
    }
}

/** Uma assinatura como a Google Play Developer API a devolve, já lida pelo adapter. */
data class GooglePlayPurchase(
    val purchaseToken: String,
    val productId: String,
    val basePlanId: String?,
    val state: GooglePlayState,
    val expiresAt: Instant?,
    val autoRenew: Boolean?,
    val canceledAt: Instant?,
    val obfuscatedAccountId: String?,
    val linkedPurchaseToken: String?,
    val latestOrderId: String?,
    val acknowledged: Boolean,
    val testPurchase: Boolean,
    /** Troca adiada (DEFERRED): produto e plano base que entram na renovação. */
    val pendingProductId: String? = null,
    val pendingBasePlanId: String? = null,
) {
    /** Plano que entra na renovação; troca só de ciclo no mesmo plano não conta, como na App Store. */
    fun pendingPlanAfter(current: GooglePlayProduct): Plan? {
        val pending = pendingProductId ?: return null
        val plan = GooglePlayProduct.of(pending, pendingBasePlanId)?.plan
            ?: GooglePlayProduct.entries.firstOrNull { it.productId == pending }?.plan
        return plan?.takeIf { it != current.plan }
    }
}

/**
 * Assinatura do Play gravada. O estado inteiro vem da API do Google a cada leitura, então não há
 * regra de ordem aqui como na App Store: a última leitura sempre vence.
 */
data class GooglePlaySubscription(
    val purchaseToken: String,
    val ownerUserId: UUID,
    val product: GooglePlayProduct,
    val state: GooglePlayState,
    val expiresAt: Instant,
    val autoRenew: Boolean?,
    val canceledAt: Instant?,
    val latestOrderId: String?,
    val linkedPurchaseToken: String?,
    val supersededAt: Instant? = null,
    val acknowledged: Boolean,
    val testPurchase: Boolean,
    val pendingPlan: Plan? = null,
) {
    /**
     * Espelho do trecho Google Play de `JdbcSubscriptionPlanLookup.findEntitlingPlan` — mudou lá,
     * muda aqui. ON_HOLD e PAUSED não dão acesso; CANCELED vale até o fim do período pago.
     */
    fun isEntitlingAt(now: Instant): Boolean =
        supersededAt == null && state in ENTITLING_STATES && expiresAt.isAfter(now)

    fun refreshedFrom(purchase: GooglePlayPurchase, product: GooglePlayProduct): GooglePlaySubscription = copy(
        product = product,
        state = purchase.state,
        expiresAt = purchase.expiresAt ?: expiresAt,
        autoRenew = purchase.autoRenew,
        canceledAt = purchase.canceledAt,
        latestOrderId = purchase.latestOrderId ?: latestOrderId,
        linkedPurchaseToken = purchase.linkedPurchaseToken,
        acknowledged = purchase.acknowledged || acknowledged,
        testPurchase = purchase.testPurchase,
        pendingPlan = purchase.pendingPlanAfter(product),
    )

    companion object {
        val ENTITLING_STATES = setOf(GooglePlayState.ACTIVE, GooglePlayState.CANCELED, GooglePlayState.IN_GRACE_PERIOD)

        fun startedBy(purchase: GooglePlayPurchase, product: GooglePlayProduct, ownerUserId: UUID) = GooglePlaySubscription(
            purchaseToken = purchase.purchaseToken,
            ownerUserId = ownerUserId,
            product = product,
            state = purchase.state,
            expiresAt = requireNotNull(purchase.expiresAt) { "assinatura do Play sem expiryTime" },
            autoRenew = purchase.autoRenew,
            canceledAt = purchase.canceledAt,
            latestOrderId = purchase.latestOrderId,
            linkedPurchaseToken = purchase.linkedPurchaseToken,
            acknowledged = purchase.acknowledged,
            testPurchase = purchase.testPurchase,
            pendingPlan = purchase.pendingPlanAfter(product),
        )
    }
}
