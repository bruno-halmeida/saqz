package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class SubscriptionUsage(
    val groupsUsed: Int,
    val groupsLimit: Int?,
)

data class MySubscriptionView(
    val status: SubscriptionStatus,
    /** [Subscription.isEntitlingAt] — a mesma regra do POST de criação, exposta para o app rotear. */
    val entitled: Boolean,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val pendingPlan: Plan?,
    val pendingPlanEffectiveAt: Instant?,
    val currentPeriodEnd: Instant,
    val paymentMethod: AsaasBillingType?,
    val usage: SubscriptionUsage,
    val readOnly: Boolean,
    val pastDueSince: Instant?,
    val canceledAt: Instant?,
)

sealed class GetMySubscriptionResult {
    data class Found(val subscription: MySubscriptionView) : GetMySubscriptionResult()
    data object NotFound : GetMySubscriptionResult()
}

class GetMySubscription(
    private val subscriptions: SubscriptionRepository,
    private val ownedGroups: OwnedGroupCounter,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun execute(ownerUserId: UUID): GetMySubscriptionResult {
        val subscription = subscriptions.findByOwnerUserId(ownerUserId)
            ?: return GetMySubscriptionResult.NotFound
        val now = clock.instant()
        return GetMySubscriptionResult.Found(
            MySubscriptionView(
                status = subscription.status,
                entitled = subscription.isEntitlingAt(now),
                plan = subscription.plan,
                cycle = subscription.cycle,
                pendingPlan = subscription.pendingPlan,
                pendingPlanEffectiveAt = subscription.pendingPlanEffectiveAt,
                currentPeriodEnd = subscription.currentPeriodEnd,
                paymentMethod = null,
                usage = usageFor(ownerUserId, subscription),
                readOnly = isReadOnly(subscription, now),
                pastDueSince = subscription.pastDueSince,
                canceledAt = subscription.canceledAt,
            ),
        )
    }

    private fun usageFor(ownerUserId: UUID, subscription: Subscription): SubscriptionUsage {
        val groupsLimit = SubscriptionLimitsAdapter.moreRestrictive(
            subscription.plan.maxGroups,
            subscription.pendingPlan?.maxGroups,
        )
        return SubscriptionUsage(
            groupsUsed = ownedGroups.countOwnedGroups(ownerUserId),
            groupsLimit = groupsLimit,
        )
    }

    companion object {
        /** Mesma carencia do entitlement — fonte unica em [Subscription.PAST_DUE_GRACE]. */
        val PAST_DUE_GRACE: java.time.Duration = Subscription.PAST_DUE_GRACE
        val CANCELED_GRACE: java.time.Duration = java.time.Duration.ofDays(30)

        fun isReadOnly(subscription: Subscription, now: Instant): Boolean = when (subscription.status) {
            SubscriptionStatus.ACTIVE -> false
            SubscriptionStatus.PAST_DUE -> {
                val since = subscription.pastDueSince ?: return false
                now.isAfter(since.plus(PAST_DUE_GRACE.toDays(), ChronoUnit.DAYS))
            }
            SubscriptionStatus.CANCELED -> {
                val canceledAt = subscription.canceledAt ?: return false
                now.isAfter(canceledAt.plus(CANCELED_GRACE.toDays(), ChronoUnit.DAYS))
            }
        }
    }
}
