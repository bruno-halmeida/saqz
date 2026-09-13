package br.com.saqz.receivables.domain

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

/** Discovery only: approval, commercial eligibility and group activation remain server concerns. */
data class ReceivablesAvailability(
    val backendEnabled: Boolean,
    val mobileEnabled: Boolean,
) {
    val newJourneysAvailable: Boolean get() = backendEnabled && mobileEnabled
}

sealed interface ReceivablesError : SaqzError {
    data class Data(val error: DataError) : ReceivablesError
}

interface ReceivablesAvailabilityGateway {
    /** Reads the authenticated user's availability; never accepts a client-selected user ID. */
    suspend fun get(): SaqzResult<ReceivablesAvailability, ReceivablesError>
}

/** Local session context, read synchronously to reject replies before session observers catch up. */
fun interface ReceivablesSessionContext {
    fun currentKey(): String?
}
