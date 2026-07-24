package br.com.saqz.access.presentation.phonecompletion

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PhoneCompletionViewModel(
    private val session: SessionAccessStateMachine,
) : MviViewModel<PhoneCompletionState, PhoneCompletionIntent, PhoneCompletionEffect>(
    session.state.value.toPhoneCompletionState(),
) {

    init {
        session.state
            .onEach { current -> update { current.toPhoneCompletionState() } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: PhoneCompletionIntent) {
        when (intent) {
            is PhoneCompletionIntent.UpdatePhone -> session.onIntent(SessionIntent.UpdatePhone(intent.value))
            PhoneCompletionIntent.Complete -> session.onIntent(SessionIntent.CompletePhone)
        }
    }
}

private fun SessionAccessState.toPhoneCompletionState(): PhoneCompletionState = when (this) {
    is SessionAccessState.CompletingPhone -> PhoneCompletionState(
        phone = phone,
        isLoading = isLoading,
        error = error,
        invalidPhone = invalidPhone,
    )
    else -> PhoneCompletionState()
}
