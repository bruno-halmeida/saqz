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
        val name = command.name.trim()
        val email = command.email.trim()
        val cpfDigits = command.cpfCnpj.filter { it.isDigit() }
        if (name.isBlank() || !isValidEmail(email) || !isValidCpfCnpj(cpfDigits)) {
            return CreateSubscriptionResult.InvalidCustomerDetails
        }

        val now = clock.instant()
        val couponOutcome = resolveCoupon(command.couponCode, command.ownerUserId, now)
        if (couponOutcome is CouponOutcome.Failure) return couponOutcome.result
        val coupon = (couponOutcome as CouponOutcome.Ok).coupon
        val valueCents = discountedPriceCents(command.plan, command.cycle, coupon)

        // Commit local row first (Asaas side effects + insert). Checkout enrichment is best-effort after.
        val committed = transaction.inTransaction {
            subscriptions.lockOwner(command.ownerUserId)
            subscriptions.findByOwnerUserId(command.ownerUserId)?.let { existing ->
                return@inTransaction existing
            }

            val customerId = asaasGateway.createCustomer(
                ownerUserId = command.ownerUserId,
                name = name,
                email = email,
                cpfCnpj = cpfDigits,
            )
            val asaasSubscriptionId = asaasGateway.createSubscription(
                asaasCustomerId = customerId,
                plan = command.plan,
                cycle = command.cycle,
                valueCents = valueCents,
                billingType = command.billingType,
                idempotencyKey = "subscription-create:${command.ownerUserId}:${command.requestId}",
            )

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
            subscriptions.insert(subscription)
            if (coupon != null) {
                coupons.saveRedemption(
                    CouponRedemption(couponId = coupon.id, userId = command.ownerUserId, redeemedAt = now),
                )
            }
            subscription
        }

        if (committed.firstConfirmedAt != null || committed.status == SubscriptionStatus.CANCELED) {
            return CreateSubscriptionResult.AlreadySubscribed
        }

        val checkout = resolveCheckout(committed.asaasSubscriptionId)
        return CreateSubscriptionResult.Success(
            subscription = committed,
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

    private fun resolveCheckout(asaasSubscriptionId: String): Checkout =
        runCatching {
            val paymentId = asaasGateway.findLatestPaymentIdForSubscription(asaasSubscriptionId)
                ?: return@runCatching Checkout(null, null)
            val pix = runCatching { asaasGateway.regeneratePixPayload(paymentId) }.getOrNull()
            val invoice = asaasGateway.findPaymentInvoiceUrl(paymentId)
            Checkout(pixCopyPaste = pix, invoiceUrl = invoice)
        }.getOrDefault(Checkout(null, null))

    private fun isValidEmail(email: String): Boolean =
        email.length in 3..254 && email.contains('@') && email.indexOf('@') in 1 until email.lastIndex

    private fun isValidCpfCnpj(digits: String): Boolean =
        digits.length == 11 || digits.length == 14
}
