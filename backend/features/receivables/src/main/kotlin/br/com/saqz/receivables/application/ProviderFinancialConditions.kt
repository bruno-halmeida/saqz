package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ProviderPaymentFee(
    val rate: BigDecimal,
    val fixedCents: Long,
    val minimumCents: Long = 0,
    val maximumCents: Long? = null,
) {
    init {
        require(rate >= BigDecimal.ZERO && rate < BigDecimal.ONE)
        require(fixedCents >= 0 && minimumCents >= 0)
        require(maximumCents == null || maximumCents >= minimumCents)
    }

    fun applyTo(schedule: FeeSchedule) = schedule.copy(providerRate = rate, providerFixedCents = fixedCents,
        providerMinimumCents = minimumCents, providerMaximumCents = maximumCents)
}

/** Null account selects the platform's informational preview. Issuance always supplies the receiving account. */
fun interface FinancialFeeProvider {
    fun current(methods: Set<PaymentMethod>, at: Instant, accountId: UUID?): Map<PaymentMethod, ProviderPaymentFee>
}

class FinancialFeesUnavailable : RuntimeException("Asaas fees unavailable")

/** Published rows version the Saqz commission. Legacy provider columns are never used for new quotes. */
class ProviderFinancialConditions(
    private val published: FinancialConditions,
    private val provider: FinancialFeeProvider,
) : FinancialConditions by published {
    override fun current(methods: Set<PaymentMethod>, at: Instant, accountId: UUID?): List<FeeSchedule> {
        val schedules = published.current(methods, at, accountId)
        if (schedules.size != methods.size || schedules.map { it.method }.toSet() != methods) return emptyList()
        val fees = try { provider.current(methods, at, accountId) } catch (_: FinancialFeesUnavailable) { return emptyList() }
        if (!fees.keys.containsAll(methods)) return emptyList()
        return schedules.map { fees.getValue(it.method).applyTo(it) }
    }
}
