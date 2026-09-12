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
) {
    init {
        require(providerRate >= BigDecimal.ZERO && providerRate < BigDecimal.ONE)
        require(commissionRate >= BigDecimal.ZERO && commissionRate <= BigDecimal.ONE)
        require(providerFixedCents >= 0 && commissionFixedCents >= 0)
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
        val minimumBeforePercentage = Math.addExact(Math.addExact(baseCents, commission), schedule.providerFixedCents)
        var lower = minimumBeforePercentage
        var upper = BigDecimal.valueOf(minimumBeforePercentage)
            .divide(BigDecimal.ONE - schedule.providerRate, 0, RoundingMode.CEILING).longValueExact()
        // The analytical ceiling may include an unnecessary cent once the provider rounds its fee.
        // Net is monotone for a provider rate below 1, so find the smallest sufficient gross amount.
        while (lower < upper) {
            val candidate = lower + (upper - lower) / 2
            val fee = Math.addExact(rounded(candidate, schedule.providerRate), schedule.providerFixedCents)
            if (candidate - fee - commission >= baseCents) upper = candidate else lower = candidate + 1
        }
        val providerFee = Math.addExact(rounded(lower, schedule.providerRate), schedule.providerFixedCents)
        return FeeQuote(schedule.id, schedule.termsVersion, schedule.method, baseCents, lower - baseCents,
            lower, lower - providerFee - commission, commission, providerFee)
    }

    private fun rounded(cents: Long, rate: BigDecimal): Long =
        BigDecimal.valueOf(cents).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact()
}
