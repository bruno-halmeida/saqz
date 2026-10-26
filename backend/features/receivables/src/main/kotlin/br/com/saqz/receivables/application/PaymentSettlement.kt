package br.com.saqz.receivables.application

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
    private val commissions: CommissionTransfers, private val clock: Clock, private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")) {

    /** Nulo: o pagamento não é do v1 (segue o caminho de setembro). Verdadeiro: processado. Falso: tentar de novo. */
    fun handle(accountId: UUID, eventType: String, paymentId: String, reference: String?, qrId: String?, billingPix: Boolean): Boolean? {
        val code = store.transaction { store.codeByReference(accountId, reference, qrId) }
        if (code == null && !billingPix) return null
        if (eventType !in SETTLE && eventType !in REVERSE) return true
        val observed = try { facts.payment(accountId, paymentId) } catch (_: Exception) { return false }
        if (observed.id != paymentId) return false
        if (eventType in REVERSE || observed.status in REVERSED) {
            store.transaction {
                val record = store.settlement(accountId, paymentId) ?: return@transaction
                if (store.markReversed(accountId, paymentId, clock.instant())) {
                    // Setembro: reversão marca a cobrança para revisão do gestor, sem reabrir a dívida.
                    ledger.requireReview(record.settledChargeIds, ownerOf(record) ?: return@transaction, clock.instant())
                }
            }
            return true
        }
        if (observed.deleted) return true
        // O aviso chegou antes de a consulta refletir o pagamento: tenta de novo no próximo ciclo.
        if (observed.status !in PAID) return false
        val operation = store.transaction { settle(accountId, observed) }
        operation?.let { runCatching { commissions.run(accountId, it) } }
        return true
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
        val own = ledger.lock(code.chargeIds)
        val chosen = if (own.size == code.chargeIds.size && own.all { it.status == "PENDING" } && payment.valueCents == code.amountCents) own
        else {
            // FIN-014: a cobrança do código já foi resolvida (o "Recebi" chegou antes). O valor quita as mais antigas em
            // aberto do mesmo atleta, enquanto cobrir cobranças inteiras; o que sobra vai para a revisão.
            val open = ledger.lock(ledger.openCharges(code.groupId, code.payerId).map { it.id })
                .sortedWith(compareBy<LedgerCharge>({ it.dueDate }, { it.createdAt }, { it.id.toString() }))
            var left = payment.valueCents
            open.takeWhile { charge -> (charge.payableCents <= left).also { if (it) left -= charge.payableCents } }
        }
        val settled = chosen.filter { ledger.settleByApp(it.id, method.name, code.payerId, now) }
        val leftover = payment.valueCents - settled.sumOf { it.payableCents }
        codes.changeStatus(code.id, setOf("CREATING", "PENDING", "EXPIRED", "CANCELLED"), "PAID", now)
        store.expireCodesCovering(settled.map { it.id }, code.id, now)
        val payerName = ledger.displayName(code.payerId) ?: "Atleta"
        if (leftover > 0) review(accountId, payment, payerName, leftover, 0, now)
        // E: comissão do Pix por transferência logo depois da baixa (no cartão ela saiu na divisão). Zero não transfere.
        val commission = if (payment.valueCents == code.amountCents) code.commissionCents else 0
        val operation = if (method == PaymentMethod.PIX && commission > 0)
            UUID.nameUUIDFromBytes("commission:$accountId:${payment.id}".toByteArray()).also {
                store.registerCommissionTransfer(accountId, it, code.payerId, now)
            } else null
        store.save(SettlementRecord(accountId, payment.id, code.id, code.groupId, method, payment.valueCents, payment.netCents,
            commission, settled.map { it.id }, leftover, operation, now))
        cashFees(code.groupId, payment, settled, leftover, commission, code.payerId, now)
        notify(code.groupId, code.payerId, payerName, settled, now, payment.id)
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
            UUID.nameUUIDFromBytes("review-push:${payment.id}".toByteArray()))
    }

    private fun notify(groupId: UUID, payerId: UUID, payerName: String, settled: List<LedgerCharge>, now: Instant, paymentId: String) {
        if (settled.isEmpty()) return
        val group = ledger.group(groupId) ?: return
        val total = brl(settled.sumOf { it.payableCents })
        val what = describe(settled)
        notices.publish(groupId, ledger.managers(groupId) - payerId, "$payerName pagou $total", "$what · ${group.name}",
            UUID.nameUUIDFromBytes("paid-manager:$paymentId".toByteArray()))
        notices.publish(groupId, setOf(payerId), "Pagamento confirmado",
            "$total · ${what.replaceFirstChar { it.lowercase() }}, ${group.name}.", UUID.nameUUIDFromBytes("paid-athlete:$paymentId".toByteArray()))
    }

    private fun ownerOf(record: SettlementRecord): UUID? = record.groupId?.let { ledger.group(it)?.ownerUserId }

    companion object {
        const val FEES_LABEL = "Taxas de serviço e pagamento"
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
