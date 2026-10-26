package br.com.saqz.receivables.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

enum class PaymentMethod { PIX, CARD }

/** Rates are fractions (0.01 = 1%); no commercial values are supplied by code. */
data class FeeSchedule(
    val id: UUID,
    val method: PaymentMethod,
    val providerRate: BigDecimal,
    val providerFixedCents: Long,
    val commissionRate: BigDecimal,
    val commissionFixedCents: Long,
    val termsVersion: String,
    val providerMinimumCents: Long = 0,
    val providerMaximumCents: Long? = null,
) {
    init {
        require(providerRate >= BigDecimal.ZERO && providerRate < BigDecimal.ONE)
        require(commissionRate >= BigDecimal.ZERO && commissionRate <= BigDecimal.ONE)
        require(providerFixedCents >= 0 && commissionFixedCents >= 0)
        require(providerMinimumCents >= 0)
        require(providerMaximumCents == null || providerMaximumCents >= providerMinimumCents)
        require(termsVersion.isNotBlank())
    }
}

data class FeeQuote(
    val feeScheduleId: UUID,
    val termsVersion: String,
    val method: PaymentMethod,
    val baseCents: Long,
    val feesCents: Long,
    val totalCents: Long,
    val expectedNetCents: Long,
    val commissionCents: Long,
    val providerFeeCents: Long,
) {
    /** Asaas fixedValue, never percentualValue: platform commission is on the base. */
    fun fixedSplitValue(): BigDecimal = BigDecimal.valueOf(commissionCents, 2)
}

object FeeCalculator {
    fun quote(baseCents: Long, schedule: FeeSchedule): FeeQuote {
        require(baseCents > 0)
        val commission = Math.addExact(rounded(baseCents, schedule.commissionRate), schedule.commissionFixedCents)
        val subtotal = BigDecimal.valueOf(Math.addExact(baseCents, commission))
        var lower = subtotal.longValueExact()
        val analyticalUpper = (subtotal + BigDecimal.valueOf(schedule.providerFixedCents))
            .divide(BigDecimal.ONE - schedule.providerRate, 0, RoundingMode.CEILING)
            .max(subtotal + BigDecimal.valueOf(schedule.providerMinimumCents))
        var upper = (schedule.providerMaximumCents?.let { analyticalUpper.min(subtotal + BigDecimal.valueOf(it)) }
            ?: analyticalUpper).longValueExact()
        // The analytical ceiling may include an unnecessary cent once the provider rounds its fee.
        // Net is monotone for a provider rate below 1, so find the smallest sufficient gross amount.
        while (lower < upper) {
            val candidate = lower + (upper - lower) / 2
            val fee = providerFee(candidate, schedule)
            if (candidate - fee - commission >= baseCents) upper = candidate else lower = candidate + 1
        }
        val providerFee = providerFee(lower, schedule)
        return FeeQuote(schedule.id, schedule.termsVersion, schedule.method, baseCents, lower - baseCents,
            lower, lower - providerFee - commission, commission, providerFee)
    }

    /** Comissão do Saqz sobre o valor pago (FIN-010); zero é permitido. */
    fun commission(paidCents: Long, schedule: FeeSchedule): Long =
        Math.addExact(rounded(paidCents, schedule.commissionRate), schedule.commissionFixedCents)

    /** O que fica para o gestor quando o atleta paga [paidCents]: tarifa do provedor e comissão saem do valor pago. */
    fun net(paidCents: Long, schedule: FeeSchedule): Long {
        require(paidCents > 0)
        return paidCents - providerFee(paidCents, schedule) - commission(paidCents, schedule)
    }

    /**
     * Repassar a taxa (FIN-010): o menor valor pago que deixa [baseCents] líquido para o gestor. O líquido cresce com o
     * valor pago (taxas abaixo de 100%), então a busca dobra até cobrir e depois afina por bisseção.
     */
    fun passThrough(baseCents: Long, schedule: FeeSchedule): Long {
        require(baseCents > 0)
        var lower = baseCents
        var upper = baseCents
        while (net(upper, schedule) < baseCents) {
            lower = upper + 1
            upper = Math.addExact(Math.multiplyExact(upper, 2L), 1L)
            if (upper > PASS_LIMIT_CENTS) throw ArithmeticException("Taxa impede o repasse")
        }
        while (lower < upper) {
            val candidate = lower + (upper - lower) / 2
            if (net(candidate, schedule) >= baseCents) upper = candidate else lower = candidate + 1
        }
        return lower
    }

    private const val PASS_LIMIT_CENTS = 999_999_999L

    private fun providerFee(cents: Long, schedule: FeeSchedule): Long {
        val fee = (BigDecimal.valueOf(cents).multiply(schedule.providerRate).setScale(0, RoundingMode.HALF_UP) +
            BigDecimal.valueOf(schedule.providerFixedCents)).max(BigDecimal.valueOf(schedule.providerMinimumCents))
        return (schedule.providerMaximumCents?.let { fee.min(BigDecimal.valueOf(it)) } ?: fee).longValueExact()
    }

    private fun rounded(cents: Long, rate: BigDecimal): Long =
        BigDecimal.valueOf(cents).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact()
}
