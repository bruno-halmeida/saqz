package br.com.saqz.subscriptions.presentation.trial

import br.com.saqz.subscriptions.domain.trial.TrialAccess

enum class TrialEntryFailure { Load }
data class TrialEntryState(
    val loading: Boolean = true,
    val access: TrialAccess? = null,
    val failure: TrialEntryFailure? = null,
    val ready: Boolean = false,
)
sealed interface TrialEntryIntent {
    data object Refresh : TrialEntryIntent
}
