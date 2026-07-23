package br.com.saqz.access.presentation.passwordreset

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PasswordResetViewModel(
    private val authentication: AuthenticationStateMachine,
) : MviViewModel<PasswordResetState, PasswordResetIntent, PasswordResetEffect>(
    authentication.state.value.toPasswordResetState(),
) {

    init {
        authentication.state
            .onEach { auth -> update { auth.toPasswordResetState() } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: PasswordResetIntent) {
        when (intent) {
            is PasswordResetIntent.UpdateEmail -> authentication.onIntent(AuthenticationIntent.UpdateEmail(intent.value))
            PasswordResetIntent.SubmitPasswordReset -> authentication.onIntent(AuthenticationIntent.SubmitPasswordReset)
            PasswordResetIntent.ShowLogin -> authentication.onIntent(AuthenticationIntent.ShowLogin)
        }
    }
}

private fun AuthenticationState.toPasswordResetState() = PasswordResetState(
    email = email,
    isLoading = isLoading,
    resetConfirmation = resetConfirmation,
    validationAttempted = validationAttempted,
)
