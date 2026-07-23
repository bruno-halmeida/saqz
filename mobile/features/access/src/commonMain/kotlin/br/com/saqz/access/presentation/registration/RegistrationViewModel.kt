package br.com.saqz.access.presentation.registration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable

class RegistrationViewModel(
    private val authentication: AuthenticationStateMachine,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : MviViewModel<RegistrationState, RegistrationIntent, RegistrationEffect>(
    authentication.state.value.toRegistrationState(),
) {
    // No durable draft exists for registration, so this SavedStateHandle snapshot is the
    // authoritative restore for in-progress input after process death (PMVI-018). The password
    // is intentionally excluded and never persisted.
    private var formSnapshot by savedStateHandle.saved { RegistrationFormSnapshot() }

    init {
        val restored = formSnapshot
        if (restored.name.isNotEmpty()) authentication.onIntent(AuthenticationIntent.UpdateName(restored.name))
        if (restored.email.isNotEmpty()) authentication.onIntent(AuthenticationIntent.UpdateEmail(restored.email))
        authentication.state
            .onEach { auth ->
                update { auth.toRegistrationState() }
                formSnapshot = RegistrationFormSnapshot(auth.name, auth.email)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: RegistrationIntent) {
        when (intent) {
            is RegistrationIntent.UpdateName -> authentication.onIntent(AuthenticationIntent.UpdateName(intent.value))
            is RegistrationIntent.UpdateEmail -> authentication.onIntent(AuthenticationIntent.UpdateEmail(intent.value))
            is RegistrationIntent.UpdatePassword ->
                authentication.onIntent(AuthenticationIntent.UpdatePassword(intent.value))
            RegistrationIntent.SubmitRegistration -> authentication.onIntent(AuthenticationIntent.SubmitRegistration)
            RegistrationIntent.SubmitGoogleLogin -> authentication.onIntent(AuthenticationIntent.SubmitGoogleLogin)
            RegistrationIntent.ShowLogin -> authentication.onIntent(AuthenticationIntent.ShowLogin)
        }
    }
}

private fun AuthenticationState.toRegistrationState() = RegistrationState(
    name = name,
    email = email,
    password = password,
    isLoading = isLoading,
    error = error,
    validationAttempted = validationAttempted,
)

@Serializable
private data class RegistrationFormSnapshot(val name: String = "", val email: String = "")
