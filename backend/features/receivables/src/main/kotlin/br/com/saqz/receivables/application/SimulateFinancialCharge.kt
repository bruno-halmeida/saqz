package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.FeeQuote
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import java.time.Instant
import java.util.UUID

data class FinancialTerms(val version: String, val content: String, val contentSha256: String,
                          val effectiveAt: Instant, val publishedAt: Instant)

interface FinancialConditions {
    /** One database snapshot for all methods, including applicable published terms. */
    fun current(methods: Set<PaymentMethod>, at: Instant): List<FeeSchedule>
    fun terms(version: String, at: Instant): FinancialTerms?
    fun currentTerms(at: Instant): FinancialTerms?
}

data class ChargeSimulation(val quotes: List<FeeQuote>, val calculatedAt: Instant,
                            val feesLabel: String = "Taxas de serviço e pagamento")

/** Informational only: never creates an account, acceptance, order or payment instrument. */
class SimulateFinancialCharge(private val conditions: FinancialConditions) {
    fun execute(requestId: UUID, baseCents: Long, methods: Set<PaymentMethod>, at: Instant): FinancialResult<ChargeSimulation> {
        if (baseCents <= 0 || methods.isEmpty()) return FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId)
        val schedules = conditions.current(methods, at)
        if (schedules.map { it.method }.toSet() != methods || schedules.size != methods.size) {
            return FinancialResult.Failure(FinancialError.CONFIGURATION_UNAVAILABLE, requestId)
        }
        return try {
            FinancialResult.Success(ChargeSimulation(schedules.sortedBy { it.method.ordinal }
                .map { FeeCalculator.quote(baseCents, it) }, at), requestId)
        } catch (_: ArithmeticException) {
            FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId)
        }
    }
}
