package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
import br.com.saqz.subscriptions.application.SubscriptionPricing.applyDiscount
import br.com.saqz.subscriptions.application.SubscriptionPricing.priceCents
import br.com.saqz.subscriptions.application.SubscriptionPricing.prorataUpgradeCents
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.util.UUID

data class ChangePlanCommand(
    val ownerUserId: UUID,
    val requestId: UUID,
    val targetPlan: Plan,
)

sealed interface ChangePlanResult {
    data class Upgraded(
        val subscription: Subscription,
        val chargedCents: Long,
        val oneOffChargeId: String,
    ) : ChangePlanResult

    data class DowngradeScheduled(val subscription: Subscription) : ChangePlanResult

    data object NotFound : ChangePlanResult
    data object NotActive : ChangePlanResult
    data object SamePlan : ChangePlanResult
    data object DowngradeBlockedByUsage : ChangePlanResult
}

class ChangePlan(
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val usageLookup: OwnerPlanUsageLookup,
    private val coupons: CouponRepository,
    private val clock: Clock,
) {
    fun execute(command: ChangePlanCommand): ChangePlanResult {
        val current = subscriptions.findByOwnerUserId(command.ownerUserId)
            ?: return ChangePlanResult.NotFound
        if (current.status == SubscriptionStatus.CANCELED) {
            return ChangePlanResult.NotActive
        }
        if (current.plan == command.targetPlan) {
            return ChangePlanResult.SamePlan
        }

        val currentPrice = current.plan.priceCents(current.cycle)
        val targetPrice = command.targetPlan.priceCents(current.cycle)
        return if (targetPrice > currentPrice) {
            upgrade(current, command)
        } else {
            downgrade(current, command)
        }
    }

    private fun upgrade(current: Subscription, command: ChangePlanCommand): ChangePlanResult {
        val now = clock.instant()
        val chargedCents = prorataUpgradeCents(
            currentPriceCents = current.plan.priceCents(current.cycle),
            targetPriceCents = command.targetPlan.priceCents(current.cycle),
            now = now,
            currentPeriodEnd = current.currentPeriodEnd,
            cycle = current.cycle,
        )
        val chargeId = if (chargedCents > 0L) {
            asaasGateway.createOneOffCharge(
                asaasCustomerId = current.asaasCustomerId,
                valueCents = chargedCents,
                description = "Upgrade Saqz ${current.plan.name} → ${command.targetPlan.name}",
                idempotencyKey = "subscription-upgrade:${command.ownerUserId}:${command.requestId}",
            )
        } else {
            "no-charge"
        }

        val nextPrice = recurringPriceCents(command.targetPlan, current)
        asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, nextPrice)

        val updated = current.copy(
            plan = command.targetPlan,
            pendingPlan = null,
            pendingPlanEffectiveAt = null,
        )
        subscriptions.save(updated)
        return ChangePlanResult.Upgraded(updated, chargedCents, chargeId)
    }

    private fun downgrade(current: Subscription, command: ChangePlanCommand): ChangePlanResult {
        if (!usageFitsTarget(command.ownerUserId, command.targetPlan)) {
            return ChangePlanResult.DowngradeBlockedByUsage
        }
        val targetPrice = recurringPriceCents(command.targetPlan, current)
        asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, targetPrice)
        val updated = current.copy(
            pendingPlan = command.targetPlan,
            pendingPlanEffectiveAt = current.currentPeriodEnd,
        )
        subscriptions.save(updated)
        return ChangePlanResult.DowngradeScheduled(updated)
    }

    /** List price of [plan], still applying an active multi-cycle coupon when remaining. */
    private fun recurringPriceCents(plan: Plan, current: Subscription): Long {
        val full = plan.priceCents(current.cycle)
        val remaining = current.couponCyclesRemaining
        if (remaining == null || remaining <= 0) return full
        val couponId = current.couponId ?: return full
        val coupon = coupons.findById(couponId) ?: return full
        return applyDiscount(full, coupon.discountPercent)
    }

    private fun usageFitsTarget(ownerUserId: UUID, target: Plan): Boolean {
        val usage = usageLookup.usageFor(ownerUserId)
        val groupsOk = target.maxGroups == null || usage.ownedGroupCount <= target.maxGroups
        val athletesOk = target.maxAthletes == null || usage.occupyingAthleteCount <= target.maxAthletes
        return groupsOk && athletesOk
    }
}
