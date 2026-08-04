package br.com.saqz.subscriptions.domain

import br.com.saqz.subscriptions.application.AsaasBillingType
import java.time.Instant
import java.util.UUID

enum class SubscriptionCycle { MONTHLY, ANNUAL }
enum class SubscriptionStatus { ACTIVE, PAST_DUE, CANCELED }

data class Subscription(
    val ownerUserId: UUID,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val asaasCustomerId: String,
    val asaasSubscriptionId: String,
    /** Null for legacy rows created before this field existed — never fabricate a value for them. */
    val billingType: AsaasBillingType?,
    val currentPeriodEnd: Instant,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val canceledAt: Instant? = null,
    val pendingPlan: Plan? = null,
    val pendingPlanEffectiveAt: Instant? = null,
    val couponId: UUID? = null,
    val couponCyclesRemaining: Int? = null,
    val pastDueSince: Instant? = null,
    /** Set on first PAYMENT_CONFIRMED; null means never paid (no entitlements while PAST_DUE). */
    val firstConfirmedAt: Instant? = null,
    /** Upgrade awaiting one-off charge confirmation (webhook applies plan). */
    val pendingUpgradePlan: Plan? = null,
    val pendingUpgradeChargeId: String? = null,
    /**
     * Última cobrança cujo pagamento já foi aplicado. Asaas manda PAYMENT_CONFIRMED e
     * PAYMENT_RECEIVED para a mesma cobrança com `asaasEventId` diferente, então a trava por
     * evento não colapsa o par — sem isto o segundo evento avançaria `currentPeriodEnd` de novo.
     */
    val lastConfirmedPaymentId: String? = null,
) {
    /**
     * Espelho em Kotlin do predicado de `JdbcSubscriptionPlanLookup.findEntitlingPlan` —
     * mudou lá, muda aqui. É o que o `/subscriptions/me` expõe para o app rotear o "+"
     * sem reinventar a regra no cliente.
     */
    fun isEntitlingAt(now: Instant): Boolean = when (status) {
        SubscriptionStatus.ACTIVE -> true
        SubscriptionStatus.PAST_DUE -> firstConfirmedAt != null
        SubscriptionStatus.CANCELED -> firstConfirmedAt != null && currentPeriodEnd.isAfter(now)
    }
}
