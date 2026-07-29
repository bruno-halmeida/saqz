package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthUiError {
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    AUTH_METHOD_CONFLICT,
    NETWORK_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    TOO_MANY_REQUESTS,
    UNKNOWN,
}

data class AuthenticationState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
)

sealed interface AuthTransition {
    data class Authenticated(val user: NativeUser) : AuthTransition
}

sealed interface AuthenticationIntent {
    data class UpdateEmail(val value: String) : AuthenticationIntent

    data class UpdatePassword(val value: String) : AuthenticationIntent

    data object SubmitPasswordLogin : AuthenticationIntent

    data object SubmitGoogleLogin : AuthenticationIntent
}

class AuthenticationStateMachine(
    private val auth: NativeAuthPort,
    private val transition: (AuthTransition) -> Unit,
) {
    private val mutableState = MutableStateFlow(AuthenticationState())
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    fun onIntent(intent: AuthenticationIntent) {
        when (intent) {
            is AuthenticationIntent.UpdateEmail -> updateForm { copy(email = intent.value) }
            is AuthenticationIntent.UpdatePassword -> updateForm { copy(password = intent.value) }
            AuthenticationIntent.SubmitPasswordLogin -> submitPasswordLogin()
            AuthenticationIntent.SubmitGoogleLogin -> submitGoogleLogin()
        }
    }

    private fun submitPasswordLogin() {
        val current = beginSensitiveSubmit() ?: return
        auth.signInWithPassword(current.email, current.password, authCallback(::completeLogin))
    }

    private fun submitGoogleLogin() {
        if (!beginSubmit()) return
        auth.signInWithGoogle(authCallback(::completeLogin))
    }

    private fun completeLogin(result: AuthResult) {
        when (result) {
            AuthResult.Cancelled -> finish()
            is AuthResult.Failure -> fail(result.code)
            is AuthResult.Success -> {
                finish()
                transition(AuthTransition.Authenticated(result.user))
            }
        }
    }

    private fun beginSensitiveSubmit(): AuthenticationState? {
        val captured = mutableState.value
        if (!beginSubmit()) return null
        mutableState.value = mutableState.value.copy(password = "")
        return captured
    }

    private fun beginSubmit(): Boolean {
        val current = mutableState.value
        if (current.isLoading) return false
        mutableState.value = current.copy(isLoading = true, error = null)
        return true
    }

    private fun finish() {
        mutableState.value = mutableState.value.copy(isLoading = false)
    }

    private fun fail(code: NativeFailureCode) {
        mutableState.value = mutableState.value.copy(
            isLoading = false,
            error = code.toUiError(),
        )
    }

    private fun updateForm(update: AuthenticationState.() -> AuthenticationState) {
        if (mutableState.value.isLoading) return
        mutableState.value = mutableState.value.update().copy(error = null)
    }

    private fun authCallback(block: (AuthResult) -> Unit) = object : AuthCallback {
        override fun complete(result: AuthResult) = block(result)
    }
}
