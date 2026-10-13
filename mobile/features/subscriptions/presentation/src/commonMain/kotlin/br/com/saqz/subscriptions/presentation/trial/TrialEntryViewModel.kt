package br.com.saqz.subscriptions.presentation.trial

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import kotlinx.coroutines.launch

class TrialEntryViewModel(private val gateway: TrialGateway) :
    MviViewModel<TrialEntryState, TrialEntryIntent, Nothing>(TrialEntryState()) {
    private var generation = 0
    init { load() }
    override fun handleIntent(intent: TrialEntryIntent) {
        when (intent) {
            TrialEntryIntent.Refresh -> load()
        }
    }
    private fun load() {
        val request = ++generation
        update { it.copy(loading = true, failure = null, ready = false) }
        viewModelScope.launch {
            val result = gateway.ownerTrial()
            if (request != generation) return@launch
            when (result) {
                is SaqzResult.Success -> update { it.copy(loading = false, access = result.value,
                    ready = result.value.canCreateGroup) }
                is SaqzResult.Failure -> update { it.copy(loading = false, access = null, failure = TrialEntryFailure.Load) }
            }
        }
    }
}
