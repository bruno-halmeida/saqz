package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.application.SubscriptionPricing.priceCents
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Instant
import java.time.ZoneOffset

data class ConfirmOutcome(
    val subscription: Subscription,
    /** Valor cheio a empurrar para o Asaas quando o cupom acabou neste ciclo. */
    val fullPriceCentsToPush: Long?,
)

/**
 * Regra unica de "esta cobranca foi paga". Dois caminhos chegam aqui e precisam produzir
 * exatamente o mesmo estado: o webhook do Asaas ([ProcessAsaasWebhook]) e a consulta ativa da
 * recuperacao de checkout ([CreateSubscription]) — se um deles reimplementasse a regra, as duas
 * versoes divergiriam no primeiro ajuste de ciclo ou cupom.
 *
 * A colisao entre os dois caminhos e resolvida por `lastConfirmedPaymentId`: quem confirmar
 * primeiro grava a cobranca, e o outro reconhece pelo [isAlreadyConfirmed] e vira no-op em vez
 * de avancar `currentPeriodEnd` um ciclo de graca.
 */
object PaymentConfirmation {
    /** Status do Asaas que significam dinheiro reconhecido para a cobranca. */
    val PAID_STATUSES = setOf("CONFIRMED", "RECEIVED", "RECEIVED_IN_CASH")

    fun isPaid(status: String?): Boolean = status != null && status.uppercase() in PAID_STATUSES

    /** True quando esta cobranca ja foi aplicada — cobranca sem id nao tem como deduplicar. */
    fun isAlreadyConfirmed(current: Subscription, paymentId: String?): Boolean =
        paymentId != null && paymentId == current.lastConfirmedPaymentId

    fun confirm(current: Subscription, paymentId: String?, now: Instant): ConfirmOutcome {
        // First confirmation keeps the period set at create; renewals advance one cycle.
        val periodEnd = if (current.firstConfirmedAt == null) {
            current.currentPeriodEnd
        } else {
            advancePeriodEnd(current.currentPeriodEnd, current.cycle)
        }
        var next = current.copy(
            status = SubscriptionStatus.ACTIVE,
            currentPeriodEnd = periodEnd,
            pastDueSince = null,
            firstConfirmedAt = current.firstConfirmedAt ?: now,
            lastConfirmedPaymentId = paymentId ?: current.lastConfirmedPaymentId,
        )

        // Any recurring confirmation while a downgrade is scheduled is the renewal that should
        // apply it — do not gate on wall-clock vs pendingPlanEffectiveAt.
        val pending = next.pendingPlan
        if (pending != null) {
            next = next.copy(
                plan = pending,
                pendingPlan = null,
                pendingPlanEffectiveAt = null,
            )
        }

        var fullPriceCentsToPush: Long? = null
        val remaining = next.couponCyclesRemaining
        if (remaining != null && remaining > 0) {
            val after = remaining - 1
            if (after == 0) {
                next = next.copy(couponCyclesRemaining = null)
                fullPriceCentsToPush = next.plan.priceCents(next.cycle)
            } else {
                next = next.copy(couponCyclesRemaining = after)
            }
        }

        return ConfirmOutcome(next, fullPriceCentsToPush)
    }

    fun advancePeriodEnd(currentPeriodEnd: Instant, cycle: SubscriptionCycle): Instant {
        val zoned = currentPeriodEnd.atZone(ZoneOffset.UTC)
        return when (cycle) {
            SubscriptionCycle.MONTHLY -> zoned.plusMonths(1).toInstant()
            SubscriptionCycle.ANNUAL -> zoned.plusYears(1).toInstant()
        }
    }
}
