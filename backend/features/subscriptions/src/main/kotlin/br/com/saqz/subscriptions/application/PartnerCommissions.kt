package br.com.saqz.subscriptions.application

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

enum class CommissionDuration { FIRST_PAYMENT, MONTHS, LIFETIME }

/** Regra de comissão de um parceiro: [percent] de 0,01 a 100 (duas casas); [months] só em MONTHS (1 a 60). */
data class CommissionRule(val percent: BigDecimal, val duration: CommissionDuration, val months: Int? = null) {
    init {
        require(percent.scale() <= 2 && percent >= BigDecimal("0.01") && percent <= BigDecimal(100)) { "percent fora de 0,01..100" }
        require((duration == CommissionDuration.MONTHS) == (months != null)) { "months só com MONTHS" }
        require(months == null || months in 1..60) { "months fora de 1..60" }
    }
}

enum class CommissionLineKind { PAYMENT, REFUND }

/** Pagamento de um cliente do parceiro (de `subscription_payments`, já sem teste e a partir do primeiro uso). */
data class CommissionPayment(
    val source: String,
    val ref: String,
    val ownerUserId: UUID,
    val paidAt: Instant,
    /** Nulo = a loja ainda não informou o valor (ou moeda que não é real). */
    val grossCents: Long?,
    val refunded: Boolean,
)

data class CommissionLine(
    val kind: CommissionLineKind,
    val source: String,
    val ref: String,
    val ownerUserId: UUID,
    val paidAt: Instant,
    val grossCents: Long,
    /** Negativo em REFUND. */
    val commissionCents: Long,
)

/** Linha já gravada num extrato fechado. */
data class ClosedCommissionLine(val kind: CommissionLineKind, val source: String, val ref: String, val commissionCents: Long)

/**
 * Comissão do parceiro mês a mês (fuso de São Paulo). Regras do dono (21/10/2026):
 *
 * - Base: bruto pago pelo cliente; pagamento estornado antes do fechamento não entra.
 * - Duração contada do primeiro pagamento real do cliente (com ou sem valor informado, estornado ou não).
 * - Arredondamento: por pagamento, `bruto × percentual / 100` em centavos, metade para cima (HALF_UP);
 *   o total do mês é a soma das linhas já arredondadas.
 * - Pagamento cujo mês já fechou (valor que a loja informou depois, ou que ficou de fora) entra no mês em
 *   aberto atual; o mesmo vale para o desconto de um pagamento fechado e estornado depois.
 * - Meses fechados não são recalculados: a regra atual vale só para o que ainda não foi fechado.
 */
object PartnerCommissionCalculator {
    val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

    fun monthOf(at: Instant): YearMonth = YearMonth.from(at.atZone(ZONE))

    fun monthStart(month: YearMonth): Instant = month.atDay(1).atStartOfDay(ZONE).toInstant()

    fun commissionCents(grossCents: Long, percent: BigDecimal): Long =
        BigDecimal(grossCents).multiply(percent).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).longValueExact()

    /** Linhas ainda não fechadas, por mês em que entram. */
    fun open(
        rule: CommissionRule?,
        payments: List<CommissionPayment>,
        closedLines: List<ClosedCommissionLine>,
        closedMonths: Set<YearMonth>,
        now: Instant,
    ): Map<YearMonth, List<CommissionLine>> {
        val current = monthOf(now)
        val closed = closedLines.associateBy { Triple(it.kind, it.source, it.ref) }
        val firstPayment = payments.groupBy { it.ownerUserId }.mapValues { (_, list) -> list.minOf { it.paidAt } }
        val firstRef = payments.groupBy { it.ownerUserId }.mapValues { (_, list) -> list.minWith(compareBy({ it.paidAt }, { it.ref })).ref }
        val lines = mutableListOf<Pair<YearMonth, CommissionLine>>()
        for (payment in payments) {
            val paid = closed[Triple(CommissionLineKind.PAYMENT, payment.source, payment.ref)]
            if (paid != null) {
                if (payment.refunded && closed[Triple(CommissionLineKind.REFUND, payment.source, payment.ref)] == null) {
                    lines += current to CommissionLine(
                        CommissionLineKind.REFUND, payment.source, payment.ref, payment.ownerUserId, payment.paidAt,
                        -(payment.grossCents ?: 0), -paid.commissionCents,
                    )
                }
                continue
            }
            val gross = payment.grossCents ?: continue
            if (rule == null || payment.refunded || gross <= 0) continue
            val anchor = requireNotNull(firstPayment[payment.ownerUserId])
            val inDuration = when (rule.duration) {
                CommissionDuration.FIRST_PAYMENT -> payment.ref == firstRef[payment.ownerUserId]
                CommissionDuration.MONTHS -> payment.paidAt.isBefore(anchor.atZone(ZONE).plusMonths(requireNotNull(rule.months).toLong()).toInstant())
                CommissionDuration.LIFETIME -> true
            }
            if (!inDuration) continue
            val month = monthOf(payment.paidAt).takeUnless { it in closedMonths } ?: current
            lines += month to CommissionLine(
                CommissionLineKind.PAYMENT, payment.source, payment.ref, payment.ownerUserId, payment.paidAt, gross,
                commissionCents(gross, rule.percent),
            )
        }
        return lines.groupBy({ it.first }, { it.second }).toSortedMap()
    }

    /** Só fecha mês que já terminou e que ainda não foi fechado. */
    fun canClose(month: YearMonth, closedMonths: Set<YearMonth>, now: Instant): Boolean = month.isBefore(monthOf(now)) && month !in closedMonths
}

data class CommissionStatementLine(
    val line: CommissionLine,
    /** Dado pessoal, só no admin. */
    val customerName: String?,
)

/** Um mês da comissão: fechado (extrato gravado) ou em aberto (calculado agora com a regra atual). */
data class CommissionMonth(
    val month: YearMonth,
    val closed: Boolean,
    val rule: CommissionRule?,
    val totalCents: Long,
    val lines: List<CommissionStatementLine>,
    val closedAt: Instant? = null,
    val paidOn: LocalDate? = null,
    val paidNote: String? = null,
    val canClose: Boolean = false,
)

data class PartnerCommissionTotals(
    /** Soma dos meses em aberto (ainda pode mudar). */
    val openCents: Long,
    /** Soma dos extratos fechados e ainda não marcados como pagos. */
    val closedUnpaidCents: Long,
)

sealed interface CloseCommissionResult {
    data class Closed(val month: CommissionMonth) : CloseCommissionResult
    data object NotEnded : CloseCommissionResult
    data object AlreadyClosed : CloseCommissionResult
    data object NotFound : CloseCommissionResult
}

interface PartnerCommissionStore {
    fun rule(partnerId: UUID): CommissionRule?

    /** false quando o parceiro não existe. */
    fun setRule(partnerId: UUID, rule: CommissionRule?): Boolean

    /** Meses fechados e os abertos com linhas, do mais novo ao mais antigo. Null = parceiro não existe. */
    fun months(partnerId: UUID, now: Instant): List<CommissionMonth>?

    fun totals(partnerId: UUID, now: Instant): PartnerCommissionTotals

    fun close(partnerId: UUID, month: YearMonth, now: Instant): CloseCommissionResult

    /** false quando o mês não está fechado. */
    fun markPaid(partnerId: UUID, month: YearMonth, paidOn: LocalDate, note: String?): Boolean
}
