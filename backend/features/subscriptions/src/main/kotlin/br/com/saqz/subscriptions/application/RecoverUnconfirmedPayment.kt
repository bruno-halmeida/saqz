package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.SubscriptionPricing.applyDiscount
import br.com.saqz.subscriptions.application.SubscriptionPricing.hasActiveCouponDiscount
import br.com.saqz.subscriptions.application.SubscriptionPricing.priceCents
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import java.time.Clock
import java.time.Instant

/**
 * Confirma no nosso banco uma cobrança que a Asaas já reconheceu como paga, sem esperar o
 * webhook. O webhook é push e chega uma vez: se a entrega se perdeu, [GetMySubscription] e o
 * retry de [CreateSubscription] perguntam o estado direto — senão o Pix pago fica eterno em
 * `PAST_DUE` e o app continua pedindo assinatura.
 *
 * A regra de "está pago" é [PaymentConfirmation] no primeiro checkout e a mesma aplicação de
 * plano do webhook ([ProcessAsaasWebhook]) no upgrade avulso. Os dois caminhos não podem
 * divergir no primeiro ajuste de ciclo, cupom ou troca de plano.
 */
class RecoverUnconfirmedPayment(
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
    private val coupons: CouponRepository? = null,
) {
    fun recoverIfPaid(committed: Subscription): Subscription {
        val afterFirstCheckout = recoverFirstCheckout(committed)
        return recoverPendingUpgrade(afterFirstCheckout)
    }

    private fun recoverFirstCheckout(committed: Subscription): Subscription {
        if (committed.firstConfirmedAt != null) return committed
        val paymentId = runCatching {
            asaasGateway.findLatestPaymentIdForSubscription(committed.asaasSubscriptionId)
        }.getOrNull() ?: return committed
        val payment = runCatching { asaasGateway.findPayment(paymentId) }.getOrNull()
        if (!PaymentConfirmation.isPaid(payment?.status)) return committed
        return confirmPaidCharge(committed, paymentId, clock.instant())
    }

    /**
     * Cobrança avulsa de upgrade não entra em [findLatestPaymentIdForSubscription] — aquela
     * lista é da assinatura recorrente. Sem isto, "Já paguei" / o poll do /me vê o plano
     * antigo para sempre se o webhook da Asaas se perder.
     */
    private fun recoverPendingUpgrade(committed: Subscription): Subscription {
        if (committed.canceledAt != null) return committed
        val chargeId = committed.pendingUpgradeChargeId ?: return committed
        val payment = runCatching { asaasGateway.findPayment(chargeId) }.getOrNull() ?: return committed
        if (!PaymentConfirmation.isPaid(payment.status)) return committed
        return applyPaidUpgrade(committed, chargeId)
    }

    private fun confirmPaidCharge(committed: Subscription, paymentId: String, now: Instant): Subscription =
        transaction.inTransaction {
            val current = subscriptions.findByOwnerUserIdForUpdate(committed.ownerUserId) ?: committed
            if (current.firstConfirmedAt != null ||
                PaymentConfirmation.isAlreadyConfirmed(current, paymentId)
            ) {
                return@inTransaction current
            }
            val outcome = PaymentConfirmation.confirm(current, paymentId, now)
            subscriptions.save(outcome.subscription)
            outcome.fullPriceCentsToPush?.let { cents ->
                runCatching { asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, cents) }
            }
            outcome.subscription
        }

    private fun applyPaidUpgrade(committed: Subscription, chargeId: String): Subscription =
        transaction.inTransaction {
            val current = subscriptions.findByOwnerUserIdForUpdate(committed.ownerUserId) ?: committed
            if (current.canceledAt != null) return@inTransaction current
            if (current.pendingUpgradeChargeId != chargeId) return@inTransaction current
            val target = current.pendingUpgradePlan ?: return@inTransaction current
            val recurring = recurringPriceCents(target, current)
            asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, recurring)
            val updated = current.copy(
                plan = target,
                pendingUpgradePlan = null,
                pendingUpgradeChargeId = null,
                pendingPlan = null,
                pendingPlanEffectiveAt = null,
                lastConfirmedPaymentId = chargeId,
            )
            subscriptions.save(updated)
            updated
        }

    private fun recurringPriceCents(plan: Plan, current: Subscription): Long {
        val full = plan.priceCents(current.cycle)
        if (!hasActiveCouponDiscount(current.couponId, current.couponCyclesRemaining)) return full
        val couponId = current.couponId ?: return full
        val coupon = coupons?.findById(couponId) ?: return full
        return applyDiscount(full, coupon.discountPercent)
    }
}
