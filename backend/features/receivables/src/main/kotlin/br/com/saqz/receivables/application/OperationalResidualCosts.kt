package br.com.saqz.receivables.application

import java.time.Instant
import java.util.UUID

data class ObservedResidualCost(
    val accountId: UUID,
    val instrumentId: UUID,
    val providerReference: String,
    val amountCents: Long,
)

fun interface ExternalResidualCostLedger {
    /** Appends the exact observed provider cost once; returns false for a duplicate fact. */
    fun append(observation: ObservedResidualCost, occurredAt: Instant): Boolean
}

class ReconcileExternalResidualCost(private val ledger: ExternalResidualCostLedger) {
    fun execute(observation: ObservedResidualCost, occurredAt: Instant): Boolean {
        require(observation.amountCents > 0)
        require(observation.providerReference.matches(Regex("[A-Za-z0-9:_-]{1,128}")))
        return ledger.append(observation, occurredAt)
    }
}
