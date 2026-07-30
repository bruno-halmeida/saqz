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
}

class CreateSubscription(
    private val subscriptions: SubscriptionRepository,
    private val coupons: CouponRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
) {
    fun execute(command: CreateSubscriptionCommand): CreateSubscriptionResult {
        if (command.name.isBlank() || command.email.isBlank() || command.cpfCnpj.isBlank()) {
            return CreateSubscriptionResult.InvalidCustomerDetails
        }
        subscriptions.findByOwnerUserId(command.ownerUserId)?.let {
            return CreateSubscriptionResult.AlreadySubscribed
        }

        val now = clock.instant()
        val couponOutcome = resolveCoupon(command.couponCode, command.ownerUserId, now)
        if (couponOutcome is CouponOutcome.Failure) return couponOutcome.result

        val coupon = (couponOutcome as CouponOutcome.Ok).coupon
        val valueCents = discountedPriceCents(command.plan, command.cycle, coupon)

        val customerId = asaasGateway.createCustomer(
            ownerUserId = command.ownerUserId,
            name = command.name.trim(),
            email = command.email.trim(),
            cpfCnpj = command.cpfCnpj.filter { it.isDigit() },
        )
        val asaasSubscriptionId = asaasGateway.createSubscription(
            asaasCustomerId = customerId,
            plan = command.plan,
            cycle = command.cycle,
            valueCents = valueCents,
            billingType = command.billingType,
            idempotencyKey = "subscription-create:${command.ownerUserId}:${command.requestId}",
        )

        // Persist before any webhook can land (VUL-105 returns 503 until this row exists).
        val subscription = Subscription(
            ownerUserId = command.ownerUserId,
            plan = command.plan,
            cycle = command.cycle,
            asaasCustomerId = customerId,
            asaasSubscriptionId = asaasSubscriptionId,
            currentPeriodEnd = initialPeriodEnd(now, command.cycle),
            status = SubscriptionStatus.PAST_DUE,
            pastDueSince = now,
            couponId = coupon?.id,
            couponCyclesRemaining = coupon?.durationCycles,
        )

        transaction.inTransaction {
            subscriptions.insert(subscription)
            if (coupon != null) {
                coupons.saveRedemption(
                    CouponRedemption(couponId = coupon.id, userId = command.ownerUserId, redeemedAt = now),
                )
            }
        }

        val checkout = resolveCheckout(asaasSubscriptionId, command.billingType)
        return CreateSubscriptionResult.Success(
            subscription = subscription,
            billingType = command.billingType,
            pixCopyPaste = checkout.pixCopyPaste,
            invoiceUrl = checkout.invoiceUrl,
        )
    }

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

    private fun resolveCheckout(asaasSubscriptionId: String, billingType: AsaasBillingType): Checkout {
        val paymentId = asaasGateway.findLatestPaymentIdForSubscription(asaasSubscriptionId)
            ?: return Checkout(null, null)
        return when (billingType) {
            AsaasBillingType.PIX -> Checkout(
                pixCopyPaste = asaasGateway.regeneratePixPayload(paymentId),
                invoiceUrl = null,
            )
            AsaasBillingType.CREDIT_CARD -> Checkout(
                pixCopyPaste = null,
                invoiceUrl = asaasGateway.findPaymentInvoiceUrl(paymentId),
            )
        }
    }
}
