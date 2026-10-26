package br.com.saqz.receivables.application

import br.com.saqz.sharedkernel.group.PaymentNoticeKind
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.group.GroupChargeLedger
import br.com.saqz.sharedkernel.group.LedgerCharge
import br.com.saqz.sharedkernel.group.ReceivablesNotices
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class SettlementRecord(val accountId: UUID, val paymentId: String, val codeId: UUID?, val groupId: UUID?,
    val method: PaymentMethod?, val valueCents: Long, val netCents: Long?, val commissionCents: Long,
    val settledChargeIds: List<UUID>, val leftoverCents: Long, val commissionOperationId: UUID?, val at: Instant)

data class NewReview(val id: UUID, val accountId: UUID, val paymentId: String, val payerName: String, val amountCents: Long,
    val feesCents: Long, val receivedAt: Instant)

/** Executa a transferência da comissão registrada na baixa; também é o passo do job de recuperação. */
fun interface CommissionTransfers {
    fun run(accountId: UUID, operationId: UUID)
}

interface SettlementStore {
    fun <T> transaction(block: () -> T): T
    /** Falso quando o pagamento já foi processado (aviso repetido). Trava a linha até o fim da transação. */
    fun claim(accountId: UUID, paymentId: String, at: Instant): Boolean
    fun save(record: SettlementRecord)
    fun settlement(accountId: UUID, paymentId: String): SettlementRecord?
    fun markReversed(accountId: UUID, paymentId: String, at: Instant): Boolean
    fun codeByReference(accountId: UUID, reference: String?, qrId: String?): UUID?
    /** Outros códigos vivos que cobriam essas cobranças deixam de valer (FIN-011). */
    fun expireCodesCovering(chargeIds: Collection<UUID>, except: UUID?, at: Instant)
    fun insertReview(review: NewReview)
    /** Grupos ativos da conta, o mais antigo primeiro. */
    fun accountGroups(accountId: UUID): List<UUID>
    /** Transferência durável da comissão do Pix (cerca de operação de setembro). */
    fun registerCommissionTransfer(accountId: UUID, operationId: UUID, actorId: UUID, at: Instant)
}

