package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Subscription
import java.time.Clock
import java.time.Instant

/**
 * Confirma no nosso banco uma cobrança que a Asaas já reconheceu como paga, sem esperar o
 * webhook. O webhook é push e chega uma vez: se a entrega se perdeu, [GetMySubscription] e o
 * retry de [CreateSubscription] perguntam o estado direto — senão o Pix pago fica eterno em
 * `PAST_DUE` e o app continua pedindo assinatura.
 *
 * A regra de "está pago" é [PaymentConfirmation]: o mesmo estado do webhook, para os dois
 * caminhos não divergirem no primeiro ajuste de ciclo ou cupom.
 */
class RecoverUnconfirmedPayment(
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
) {
    fun recoverIfPaid(committed: Subscription): Subscription {
        if (committed.firstConfirmedAt != null) return committed
        val paymentId = runCatching {
            asaasGateway.findLatestPaymentIdForSubscription(committed.asaasSubscriptionId)
        }.getOrNull() ?: return committed
        val payment = runCatching { asaasGateway.findPayment(paymentId) }.getOrNull()
        if (!PaymentConfirmation.isPaid(payment?.status)) return committed
        return confirmPaidCharge(committed, paymentId, clock.instant())
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
}
