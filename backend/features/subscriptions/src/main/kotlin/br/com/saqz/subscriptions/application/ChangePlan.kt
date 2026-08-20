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
        val pixQrCodeBase64: String? = null,
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

    /** A pending upgrade charge exists; scheduling a downgrade over it could race the upgrade webhook. */
    data object UpgradePendingBlocksChange : ChangePlanResult
}

private sealed interface ChangePlanOutcome {
    data class Immediate(val result: ChangePlanResult) : ChangePlanOutcome
    data class NeedsCheckout(
        val subscription: Subscription,
        val chargedCents: Long,
        val chargeId: String,
    ) : ChangePlanOutcome
}

class ChangePlan(
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val usageLookup: OwnerPlanUsageLookup,
    private val coupons: CouponRepository,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
) {
    fun execute(command: ChangePlanCommand): ChangePlanResult {
        // Checkout enrichment (Pix/invoice lookup) must stay OUT of the transaction: it calls Asaas
        // and can throw after the one-off charge already exists there for real. Rolling back the local
        // save on that failure would desync from Asaas and cause a retry to create a duplicate charge.
        val outcome = transaction.inTransaction {
            val current = subscriptions.findByOwnerUserIdForUpdate(command.ownerUserId)
                ?: return@inTransaction ChangePlanOutcome.Immediate(ChangePlanResult.NotFound)
            if (current.status == SubscriptionStatus.CANCELED) {
                return@inTransaction ChangePlanOutcome.Immediate(ChangePlanResult.NotActive)
            }
            if (current.plan == command.targetPlan) {
                return@inTransaction ChangePlanOutcome.Immediate(ChangePlanResult.SamePlan)
            }

            val currentPrice = recurringPriceCents(current.plan, current)
            val targetPrice = recurringPriceCents(command.targetPlan, current)
            if (targetPrice > currentPrice) {
                upgrade(current, command, currentPrice, targetPrice)
            } else {
                // A downgrade scheduled over a pending upgrade charge would be silently overwritten
                // if that old charge is paid after the downgrade already applied at renewal.
                if (current.pendingUpgradeChargeId != null) {
                    return@inTransaction ChangePlanOutcome.Immediate(ChangePlanResult.UpgradePendingBlocksChange)
                }
                ChangePlanOutcome.Immediate(downgrade(current, command, targetPrice))
            }
        }

        return when (outcome) {
            is ChangePlanOutcome.Immediate -> outcome.result
            is ChangePlanOutcome.NeedsCheckout ->
                upgradeCheckout(outcome.subscription, outcome.chargedCents, outcome.chargeId)
        }
    }

    private fun upgrade(
        current: Subscription,
        command: ChangePlanCommand,
        currentPrice: Long,
        targetPrice: Long,
    ): ChangePlanOutcome {
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
            return ChangePlanOutcome.Immediate(ChangePlanResult.Upgraded(updated, chargedCents = 0L))
        }

        // Reuse an in-flight upgrade charge only for the SAME target plan — if the target changed,
        // that charge is still for the old plan/value, so a new one is required.
        val existingChargeId = current.pendingUpgradeChargeId
        if (existingChargeId != null && current.pendingUpgradePlan == command.targetPlan) {
            return ChangePlanOutcome.NeedsCheckout(current, chargedCents, existingChargeId)
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
        return ChangePlanOutcome.NeedsCheckout(updated, chargedCents, chargeId)
    }

    private fun upgradeCheckout(
        subscription: Subscription,
        chargedCents: Long,
        chargeId: String,
    ): ChangePlanResult.UpgradePendingPayment {
        val pix = runCatching { asaasGateway.regeneratePixPayload(chargeId) }.getOrNull()
        val invoice = runCatching { asaasGateway.findPaymentInvoiceUrl(chargeId) }.getOrNull()
        return ChangePlanResult.UpgradePendingPayment(
            subscription = subscription,
            chargedCents = chargedCents,
            oneOffChargeId = chargeId,
            pixCopyPaste = pix?.payload,
            invoiceUrl = invoice,
            pixQrCodeBase64 = pix?.encodedImage,
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
