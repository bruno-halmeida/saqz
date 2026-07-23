package br.com.saqz.access.presentation.login

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.message
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LoginViewModel(
    private val authentication: AuthenticationStateMachine,
) : MviViewModel<LoginState, LoginIntent, LoginEffect>(authentication.state.value.toLoginState()) {

    init {
        authentication.state
            .onEach { auth -> update { auth.toLoginState() } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.UpdateEmail -> authentication.onIntent(AuthenticationIntent.UpdateEmail(intent.value))
            is LoginIntent.UpdatePassword -> authentication.onIntent(AuthenticationIntent.UpdatePassword(intent.value))
            LoginIntent.SubmitPasswordLogin -> authentication.onIntent(AuthenticationIntent.SubmitPasswordLogin)
            LoginIntent.SubmitGoogleLogin -> authentication.onIntent(AuthenticationIntent.SubmitGoogleLogin)
            LoginIntent.ShowRegistration -> authentication.onIntent(AuthenticationIntent.ShowRegistration)
            LoginIntent.ShowPasswordReset -> authentication.onIntent(AuthenticationIntent.ShowPasswordReset)
        }
    }
}

private fun AuthenticationState.toLoginState() = LoginState(
    email = email,
    password = password,
    isLoading = isLoading,
    error = error?.message(),
)