/** Baixa automática pelo aviso do Asaas (FIN-014). Só fatos consultados no Asaas; o corpo do aviso só acorda. */
class PaymentSettlement(private val store: SettlementStore, private val codes: PaymentCodeStore,
    private val ledger: GroupChargeLedger, private val notices: ReceivablesNotices, private val facts: PaymentFactsProvider,
    private val commissions: CommissionTransfers, private val clock: Clock, private val zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
    private val subscriptions: SubscriptionStore? = null, private val cards: CardSubscriptions? = null) {

    /** Nulo: o pagamento não é do v1 (segue o caminho de setembro). Verdadeiro: processado. Falso: tentar de novo. */
    fun handle(accountId: UUID, eventType: String, paymentId: String, reference: String?, qrId: String?, billingPix: Boolean,
               subscriptionId: String? = null, checkoutSession: String? = null): Boolean? {
        val code = store.transaction { store.codeByReference(accountId, reference, qrId) }
        if (code == null && (subscriptionId != null || checkoutSession != null)) {
            val subscription = subscriptions?.let { s -> s.transaction { s.byProvider(accountId, subscriptionId, checkoutSession, reference) } }
            if (subscription != null) return subscriptionEvent(subscription, eventType, paymentId)
        }
        if (code == null && !billingPix) return null
        if (eventType !in SETTLE && eventType !in REVERSE) return true
        val observed = try { facts.payment(accountId, paymentId) } catch (_: Exception) { return false }
        if (observed.id != paymentId) return false
        if (eventType in REVERSE || observed.status in REVERSED) { reverse(accountId, paymentId); return true }
        if (observed.deleted) return true
        // O aviso chegou antes de a consulta refletir o pagamento: tenta de novo no próximo ciclo.
        if (observed.status !in PAID) return false
        val operation = store.transaction { settle(accountId, observed) }
        operation?.let { runCatching { commissions.run(accountId, it) } }
        return true
    }

    /**
     * Cobrança da mensalidade no cartão (FIN-012): baixa a mensalidade do mês do vencimento. Se ela ainda não existe (a
     * geração mensal ainda não rodou), tenta de novo no próximo ciclo; depois de [SUBSCRIPTION_MATCH_DAYS] dias, revisão.
     */
    private fun subscriptionEvent(subscription: CardSubscription, eventType: String, paymentId: String): Boolean {
        val accountId = subscription.accountId
        val observed = try { facts.payment(accountId, paymentId) } catch (_: Exception) { return false }
        if (observed.id != paymentId) return false
        val due = observed.dueDate ?: return false
        val store = subscriptions!!
        val firstSeen = store.transaction { store.recordPayment(accountId, paymentId, subscription.id, due, observed.valueCents, clock.instant()) }
        if (subscription.status == "AUTHORIZING" && observed.subscriptionId != null) cards?.activated(subscription, observed.subscriptionId)
        if (eventType == "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED") { cards?.refused(subscription, observed); return true }
        if (eventType in REVERSE || observed.status in REVERSED) { reverse(accountId, paymentId); return true }
        if (eventType !in SETTLE) return true
        if (observed.deleted) return true
        if (observed.status !in PAID) return false
        val month = due.withDayOfMonth(1)
        val ready = this.store.transaction { ledger.monthlyCharge(subscription.groupId, subscription.memberId, month) != null }
        if (!ready && firstSeen.plusSeconds(SUBSCRIPTION_MATCH_DAYS * 86400) > clock.instant()) return false
        this.store.transaction {
            val now = clock.instant()
            if (!this.store.claim(accountId, observed.id, now)) return@transaction
            val target = ledger.monthlyCharge(subscription.groupId, subscription.memberId, month)
            if (target == null) {
                val payer = ledger.displayName(subscription.memberId) ?: "Atleta"
                review(accountId, observed, payer, observed.valueCents, observed.netCents?.let { observed.valueCents - it }?.coerceAtLeast(0) ?: 0, now)
                this.store.save(SettlementRecord(accountId, observed.id, null, subscription.groupId, PaymentMethod.CARD, observed.valueCents,
                    observed.netCents, 0, emptyList(), observed.valueCents, null, now))
                return@transaction
            }
            val commission = if (observed.valueCents == subscription.amountCents) subscription.commissionCents else 0
            settleCharges(accountId, observed, subscription.groupId, subscription.memberId, ledger.lock(listOf(target.id)),
                subscription.amountCents, commission, PaymentMethod.CARD, null, now)
            store.clearRefused(listOf(target.id))
        }
        return true
    }

    private fun reverse(accountId: UUID, paymentId: String) {
        store.transaction {
            val record = store.settlement(accountId, paymentId) ?: return@transaction
            if (store.markReversed(accountId, paymentId, clock.instant())) {
                // Setembro: reversão marca a cobrança para revisão do gestor, sem reabrir a dívida.
                ledger.requireReview(record.settledChargeIds, ownerOf(record) ?: return@transaction, clock.instant())
            }
        }
    }

    private fun settle(accountId: UUID, payment: ObservedPayment): UUID? {
        val now = clock.instant()
        if (!store.claim(accountId, payment.id, now)) return null
        val codeId = store.codeByReference(accountId, payment.externalReference, payment.pixQrCodeId)
        val code = codeId?.let { codes.lock(it) }
        if (code == null) {
            if (payment.method != PaymentMethod.PIX) {
                store.save(SettlementRecord(accountId, payment.id, null, null, payment.method, payment.valueCents, payment.netCents, 0, emptyList(), 0, null, now))
                return null
            }
            val fees = payment.netCents?.let { payment.valueCents - it }?.coerceAtLeast(0) ?: 0
            review(accountId, payment, payment.payerName ?: "Pagador não identificado", payment.valueCents, fees, now)
            store.save(SettlementRecord(accountId, payment.id, null, null, payment.method, payment.valueCents, payment.netCents, 0, emptyList(), payment.valueCents, null, now))
            return null
        }
        val method = payment.method ?: code.method
        val commission = if (payment.valueCents == code.amountCents) code.commissionCents else 0
        return settleCharges(accountId, payment, code.groupId, code.payerId, ledger.lock(code.chargeIds), code.amountCents, commission,
            method, code, now)
    }

    /**
     * Baixa das cobranças esperadas ([own]) se todas ainda estão em aberto e o valor bate; senão (FIN-014) as mais antigas
     * em aberto do mesmo atleta, enquanto cobrirem cobranças inteiras, e a sobra vai para a revisão. Devolve a operação
     * da comissão do Pix, se houver.
     */
    private fun settleCharges(accountId: UUID, payment: ObservedPayment, groupId: UUID, payerId: UUID, own: List<LedgerCharge>,
                              expectedCents: Long, commission: Long, method: PaymentMethod, code: StoredPaymentCode?, now: Instant): UUID? {
        val chosen = if (own.isNotEmpty() && own.all { it.status == "PENDING" } && payment.valueCents == expectedCents) own
        else {
            // FIN-014: a cobrança do código já foi resolvida (o "Recebi" chegou antes). O valor quita as mais antigas em
            // aberto do mesmo atleta, enquanto cobrir cobranças inteiras; o que sobra vai para a revisão.
            val open = ledger.lock(ledger.openCharges(groupId, payerId).map { it.id })
                .sortedWith(compareBy<LedgerCharge>({ it.dueDate }, { it.createdAt }, { it.id.toString() }))
            var left = payment.valueCents
            open.takeWhile { charge -> (charge.payableCents <= left).also { if (it) left -= charge.payableCents } }
        }
        val settled = chosen.filter { ledger.settleByApp(it.id, method.name, payerId, now) }
        val leftover = payment.valueCents - settled.sumOf { it.payableCents }
        code?.let { codes.changeStatus(it.id, setOf("CREATING", "PENDING", "EXPIRED", "CANCELLED"), "PAID", now) }
        store.expireCodesCovering(settled.map { it.id }, code?.id, now)
        val payerName = ledger.displayName(payerId) ?: "Atleta"
        if (leftover > 0) review(accountId, payment, payerName, leftover, 0, now)
        // E: comissão do Pix por transferência logo depois da baixa (no cartão ela saiu na divisão). Zero não transfere.
        val operation = if (method == PaymentMethod.PIX && commission > 0)
            UUID.nameUUIDFromBytes("commission:$accountId:${payment.id}".toByteArray()).also {
                store.registerCommissionTransfer(accountId, it, payerId, now)
            } else null
        store.save(SettlementRecord(accountId, payment.id, code?.id, groupId, method, payment.valueCents, payment.netCents,
            commission, settled.map { it.id }, leftover, operation, now))
        cashFees(groupId, payment, settled, leftover, commission, payerId, now)
        notify(groupId, payerId, payerName, settled, now, payment.id)
        return operation
    }

    /**
     * Caixa (FIN-014): a entrada é o que o atleta pagou (a cobrança paga pelo app entra pelo payable, e a sobra entra quando a
     * revisão for resolvida); as taxas daquele pagamento saem como "Taxas de serviço e pagamento" = pago − (líquido −
     * comissão), para o saldo bater com a carteira. Nunca negativa.
     */
    private fun cashFees(groupId: UUID, payment: ObservedPayment, settled: List<LedgerCharge>, leftover: Long, commission: Long,
                         actor: UUID, now: Instant) {
        val net = payment.netCents ?: return
        val fees = settled.sumOf { it.payableCents } + leftover - (net - commission)
        if (fees <= 0) return
        ledger.record(UUID.nameUUIDFromBytes("fees:${payment.id}".toByteArray()), groupId, "OUT", "RECEIVABLES_FEES", FEES_LABEL,
            fees, LocalDate.ofInstant(now, zone), actor, now)
    }

    private fun review(accountId: UUID, payment: ObservedPayment, payerName: String, amount: Long, fees: Long, now: Instant) {
        val groups = store.accountGroups(accountId)
        store.insertReview(NewReview(UUID.randomUUID(), accountId, payment.id, payerName.take(160), amount, fees, now))
        val group = groups.firstOrNull() ?: return
        val managers = groups.flatMap { ledger.managers(it) }.toSet()
        notices.publish(group, managers, "Pix para revisar", "${brl(amount)} de $payerName caiu fora de uma cobrança. Quem é?",
            UUID.nameUUIDFromBytes("review-push:${payment.id}".toByteArray()), kind = PaymentNoticeKind.REVIEW)
    }

    private fun notify(groupId: UUID, payerId: UUID, payerName: String, settled: List<LedgerCharge>, now: Instant, paymentId: String) {
        if (settled.isEmpty()) return
        val group = ledger.group(groupId) ?: return
        val total = brl(settled.sumOf { it.payableCents })
        val what = describe(settled)
        notices.publish(groupId, ledger.managers(groupId) - payerId, "$payerName pagou $total", "$what · ${group.name}",
            UUID.nameUUIDFromBytes("paid-manager:$paymentId".toByteArray()), kind = PaymentNoticeKind.PAID)
        notices.publish(groupId, setOf(payerId), "Pagamento confirmado",
            "$total · ${what.replaceFirstChar { it.lowercase() }}, ${group.name}.", UUID.nameUUIDFromBytes("paid-athlete:$paymentId".toByteArray()),
            kind = PaymentNoticeKind.CONFIRMED)
    }

    private fun ownerOf(record: SettlementRecord): UUID? = record.groupId?.let { ledger.group(it)?.ownerUserId }

    companion object {
        const val FEES_LABEL = "Taxas de serviço e pagamento"
        private const val SUBSCRIPTION_MATCH_DAYS = 3L
        private val SETTLE = setOf("PAYMENT_RECEIVED", "PAYMENT_CONFIRMED")
        private val REVERSE = setOf("PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE",
            "PAYMENT_AWAITING_CHARGEBACK_REVERSAL")
        private val PAID = setOf("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH")
        private val REVERSED = setOf("REFUNDED", "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "AWAITING_CHARGEBACK_REVERSAL")

        /** "Jogo de terça", "Mensalidade de agosto" ou "3 cobranças", nos textos aprovados (Avisos.dc.html). */
        fun describe(charges: List<LedgerCharge>): String {
            val games = charges.mapNotNull { it.gameId }.toSet()
            if (charges.all { it.kind == "GAME" } && games.size == 1) {
                val date = charges.first().gameDate ?: charges.first().dueDate
                return "Jogo de ${weekday(date.dayOfWeek)}"
            }
            if (charges.size == 1 && charges.single().kind == "MONTHLY") {
                return "Mensalidade de ${month(charges.single().billingMonth ?: charges.single().dueDate)}"
            }
            return "${charges.size} cobranças"
        }

        fun weekday(day: DayOfWeek) = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")[day.value - 1]
        fun month(date: LocalDate) = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto",
            "setembro", "outubro", "novembro", "dezembro")[date.monthValue - 1]
    }
}
