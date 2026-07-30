package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

data class AsaasWebhookCommand(
    val asaasEventId: String,
    val eventType: String,
    val asaasSubscriptionId: String?,
    val rawPayload: String,
)

sealed interface ProcessAsaasWebhookResult {
    data object Unauthorized : ProcessAsaasWebhookResult
    data object Accepted : ProcessAsaasWebhookResult
}

fun interface AsaasWebhookProcessor {
    fun execute(providedToken: String?, command: AsaasWebhookCommand): ProcessAsaasWebhookResult
}

/**
 * Only path that may mark a subscription as paid/active. Token is checked before any
 * side effect; [SubscriptionEvent] insert by `asaasEventId` is the idempotency gate.
 */
class ProcessAsaasWebhook(
    private val expectedToken: String,
    private val events: SubscriptionEventStore,
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
    private val newEventId: () -> UUID = UUID::randomUUID,
) : AsaasWebhookProcessor {
    override fun execute(providedToken: String?, command: AsaasWebhookCommand): ProcessAsaasWebhookResult {
        if (!tokenMatches(providedToken, expectedToken)) {
            return ProcessAsaasWebhookResult.Unauthorized
        }
        if (command.asaasEventId.isBlank() || command.eventType.isBlank()) {
            return ProcessAsaasWebhookResult.Accepted
        }

        val now = clock.instant()
        return transaction.inTransaction {
            val claimed = events.tryInsert(
                id = newEventId(),
                asaasEventId = command.asaasEventId,
                type = command.eventType,
                payload = command.rawPayload,
                now = now,
            )
            if (!claimed) {
                return@inTransaction ProcessAsaasWebhookResult.Accepted
            }

            applyEvent(command, now)
            events.markProcessed(command.asaasEventId, now)
            ProcessAsaasWebhookResult.Accepted
        }
    }

    private fun applyEvent(command: AsaasWebhookCommand, now: Instant) {
        val subscriptionId = command.asaasSubscriptionId?.takeIf { it.isNotBlank() } ?: return
        val current = subscriptions.findByAsaasSubscriptionId(subscriptionId) ?: return

        when (command.eventType) {
            EVENT_PAYMENT_CONFIRMED -> {
                val confirmed = confirmPayment(current, now)
                subscriptions.save(confirmed.subscription)
                confirmed.fullPriceCentsToPush?.let { cents ->
                    asaasGateway.updateSubscriptionValue(subscriptionId, cents)
                }
            }
            EVENT_PAYMENT_OVERDUE -> subscriptions.save(markPastDue(current, now))
            EVENT_SUBSCRIPTION_DELETED -> subscriptions.save(cancel(current, now))
            else -> Unit
        }
    }

    private data class ConfirmOutcome(
        val subscription: Subscription,
        val fullPriceCentsToPush: Long?,
    )

    private fun confirmPayment(current: Subscription, now: Instant): ConfirmOutcome {
        var next = current.copy(
            status = SubscriptionStatus.ACTIVE,
            currentPeriodEnd = advancePeriodEnd(current.currentPeriodEnd, current.cycle),
            pastDueSince = null,
        )

        val pending = next.pendingPlan
        val pendingAt = next.pendingPlanEffectiveAt
        if (pending != null && pendingAt != null && !now.isBefore(pendingAt)) {
            next = next.copy(
                plan = pending,
                pendingPlan = null,
                pendingPlanEffectiveAt = null,
            )
        }

        var fullPriceCentsToPush: Long? = null
        val remaining = next.couponCyclesRemaining
        if (remaining != null && remaining > 0) {
            val after = remaining - 1
            if (after == 0) {
                next = next.copy(couponCyclesRemaining = null)
                fullPriceCentsToPush = next.plan.priceCents(next.cycle)
            } else {
                next = next.copy(couponCyclesRemaining = after)
            }
        }

        return ConfirmOutcome(next, fullPriceCentsToPush)
    }

    private fun markPastDue(current: Subscription, now: Instant): Subscription =
        current.copy(
            status = SubscriptionStatus.PAST_DUE,
            pastDueSince = current.pastDueSince ?: now,
        )

    private fun cancel(current: Subscription, now: Instant): Subscription =
        current.copy(
            status = SubscriptionStatus.CANCELED,
            canceledAt = current.canceledAt ?: now,
        )

    companion object {
        const val EVENT_PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED"
        const val EVENT_PAYMENT_OVERDUE = "PAYMENT_OVERDUE"
        const val EVENT_SUBSCRIPTION_DELETED = "SUBSCRIPTION_DELETED"
        const val WEBHOOK_TOKEN_HEADER = "asaas-access-token"

        fun advancePeriodEnd(currentPeriodEnd: Instant, cycle: SubscriptionCycle): Instant {
            val zoned = currentPeriodEnd.atZone(ZoneOffset.UTC)
            return when (cycle) {
                SubscriptionCycle.MONTHLY -> zoned.plusMonths(1).toInstant()
                SubscriptionCycle.ANNUAL -> zoned.plusYears(1).toInstant()
            }
        }

        fun Plan.priceCents(cycle: SubscriptionCycle): Long =
            when (cycle) {
                SubscriptionCycle.MONTHLY -> monthlyPriceCents
                SubscriptionCycle.ANNUAL -> annualPriceCents
            }

        fun tokenMatches(provided: String?, expected: String): Boolean {
            if (provided == null || expected.isEmpty()) return false
            val left = provided.toByteArray(Charsets.UTF_8)
            val right = expected.toByteArray(Charsets.UTF_8)
            if (left.size != right.size) return false
            return MessageDigest.isEqual(left, right)
        }
    }
}
