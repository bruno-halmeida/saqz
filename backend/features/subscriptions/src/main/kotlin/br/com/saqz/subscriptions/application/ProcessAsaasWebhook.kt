package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class AsaasWebhookCommand(
    val asaasEventId: String,
    val eventType: String,
    val asaasSubscriptionId: String?,
    val asaasPaymentId: String?,
    val rawPayload: String,
)

sealed interface ProcessAsaasWebhookResult {
    data object Unauthorized : ProcessAsaasWebhookResult
    data object Accepted : ProcessAsaasWebhookResult
    /** Local row not committed yet — Asaas should redeliver (HTTP 503). */
    data object SubscriptionNotReady : ProcessAsaasWebhookResult
}

fun interface AsaasWebhookProcessor {
    fun execute(providedToken: String?, command: AsaasWebhookCommand): ProcessAsaasWebhookResult
}

/**
 * Only path that may mark a subscription as paid/active. Token is checked before any
 * side effect; [SubscriptionEvent] insert by `asaasEventId` is the idempotency gate.
 */
class ProcessAsaasWebhook(
    private val expectedToken: String,
    private val events: SubscriptionEventStore,
    private val subscriptions: SubscriptionRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
    private val coupons: CouponRepository? = null,
    private val newEventId: () -> UUID = UUID::randomUUID,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : AsaasWebhookProcessor {
    override fun execute(providedToken: String?, command: AsaasWebhookCommand): ProcessAsaasWebhookResult {
        if (!tokenMatches(providedToken, expectedToken)) {
            return ProcessAsaasWebhookResult.Unauthorized
        }
        if (command.asaasEventId.isBlank() || command.eventType.isBlank()) {
            return ProcessAsaasWebhookResult.Accepted
        }

        val now = clock.instant()
        return transaction.inTransaction {
            if (command.eventType !in DOMAIN_EVENT_TYPES) {
                return@inTransaction claimAndAccept(command, now)
            }

            // Already-seen events (e.g. upgrade redelivery after pendingUpgradeChargeId cleared)
            // must not 503 forever — accept without re-resolving the subscription.
            if (events.exists(command.asaasEventId)) {
                return@inTransaction ProcessAsaasWebhookResult.Accepted
            }

            val current = resolveSubscription(command)
                ?: return@inTransaction when {
                    command.asaasSubscriptionId.isNullOrBlank() &&
                        command.asaasPaymentId.isNullOrBlank() -> claimAndAccept(command, now)
                    else -> ProcessAsaasWebhookResult.SubscriptionNotReady
                }

            if (!claimEvent(command, now, ownerUserId = current.ownerUserId)) {
                return@inTransaction ProcessAsaasWebhookResult.Accepted
            }
            applyDomainEvent(command, current, now)
            events.markProcessed(command.asaasEventId, now)
            ProcessAsaasWebhookResult.Accepted
        }
    }

    private fun resolveSubscription(command: AsaasWebhookCommand): Subscription? {
        command.asaasSubscriptionId?.takeIf { it.isNotBlank() }?.let { subId ->
            subscriptions.findByAsaasSubscriptionId(subId)?.let { return it }
        }
        command.asaasPaymentId?.takeIf { it.isNotBlank() }?.let { payId ->
            subscriptions.findByPendingUpgradeChargeId(payId)?.let { return it }
            // Cobranca de upgrade ja aplicada limpa pendingUpgradeChargeId, entao o evento irmao
            // (RECEIVED depois do CONFIRMED) so resolve por aqui — sem isto viraria 503 eterno.
            subscriptions.findByLastConfirmedPaymentId(payId)?.let { return it }
        }
        return null
    }

    private fun claimAndAccept(command: AsaasWebhookCommand, now: Instant): ProcessAsaasWebhookResult {
        if (!claimEvent(command, now)) return ProcessAsaasWebhookResult.Accepted
        events.markProcessed(command.asaasEventId, now)
        return ProcessAsaasWebhookResult.Accepted
    }

    private fun claimEvent(
        command: AsaasWebhookCommand,
        now: Instant,
        ownerUserId: UUID? = null,
    ): Boolean =
        events.tryInsert(
            id = newEventId(),
            asaasEventId = command.asaasEventId,
            type = command.eventType,
            payload = maskCardNumbers(command.rawPayload),
            now = now,
            ownerUserId = ownerUserId,
        )

    private fun applyDomainEvent(command: AsaasWebhookCommand, current: Subscription, now: Instant) {
        when (command.eventType) {
            in CONFIRMING_EVENT_TYPES -> {
                if (current.status == SubscriptionStatus.CANCELED) return
                applyConfirmedPayment(command, current, now)
            }
            EVENT_PAYMENT_OVERDUE -> {
                if (current.status == SubscriptionStatus.CANCELED) return
                applyOverdue(command, current, now)
            }
            in REVOKING_EVENT_TYPES -> applyRefund(command, current, now)
            EVENT_SUBSCRIPTION_DELETED -> subscriptions.save(cancel(current, now))
        }
    }

    /**
     * Cancelamento comum preserva o ciclo ja pago (ver [cancel]); estorno nao — o pagamento que
     * sustentava o acesso deixou de existir, entao o periodo e cortado para agora e
     * `isEntitlingAt` passa a devolver false na mesma hora.
     *
     * So revoga se o estorno for da cobranca que confirmou o acesso atual: estorno de uma
     * cobranca antiga, ja superada por uma renovacao, nao pode derrubar o ciclo vigente.
     */
    private fun applyRefund(command: AsaasWebhookCommand, current: Subscription, now: Instant) {
        val paymentId = command.asaasPaymentId
        val sustainsCurrentAccess = current.lastConfirmedPaymentId == null ||
            (paymentId != null && paymentId == current.lastConfirmedPaymentId)
        if (!sustainsCurrentAccess) return
        subscriptions.save(revoke(current, now))
    }

    private fun applyConfirmedPayment(command: AsaasWebhookCommand, current: Subscription, now: Instant) {
        // PAYMENT_CONFIRMED e PAYMENT_RECEIVED descrevem a mesma cobranca paga e os dois chegam,
        // com asaasEventId distinto — o gate por evento nao colapsa o par. Quem chegar primeiro
        // aplica; o segundo vira no-op aqui, senao avancaria currentPeriodEnd um ciclo de graca.
        if (isAlreadyConfirmed(current, command.asaasPaymentId)) return
        if (isPendingUpgradePayment(current, command.asaasPaymentId)) {
            // canceledAt is set by CancelSubscription immediately; status only flips to
            // CANCELED once this same webhook processes SUBSCRIPTION_DELETED. A late
            // payment on an abandoned pending-upgrade charge (never canceled at Asaas, no
            // gateway call for that) must not be misapplied as an upgrade in that gap.
            if (current.canceledAt != null) return
            applyPendingUpgrade(current, command.asaasPaymentId)
            return
        }
        val confirmed = PaymentConfirmation.confirm(current, command.asaasPaymentId, now)
        subscriptions.save(confirmed.subscription)
        confirmed.fullPriceCentsToPush?.let { cents ->
            asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, cents)
        }
    }

    private fun applyOverdue(command: AsaasWebhookCommand, current: Subscription, now: Instant) {
        if (isPendingUpgradePayment(current, command.asaasPaymentId)) {
            // Same canceledAt gap as the confirming branch above: preserve the mapping so a
            // late payment on this charge can still resolve.
            if (current.canceledAt != null) return
            // Optional upgrade charge expired — clear pending only; base plan stays.
            clearPendingUpgrade(current)
            return
        }
        // Asaas doesn't guarantee delivery order — an overdue notice for an invoice due
        // before the already-confirmed period end is stale (superseded by a later
        // PAYMENT_CONFIRMED) and must not regress the subscription back to PAST_DUE.
        if (isStaleOverdue(command, current)) return
        subscriptions.save(markPastDue(current, now))
    }

    /**
     * Cobranca sem id no payload nao tem como ser deduplicada — aplica, que e exatamente o
     * comportamento que existia antes de PAYMENT_RECEIVED virar evento de dominio.
     */
    private fun isAlreadyConfirmed(current: Subscription, paymentId: String?): Boolean =
        PaymentConfirmation.isAlreadyConfirmed(current, paymentId)

    private fun isStaleOverdue(command: AsaasWebhookCommand, current: Subscription): Boolean {
        val dueDate = paymentDueDate(command.rawPayload) ?: return false
        return dueDate.isBefore(current.currentPeriodEnd.atZone(ZoneOffset.UTC).toLocalDate())
    }

    private fun paymentDueDate(rawPayload: String): LocalDate? {
        val text = runCatching { objectMapper.readTree(rawPayload) }
            .getOrNull()
            ?.path("payment")
            ?.path("dueDate")
            ?.asText(null)
            ?: return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    private fun isPendingUpgradePayment(current: Subscription, paymentId: String?): Boolean {
        val pendingCharge = current.pendingUpgradeChargeId ?: return false
        return paymentId != null && paymentId == pendingCharge
    }

    private fun applyPendingUpgrade(current: Subscription, paymentId: String?) {
        val target = current.pendingUpgradePlan ?: return
        val full = target.priceCents(current.cycle)
        val recurring = recurringPriceWithCoupon(full, current)
        asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, recurring)
        subscriptions.save(
            current.copy(
                plan = target,
                pendingUpgradePlan = null,
                pendingUpgradeChargeId = null,
                pendingPlan = null,
                pendingPlanEffectiveAt = null,
                // Sem isto o evento irmao (RECEIVED apos CONFIRMED) nao casaria mais com
                // pendingUpgradeChargeId, ja limpo acima, e seria cobrado como renovacao.
                lastConfirmedPaymentId = paymentId ?: current.lastConfirmedPaymentId,
            ),
        )
    }

    private fun clearPendingUpgrade(current: Subscription) {
        subscriptions.save(
            current.copy(
                pendingUpgradePlan = null,
                pendingUpgradeChargeId = null,
            ),
        )
    }

    private fun recurringPriceWithCoupon(fullPriceCents: Long, current: Subscription): Long {
        if (!SubscriptionPricing.hasActiveCouponDiscount(current.couponId, current.couponCyclesRemaining)) {
            return fullPriceCents
        }
        val couponId = current.couponId ?: return fullPriceCents
        val coupon = coupons?.findById(couponId) ?: return fullPriceCents
        return fullPriceCents - (fullPriceCents * coupon.discountPercent / 100)
    }

    private fun markPastDue(current: Subscription, now: Instant): Subscription =
        current.copy(
            status = SubscriptionStatus.PAST_DUE,
            pastDueSince = current.pastDueSince ?: now,
        )

    private fun revoke(current: Subscription, now: Instant): Subscription =
        current.copy(
            status = SubscriptionStatus.CANCELED,
            canceledAt = current.canceledAt ?: now,
            // Corta o ciclo: CANCELED so continua dando acesso enquanto currentPeriodEnd > now.
            currentPeriodEnd = if (current.currentPeriodEnd.isAfter(now)) now else current.currentPeriodEnd,
        )

    private fun cancel(current: Subscription, now: Instant): Subscription =
        current.copy(
            status = SubscriptionStatus.CANCELED,
            canceledAt = current.canceledAt ?: now,
        )

    companion object {
        const val EVENT_PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED"

        /**
         * Boleto/PIX liquidam com RECEIVED e nem sempre mandam CONFIRMED — tratar so CONFIRMED
         * deixaria assinatura paga presa em PAST_DUE. Os dois confirmam; [isAlreadyConfirmed]
         * garante que o par nao seja aplicado duas vezes.
         */
        const val EVENT_PAYMENT_RECEIVED = "PAYMENT_RECEIVED"
        const val EVENT_PAYMENT_OVERDUE = "PAYMENT_OVERDUE"
        const val EVENT_SUBSCRIPTION_DELETED = "SUBSCRIPTION_DELETED"

        /** Estorno concluido: o dinheiro voltou ao pagador. */
        const val EVENT_PAYMENT_REFUNDED = "PAYMENT_REFUNDED"

        /** Baixa manual desfeita — o recebimento deixou de existir. */
        const val EVENT_PAYMENT_RECEIVED_IN_CASH_UNDONE = "PAYMENT_RECEIVED_IN_CASH_UNDONE"
        const val WEBHOOK_TOKEN_HEADER = "asaas-access-token"

        /** Os dois eventos que comprovam pagamento — [ListReceipts] lista os dois. */
        val CONFIRMING_EVENT_TYPES = setOf(
            EVENT_PAYMENT_CONFIRMED,
            EVENT_PAYMENT_RECEIVED,
        )

        /**
         * Dinheiro devolvido revoga o acesso na hora — nao existe carencia para quem foi
         * estornado. Estorno PARCIAL (PAYMENT_PARTIALLY_REFUNDED) fica de fora de proposito:
         * o pagador continua tendo pago parte do ciclo.
         */
        private val REVOKING_EVENT_TYPES = setOf(
            EVENT_PAYMENT_REFUNDED,
            EVENT_PAYMENT_RECEIVED_IN_CASH_UNDONE,
        )

        private val DOMAIN_EVENT_TYPES = CONFIRMING_EVENT_TYPES + REVOKING_EVENT_TYPES + setOf(
            EVENT_PAYMENT_OVERDUE,
            EVENT_SUBSCRIPTION_DELETED,
        )

        fun advancePeriodEnd(currentPeriodEnd: Instant, cycle: SubscriptionCycle): Instant =
            PaymentConfirmation.advancePeriodEnd(currentPeriodEnd, cycle)

        fun Plan.priceCents(cycle: SubscriptionCycle): Long =
            when (cycle) {
                SubscriptionCycle.MONTHLY -> monthlyPriceCents
                SubscriptionCycle.ANNUAL -> annualPriceCents
            }

        fun tokenMatches(provided: String?, expected: String): Boolean {
            if (provided == null || expected.isEmpty()) return false
            val left = provided.toByteArray(Charsets.UTF_8)
            val right = expected.toByteArray(Charsets.UTF_8)
            if (left.size != right.size) return false
            return MessageDigest.isEqual(left, right)
        }

        /**
         * A Asaas nunca deveria mandar o PAN completo no webhook — o objeto `creditCard` só traz
         * os 4 últimos dígitos + token —, mas este payload fica anos em `subscription_events` para
         * auditoria. Mascara defensivamente qualquer `number`/`creditCardNumber` de 13-19 dígitos
         * antes de persistir, caso isso mude do lado da Asaas (PCI é inegociável — VUL-194).
         */
        private val PAN_FIELD_PATTERN = Regex(""""(number|creditCardNumber)"\s*:\s*"(\d{13,19})"""")

        fun maskCardNumbers(rawPayload: String): String =
            PAN_FIELD_PATTERN.replace(rawPayload) { match ->
                val field = match.groupValues[1]
                val digits = match.groupValues[2]
                "\"$field\":\"${"*".repeat(digits.length - 4)}${digits.takeLast(4)}\""
            }
    }
}
