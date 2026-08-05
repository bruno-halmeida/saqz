package br.com.saqz.subscriptions.domain

import br.com.saqz.subscriptions.application.AsaasBillingType
import java.time.Duration
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
        SubscriptionStatus.PAST_DUE -> firstConfirmedAt != null && withinPastDueGrace(now)
        SubscriptionStatus.CANCELED -> firstConfirmedAt != null && currentPeriodEnd.isAfter(now)
    }

    /**
     * Inadimplente mantém o acesso por [PAST_DUE_GRACE] contados do vencimento, e depois perde.
     * Antes disto a cláusula era só `firstConfirmedAt != null`: quem pagasse uma única vez ficava
     * com o plano para sempre, sem nunca mais pagar.
     *
     * `pastDueSince` nulo são linhas legadas (o campo passou a ser sempre preenchido em
     * `markPastDue`/`blankSubscription`) — para elas o acesso é preservado, mesma escolha do
     * `GetMySubscription.isReadOnly`, para não cortar ninguém por dado ausente.
     */
    private fun withinPastDueGrace(now: Instant): Boolean {
        val since = pastDueSince ?: return true
        return now.isBefore(since.plus(PAST_DUE_GRACE))
    }

    companion object {
        /** Carência de inadimplência. Espelhada no intervalo do `JdbcSubscriptionPlanLookup`. */
        val PAST_DUE_GRACE: Duration = Duration.ofDays(7)
    }
}
