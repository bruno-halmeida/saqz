package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import br.com.saqz.subscriptions.domain.Plan
import java.util.UUID

fun interface SubscriptionPlanLookup {
    fun findEntitlingPlan(ownerId: UUID): EntitlingSubscription?
}

data class EntitlingSubscription(
    val plan: Plan,
    val pendingPlan: Plan? = null,
)

class SubscriptionLimitsAdapter(
    private val lookup: SubscriptionPlanLookup,
) : SubscriptionLimits {
    override fun groupLimitFor(ownerId: UUID): Int? = effectiveLimit(ownerId) { it.maxGroups }

    override fun athleteLimitFor(ownerId: UUID): Int? = effectiveLimit(ownerId) { it.maxAthletes }

    private fun effectiveLimit(ownerId: UUID, selector: (Plan) -> Int?): Int? {
        val subscription = lookup.findEntitlingPlan(ownerId) ?: return 0
        val current = selector(subscription.plan)
        val pending = subscription.pendingPlan?.let(selector)
        return moreRestrictive(current, pending)
    }

    companion object {
        fun moreRestrictive(current: Int?, pending: Int?): Int? = when {
            current == null -> pending
            pending == null -> current
            else -> minOf(current, pending)
        }
    }
}
