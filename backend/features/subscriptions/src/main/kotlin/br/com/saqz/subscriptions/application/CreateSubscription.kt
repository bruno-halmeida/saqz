package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.SubscriptionPricing.discountedPriceCents
import br.com.saqz.subscriptions.application.SubscriptionPricing.initialPeriodEnd
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateSubscriptionCommand(
    val ownerUserId: UUID,
    val requestId: UUID,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val billingType: AsaasBillingType,
    val name: String,
    val email: String,
    val cpfCnpj: String,
    val couponCode: String? = null,
)

sealed interface CreateSubscriptionResult {
    data class Success(
        val subscription: Subscription,
        val billingType: AsaasBillingType,
        val pixCopyPaste: String?,
        val invoiceUrl: String?,
    ) : CreateSubscriptionResult

    data object AlreadySubscribed : CreateSubscriptionResult
    data object CouponNotFound : CreateSubscriptionResult
    data object CouponExpired : CreateSubscriptionResult
    data object CouponAlreadyRedeemed : CreateSubscriptionResult
    data object InvalidCustomerDetails : CreateSubscriptionResult

    /** An unconfirmed subscription is pending for a DIFFERENT plan/cycle than this request. */
    data object PendingCheckoutMismatch : CreateSubscriptionResult
}

class CreateSubscription(
    private val subscriptions: SubscriptionRepository,
    private val coupons: CouponRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
) {
    fun execute(command: CreateSubscriptionCommand): CreateSubscriptionResult {
        val name = command.name.trim()
        val email = command.email.trim()
        val cpfDigits = command.cpfCnpj.filter { it.isDigit() }
        if (name.isBlank() || !isValidEmail(email) || !isValidCpfCnpj(cpfDigits)) {
            return CreateSubscriptionResult.InvalidCustomerDetails
        }

        val now = clock.instant()

        // Commit local row first (Asaas side effects + insert/reactivate). Checkout is best-effort after.
        // Coupon resolution only runs for a genuinely new Asaas subscription (new or reactivated) — an
        // unconfirmed subscription being recovered must not re-check redemption, since it was already
        // recorded on the first attempt and would wrongly fail the retry.
        val outcome = transaction.inTransaction {
            subscriptions.lockOwner(command.ownerUserId)
            val existing = subscriptions.findByOwnerUserIdForUpdate(command.ownerUserId)
            when {
                existing == null -> newSubscriptionOutcome(command, now) { c, v ->
                    createNew(command, name, email, cpfDigits, c, v, now)
                }
                existing.status == SubscriptionStatus.CANCELED ->
                    newSubscriptionOutcome(command, now) { c, v ->
                        reactivate(existing, command, name, email, cpfDigits, c, v, now)
                    }
                existing.firstConfirmedAt == null ->
                    // Legacy rows predating billingType have it null — only enforce the match once
                    // we actually know what the existing charge's billing type was.
                    if (existing.plan == command.plan &&
                        existing.cycle == command.cycle &&
                        (existing.billingType == null || existing.billingType == command.billingType)
                    ) {
                        CommitOutcome.Committed(existing)
                    } else {
                        CommitOutcome.Rejected(CreateSubscriptionResult.PendingCheckoutMismatch)
                    }
                else -> CommitOutcome.Committed(existing) // confirmed active/past_due — AlreadySubscribed after commit
            }
        }

        val committed = when (outcome) {
            is CommitOutcome.Rejected -> return outcome.result
            is CommitOutcome.Committed -> outcome.subscription
        }

        if (committed.status != SubscriptionStatus.CANCELED &&
            committed.firstConfirmedAt == null
        ) {
            val checkout = resolveCheckout(committed.asaasSubscriptionId)
            return CreateSubscriptionResult.Success(
                subscription = committed,
                billingType = command.billingType,
                pixCopyPaste = checkout.pixCopyPaste,
                invoiceUrl = checkout.invoiceUrl,
            )
        }

        return CreateSubscriptionResult.AlreadySubscribed
    }

    private fun newSubscriptionOutcome(
        command: CreateSubscriptionCommand,
        now: Instant,
        create: (Coupon?, Long) -> Subscription,
    ): CommitOutcome {
        val couponOutcome = resolveCoupon(command.couponCode, command.ownerUserId, now)
        if (couponOutcome is CouponOutcome.Failure) return CommitOutcome.Rejected(couponOutcome.result)
        val coupon = (couponOutcome as CouponOutcome.Ok).coupon
        val valueCents = discountedPriceCents(command.plan, command.cycle, coupon)
        return CommitOutcome.Committed(create(coupon, valueCents))
    }

    private sealed interface CommitOutcome {
        data class Committed(val subscription: Subscription) : CommitOutcome
        data class Rejected(val result: CreateSubscriptionResult) : CommitOutcome
    }

    private fun createNew(
        command: CreateSubscriptionCommand,
        name: String,
        email: String,
        cpfDigits: String,
        coupon: Coupon?,
        valueCents: Long,
        now: Instant,
    ): Subscription {
        val customerId = asaasGateway.createCustomer(command.ownerUserId, name, email, cpfDigits)
        val asaasSubscriptionId = asaasGateway.createSubscription(
            asaasCustomerId = customerId,
            plan = command.plan,
            cycle = command.cycle,
            valueCents = valueCents,
            billingType = command.billingType,
            idempotencyKey = "subscription-create:${command.ownerUserId}:${command.requestId}",
        )
        val subscription = blankSubscription(
            ownerUserId = command.ownerUserId,
            plan = command.plan,
            cycle = command.cycle,
            customerId = customerId,
            asaasSubscriptionId = asaasSubscriptionId,
            billingType = command.billingType,
            now = now,
            coupon = coupon,
        )
        subscriptions.insert(subscription)
        if (coupon != null) {
            coupons.saveRedemption(CouponRedemption(coupon.id, command.ownerUserId, now))
        }
        return subscription
    }

    private fun reactivate(
        existing: Subscription,
        command: CreateSubscriptionCommand,
        name: String,
        email: String,
        cpfDigits: String,
        coupon: Coupon?,
        valueCents: Long,
        now: Instant,
    ): Subscription {
        val customerId = asaasGateway.createCustomer(command.ownerUserId, name, email, cpfDigits)
        val asaasSubscriptionId = asaasGateway.createSubscription(
            asaasCustomerId = customerId,
            plan = command.plan,
            cycle = command.cycle,
            valueCents = valueCents,
            billingType = command.billingType,
            idempotencyKey = "subscription-create:${command.ownerUserId}:${command.requestId}",
        )
        val reactivated = blankSubscription(
            ownerUserId = command.ownerUserId,
            plan = command.plan,
            cycle = command.cycle,
            customerId = customerId,
            asaasSubscriptionId = asaasSubscriptionId,
            billingType = command.billingType,
            now = now,
            coupon = coupon,
        )
        subscriptions.save(reactivated)
        if (coupon != null && !coupons.hasRedemption(coupon.id, command.ownerUserId)) {
            coupons.saveRedemption(CouponRedemption(coupon.id, command.ownerUserId, now))
        }
        return reactivated
    }

    private fun blankSubscription(
        ownerUserId: UUID,
        plan: Plan,
        cycle: SubscriptionCycle,
        customerId: String,
        asaasSubscriptionId: String,
        billingType: AsaasBillingType,
        now: Instant,
        coupon: Coupon?,
    ) = Subscription(
        ownerUserId = ownerUserId,
        plan = plan,
        cycle = cycle,
        asaasCustomerId = customerId,
        asaasSubscriptionId = asaasSubscriptionId,
        billingType = billingType,
        currentPeriodEnd = initialPeriodEnd(now, cycle),
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = now,
        canceledAt = null,
        pendingPlan = null,
        pendingPlanEffectiveAt = null,
        couponId = coupon?.id,
        couponCyclesRemaining = coupon?.durationCycles,
        firstConfirmedAt = null,
        pendingUpgradePlan = null,
        pendingUpgradeChargeId = null,
    )

    private sealed interface CouponOutcome {
        data class Ok(val coupon: Coupon?) : CouponOutcome
        data class Failure(val result: CreateSubscriptionResult) : CouponOutcome
    }

    private fun resolveCoupon(code: String?, ownerUserId: UUID, now: Instant): CouponOutcome {
        if (code.isNullOrBlank()) return CouponOutcome.Ok(null)
        val coupon = coupons.findByCode(code.trim())
            ?: return CouponOutcome.Failure(CreateSubscriptionResult.CouponNotFound)
        val validUntil = coupon.validUntil
        if (validUntil != null && validUntil.isBefore(now)) {
            return CouponOutcome.Failure(CreateSubscriptionResult.CouponExpired)
        }
        if (coupons.hasRedemption(coupon.id, ownerUserId)) {
            return CouponOutcome.Failure(CreateSubscriptionResult.CouponAlreadyRedeemed)
        }
        return CouponOutcome.Ok(coupon)
    }

    private data class Checkout(val pixCopyPaste: String?, val invoiceUrl: String?)

    private fun resolveCheckout(asaasSubscriptionId: String): Checkout {
        // Best-effort enrichment AFTER the subscription is already committed — any failure here
        // must not surface as an error for a create that already succeeded.
        val paymentId = runCatching { asaasGateway.findLatestPaymentIdForSubscription(asaasSubscriptionId) }
            .getOrNull() ?: return Checkout(null, null)
        val pix = runCatching { asaasGateway.regeneratePixPayload(paymentId) }.getOrNull()
        val invoice = runCatching { asaasGateway.findPaymentInvoiceUrl(paymentId) }.getOrNull()
        return Checkout(pixCopyPaste = pix, invoiceUrl = invoice)
    }

    private fun isValidEmail(email: String): Boolean =
        email.length in 3..254 && email.contains('@') && email.indexOf('@') in 1 until email.lastIndex

    private fun isValidCpfCnpj(digits: String): Boolean =
        digits.length == 11 || digits.length == 14
}
