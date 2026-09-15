package br.com.saqz.subscriptions.presentation.trial

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlinx.coroutines.launch

class TrialEntryViewModel(private val gateway: TrialGateway) :
    MviViewModel<TrialEntryState, TrialEntryIntent, Nothing>(TrialEntryState()) {
    private var generation = 0
    init { load() }
    override fun onIntent(intent: TrialEntryIntent) {
        when (intent) {
            TrialEntryIntent.Refresh -> load()
            TrialEntryIntent.Continue -> if (state.value.canContinue) load(proceed = true)
        }
    }
    private fun load(proceed: Boolean = false) {
        val accepted = state.value.access.takeIf { proceed }
        val request = ++generation
        update { it.copy(loading = true, failure = null, ready = false) }
        viewModelScope.launch {
            val result = gateway.ownerTrial()
            if (request != generation) return@launch
            when (result) {
                is SaqzResult.Success -> update { it.copy(loading = false, access = result.value,
                    ready = result.value.canCreateGroup && ((accepted != null && accepted.trialDays == result.value.trialDays &&
                        accepted.maxGroups == result.value.maxGroups && accepted.maxAthletes == result.value.maxAthletes) ||
                        result.value.preauthorized || result.value.status == TrialStatus.Active ||
                        result.value.status == TrialStatus.Subscribed)) }
                is SaqzResult.Failure -> update { it.copy(loading = false, access = null, failure = TrialEntryFailure.Load) }
            }
        }
    }
}
