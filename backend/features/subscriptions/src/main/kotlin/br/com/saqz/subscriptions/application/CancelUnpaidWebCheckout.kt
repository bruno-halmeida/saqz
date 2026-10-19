package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.util.UUID

/**
 * Compra validada pela App Store ou pelo Google Play: o checkout do site que nunca foi pago (`firstConfirmedAt` nulo, sem
 * cancelamento) é cancelado no Asaas e a linha fica cancelada, para a fatura antiga não poder mais ser paga por cima da loja
 * (cobrança dupla). Assinatura do site que já foi paga alguma vez não é tocada.
 *
 * Vale a regra do [CancelSubscription] (cancela a assinatura no Asaas, que para as próximas cobranças), mais a cobrança pendente,
 * que é cancelada à parte. Antes de cancelar confere no Asaas se a cobrança já foi paga (webhook atrasado): paga não se cancela.
 *
 * Nunca derruba quem chama: falha no Asaas é registrada (`store_purchase_cancel_unpaid_checkout`) e a linha segue como estava,
 * então a próxima validação da loja (o app reenvia a compra ao abrir; as notificações da loja também) tenta de novo. Idempotente:
 * linha já cancelada, já paga ou inexistente não faz nada.
 */
class CancelUnpaidWebCheckout(
    private val subscriptions: SubscriptionRepository,
    private val gateway: () -> AsaasGateway?,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
) {
    fun execute(ownerUserId: UUID) {
        try {
            run(ownerUserId)
        } catch (error: Exception) {
            JobLog.itemFailed("store_purchase_cancel_unpaid_checkout", "cancel_failed", error, "userId" to ownerUserId)
        }
    }

    private fun run(ownerUserId: UUID) {
        val cancelledPaymentId = transaction.inTransaction {
            val current = subscriptions.findByOwnerUserIdForUpdate(ownerUserId) ?: return@inTransaction null
            if (current.firstConfirmedAt != null || current.canceledAt != null || current.status == SubscriptionStatus.CANCELED) {
                return@inTransaction null
            }
            val asaas = gateway()
            if (asaas == null) {
                JobLog.itemFailed("store_purchase_cancel_unpaid_checkout", "asaas_unavailable", "ASAAS_UNAVAILABLE", "userId" to ownerUserId)
                return@inTransaction null
            }
            val paymentId = asaas.findLatestPaymentIdForSubscription(current.asaasSubscriptionId)
            val payment = paymentId?.let(asaas::findPayment)
            if (payment?.status in PAID_STATUSES) {
                // O webhook só atrasou: é um pagamento de verdade, quem trata é o fluxo normal.
                JobLog.item("store_purchase_cancel_unpaid_checkout", "skipped_paid", "userId" to ownerUserId)
                return@inTransaction null
            }
            asaas.cancelSubscription(current.asaasSubscriptionId)
            subscriptions.save(current.copy(status = SubscriptionStatus.CANCELED, canceledAt = clock.instant()))
            JobLog.item("store_purchase_cancel_unpaid_checkout", "canceled", "userId" to ownerUserId)
            paymentId
        } ?: return
        // A assinatura já foi cancelada e gravada; a cobrança pendente é desfeita à parte e uma falha aqui não desfaz nada.
        try {
            gateway()?.cancelPayment(cancelledPaymentId)
        } catch (error: Exception) {
            JobLog.itemFailed("store_purchase_cancel_unpaid_checkout", "cancel_payment_failed", error, "userId" to ownerUserId, "paymentId" to cancelledPaymentId)
        }
    }

    private companion object {
        val PAID_STATUSES = setOf("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH")
    }
}
