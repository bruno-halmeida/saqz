package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
import br.com.saqz.subscriptions.application.SubscriptionPricing.applyDiscount
import br.com.saqz.subscriptions.application.SubscriptionPricing.hasActiveCouponDiscount
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
    /** Charge created; plan applies only after PAYMENT_CONFIRMED on the one-off charge. */
    data class UpgradePendingPayment(
        val subscription: Subscription,
        val chargedCents: Long,
        val oneOffChargeId: String,
        val pixCopyPaste: String?,
        val invoiceUrl: String?,
    ) : ChangePlanResult

    /** Zero prorata delta — plan applied immediately. */
    data class Upgraded(
        val subscription: Subscription,
        val chargedCents: Long,
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
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
) {
    fun execute(command: ChangePlanCommand): ChangePlanResult =
        transaction.inTransaction {
            val current = subscriptions.findByOwnerUserIdForUpdate(command.ownerUserId)
                ?: return@inTransaction ChangePlanResult.NotFound
            if (current.status == SubscriptionStatus.CANCELED) {
                return@inTransaction ChangePlanResult.NotActive
            }
            if (current.plan == command.targetPlan) {
                return@inTransaction ChangePlanResult.SamePlan
            }

            val currentPrice = recurringPriceCents(current.plan, current)
            val targetPrice = recurringPriceCents(command.targetPlan, current)
            if (targetPrice > currentPrice) {
                upgrade(current, command, currentPrice, targetPrice)
            } else {
                downgrade(current, command, targetPrice)
            }
        }

    private fun upgrade(
        current: Subscription,
        command: ChangePlanCommand,
        currentPrice: Long,
        targetPrice: Long,
    ): ChangePlanResult {
        val now = clock.instant()
        val chargedCents = prorataUpgradeCents(
            currentPriceCents = currentPrice,
            targetPriceCents = targetPrice,
            now = now,
            currentPeriodEnd = current.currentPeriodEnd,
            cycle = current.cycle,
        )

        if (chargedCents <= 0L) {
            asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, targetPrice)
            val updated = current.copy(
                plan = command.targetPlan,
                pendingPlan = null,
                pendingPlanEffectiveAt = null,
                pendingUpgradePlan = null,
                pendingUpgradeChargeId = null,
            )
            subscriptions.save(updated)
            return ChangePlanResult.Upgraded(updated, chargedCents = 0L)
        }

        // Reuse an in-flight upgrade charge only for the SAME target plan — if the target changed,
        // that charge is still for the old plan/value, so a new one is required.
        val existingChargeId = current.pendingUpgradeChargeId
        if (existingChargeId != null && current.pendingUpgradePlan == command.targetPlan) {
            return upgradeCheckout(current, chargedCents, existingChargeId)
        }

        val chargeId = asaasGateway.createOneOffCharge(
            asaasCustomerId = current.asaasCustomerId,
            valueCents = chargedCents,
            description = "Upgrade Saqz ${current.plan.name} → ${command.targetPlan.name}",
            idempotencyKey = "subscription-upgrade:${command.ownerUserId}:${command.requestId}",
        )
        val updated = current.copy(
            pendingUpgradePlan = command.targetPlan,
            pendingUpgradeChargeId = chargeId,
        )
        subscriptions.save(updated)
        return upgradeCheckout(updated, chargedCents, chargeId)
    }

    private fun upgradeCheckout(
        subscription: Subscription,
        chargedCents: Long,
        chargeId: String,
    ): ChangePlanResult.UpgradePendingPayment {
        val pix = runCatching { asaasGateway.regeneratePixPayload(chargeId) }.getOrNull()
        val invoice = asaasGateway.findPaymentInvoiceUrl(chargeId)
        return ChangePlanResult.UpgradePendingPayment(
            subscription = subscription,
            chargedCents = chargedCents,
            oneOffChargeId = chargeId,
            pixCopyPaste = pix,
            invoiceUrl = invoice,
        )
    }

    private fun downgrade(
        current: Subscription,
        command: ChangePlanCommand,
        targetPrice: Long,
    ): ChangePlanResult {
        if (!usageFitsTarget(command.ownerUserId, command.targetPlan)) {
            return ChangePlanResult.DowngradeBlockedByUsage
        }
        asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, targetPrice)
        val updated = current.copy(
            pendingPlan = command.targetPlan,
            pendingPlanEffectiveAt = current.currentPeriodEnd,
        )
        subscriptions.save(updated)
        return ChangePlanResult.DowngradeScheduled(updated)
    }

    /**
     * List price of [plan], with active coupon discount when present.
     * Permanent coupons (`couponId` set, `couponCyclesRemaining` null) keep the discount.
     */
    private fun recurringPriceCents(plan: Plan, current: Subscription): Long {
        val full = plan.priceCents(current.cycle)
        if (!hasActiveCouponDiscount(current.couponId, current.couponCyclesRemaining)) return full
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
