package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.util.UUID

sealed interface CancelSubscriptionResult {
    data class Success(val subscription: Subscription) : CancelSubscriptionResult
    data object NotFound : CancelSubscriptionResult
    data object AlreadyCanceled : CancelSubscriptionResult
}

class CancelSubscription(
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
) {
    // Row lock guards against the SUBSCRIPTION_DELETED webhook (also transacted with a row lock)
    // racing this call and having its CANCELED save clobbered by the stale snapshot saved below.
    fun execute(ownerUserId: UUID): CancelSubscriptionResult = transaction.inTransaction {
        val current = subscriptions.findByOwnerUserIdForUpdate(ownerUserId)
            ?: return@inTransaction CancelSubscriptionResult.NotFound
        if (current.status == SubscriptionStatus.CANCELED || current.canceledAt != null) {
            return@inTransaction CancelSubscriptionResult.AlreadyCanceled
        }
        // Stop future Asaas charges now; local access still follows currentPeriodEnd / VUL-106 grace.
        asaasGateway.cancelSubscription(current.asaasSubscriptionId)
        val now = clock.instant()
        // Deliberately keep pendingUpgradePlan/pendingUpgradeChargeId: there is no gateway call to
        // cancel that one-off charge, so it can still be paid after this. Clearing the mapping here
        // would make ProcessAsaasWebhook.resolveSubscription unable to find this row for that late
        // payment; canceledAt (checked below) is what stops it from being misapplied.
        val updated = current.copy(canceledAt = now)
        subscriptions.save(updated)
        CancelSubscriptionResult.Success(updated)
    }
}
