package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionProvider
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
    val provider: SubscriptionProvider = SubscriptionProvider.ASAAS,
    /** Renovação automática da App Store; nulo para o Asaas e antes da primeira notificação. */
    val autoRenew: Boolean? = null,
)

sealed class GetMySubscriptionResult {
    data class Found(val subscription: MySubscriptionView) : GetMySubscriptionResult()
    data object NotFound : GetMySubscriptionResult()
}

class GetMySubscription(
    private val subscriptions: SubscriptionRepository,
    private val ownedGroups: OwnedGroupCounter,
    private val clock: Clock = Clock.systemUTC(),
    private val recoverUnconfirmed: RecoverUnconfirmedPayment? = null,
    private val appStoreSubscriptions: AppStoreSubscriptionRepository? = null,
) {
    /**
     * Com assinatura na web e na App Store, mostra a que dá acesso — a da App Store se as duas
     * dão. Sem acesso por nenhuma, mostra a que venceu por último.
     */
    fun execute(ownerUserId: UUID): GetMySubscriptionResult {
        val now = clock.instant()
        val appStore = appStoreSubscriptions?.findByOwner(ownerUserId).orEmpty()
        appStore.filter { it.isEntitlingAt(now) }.maxByOrNull { it.expiresAt }?.let {
            return GetMySubscriptionResult.Found(appStoreView(ownerUserId, it, now))
        }
        val latestAppStore = appStore.maxByOrNull { it.expiresAt }
        val asaas = subscriptions.findByOwnerUserId(ownerUserId)?.let(::recoverIfNeeded)
        val view = when {
            asaas == null -> latestAppStore?.let { appStoreView(ownerUserId, it, now) }
            latestAppStore == null || asaas.isEntitlingAt(now) ||
                !latestAppStore.expiresAt.isAfter(asaas.currentPeriodEnd) -> asaasView(ownerUserId, asaas, now)
            else -> appStoreView(ownerUserId, latestAppStore, now)
        }
        return view?.let(GetMySubscriptionResult::Found) ?: GetMySubscriptionResult.NotFound
    }

    private fun asaasView(ownerUserId: UUID, subscription: Subscription, now: Instant) = MySubscriptionView(
        status = subscription.status,
        entitled = subscription.isEntitlingAt(now),
        plan = subscription.plan,
        cycle = subscription.cycle,
        pendingPlan = subscription.pendingPlan,
        pendingPlanEffectiveAt = subscription.pendingPlanEffectiveAt,
        currentPeriodEnd = subscription.currentPeriodEnd,
        paymentMethod = null,
        usage = usageFor(ownerUserId, subscription.plan, subscription.pendingPlan),
        readOnly = isReadOnly(subscription, now),
        pastDueSince = subscription.pastDueSince,
        canceledAt = subscription.canceledAt,
    )

    /** Tabela de estados em docs/subscriptions/app-store.md. */
    private fun appStoreView(ownerUserId: UUID, subscription: AppStoreSubscription, now: Instant): MySubscriptionView {
        val revokedAt = subscription.revokedAt
        val status = when {
            revokedAt != null -> SubscriptionStatus.CANCELED
            subscription.expiresAt.isAfter(now) ->
                if (subscription.autoRenew == false) SubscriptionStatus.CANCELED else SubscriptionStatus.ACTIVE
            subscription.inBillingRetry -> SubscriptionStatus.PAST_DUE
            else -> SubscriptionStatus.CANCELED
        }
        val pastDueSince = subscription.expiresAt.takeIf { status == SubscriptionStatus.PAST_DUE }
        val canceledAt = if (status == SubscriptionStatus.CANCELED) {
            revokedAt ?: subscription.autoRenewChangedAt ?: subscription.expiresAt
        } else {
            null
        }
        val pendingPlan = subscription.pendingPlan
        return MySubscriptionView(
            status = status,
            entitled = subscription.isEntitlingAt(now),
            plan = subscription.product.plan,
            cycle = subscription.product.cycle,
            pendingPlan = pendingPlan,
            pendingPlanEffectiveAt = subscription.expiresAt.takeIf { pendingPlan != null },
            currentPeriodEnd = revokedAt ?: subscription.expiresAt,
            paymentMethod = null,
            usage = usageFor(ownerUserId, subscription.product.plan, pendingPlan),
            readOnly = isReadOnly(status, pastDueSince, canceledAt, now),
            pastDueSince = pastDueSince,
            canceledAt = canceledAt,
            provider = SubscriptionProvider.APP_STORE,
            autoRenew = subscription.autoRenew,
        )
    }

    private fun recoverIfNeeded(subscription: Subscription): Subscription {
        if (recoverUnconfirmed == null) return subscription
        return recoverUnconfirmed.recoverIfPaid(subscription)
    }

    private fun usageFor(ownerUserId: UUID, plan: Plan, pendingPlan: Plan?): SubscriptionUsage {
        val groupsLimit = SubscriptionLimitsAdapter.moreRestrictive(plan.maxGroups, pendingPlan?.maxGroups)
        return SubscriptionUsage(
            groupsUsed = ownedGroups.countOwnedGroups(ownerUserId),
            groupsLimit = groupsLimit,
        )
    }

    companion object {
        /** Mesma carencia do entitlement — fonte unica em [Subscription.PAST_DUE_GRACE]. */
        val PAST_DUE_GRACE: java.time.Duration = Subscription.PAST_DUE_GRACE
        val CANCELED_GRACE: java.time.Duration = java.time.Duration.ofDays(30)

        fun isReadOnly(subscription: Subscription, now: Instant): Boolean =
            isReadOnly(subscription.status, subscription.pastDueSince, subscription.canceledAt, now)

        fun isReadOnly(
            status: SubscriptionStatus,
            pastDueSince: Instant?,
            canceledAt: Instant?,
            now: Instant,
        ): Boolean = when (status) {
            SubscriptionStatus.ACTIVE -> false
            SubscriptionStatus.PAST_DUE -> {
                val since = pastDueSince ?: return false
                now.isAfter(since.plus(PAST_DUE_GRACE.toDays(), ChronoUnit.DAYS))
            }
            SubscriptionStatus.CANCELED -> {
                val canceled = canceledAt ?: return false
                now.isAfter(canceled.plus(CANCELED_GRACE.toDays(), ChronoUnit.DAYS))
            }
        }
    }
}
