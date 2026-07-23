package br.com.saqz.access.presentation.verification

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class VerificationViewModel(
    private val session: SessionAccessStateMachine,
) : MviViewModel<VerificationState, VerificationIntent, VerificationEffect>(
    session.state.value.toVerificationState(),
) {

    init {
        session.state
            .onEach { current -> update { current.toVerificationState() } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: VerificationIntent) {
        when (intent) {
            VerificationIntent.Confirm -> session.onIntent(SessionIntent.ConfirmVerification)
            VerificationIntent.Resend -> session.onIntent(SessionIntent.ResendVerification)
        }
    }
}

private fun SessionAccessState.toVerificationState(): VerificationState = when (this) {
    is SessionAccessState.AwaitingVerification -> VerificationState(
        email = user.email.orEmpty(),
        isLoading = isLoading,
        error = error,
        verificationSent = verificationSent,
    )
    else -> VerificationState()
}
