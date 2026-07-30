package br.com.saqz.subscriptions.domain

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
)
