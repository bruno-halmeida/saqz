package br.com.saqz.access.presentation.namecompletion

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class NameCompletionViewModel(
    private val session: SessionAccessStateMachine,
) : MviViewModel<NameCompletionState, NameCompletionIntent, NameCompletionEffect>(
    session.state.value.toNameCompletionState(),
) {

    init {
        session.state
            .onEach { current -> update { current.toNameCompletionState() } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: NameCompletionIntent) {
        when (intent) {
            is NameCompletionIntent.UpdateName -> session.onIntent(SessionIntent.UpdateName(intent.value))
            NameCompletionIntent.Complete -> session.onIntent(SessionIntent.CompleteName)
        }
    }
}

private fun SessionAccessState.toNameCompletionState(): NameCompletionState = when (this) {
    is SessionAccessState.CompletingName -> NameCompletionState(
        name = name,
        isLoading = isLoading,
        error = error,
        invalidName = invalidName,
    )
    else -> NameCompletionState()
}
