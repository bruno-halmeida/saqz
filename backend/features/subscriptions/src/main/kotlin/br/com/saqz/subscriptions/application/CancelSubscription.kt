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
    private val clock: Clock,
) {
    fun execute(ownerUserId: UUID): CancelSubscriptionResult {
        val current = subscriptions.findByOwnerUserId(ownerUserId)
            ?: return CancelSubscriptionResult.NotFound
        if (current.status == SubscriptionStatus.CANCELED || current.canceledAt != null) {
            return CancelSubscriptionResult.AlreadyCanceled
        }
        val now = clock.instant()
        // Paid period remains until currentPeriodEnd; groups grace is computed on read (VUL-106).
        val updated = current.copy(canceledAt = now)
        subscriptions.save(updated)
        return CancelSubscriptionResult.Success(updated)
    }
}
