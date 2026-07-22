package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthScreen {
    LOGIN,
    REGISTRATION,
    PASSWORD_RESET,
}

enum class AuthUiError {
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    AUTH_METHOD_CONFLICT,
    NETWORK_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    UNKNOWN,
}

data class AuthenticationState(
    val screen: AuthScreen = AuthScreen.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
    val resetConfirmation: Boolean = false,
    val validationAttempted: Boolean = false,
)

sealed interface AuthTransition {
    data class Authenticated(val user: NativeUser) : AuthTransition

    data class VerificationRequired(val user: NativeUser) : AuthTransition
}

sealed interface AuthenticationIntent {
    data object ShowLogin : AuthenticationIntent

    data object ShowRegistration : AuthenticationIntent

    data object ShowPasswordReset : AuthenticationIntent

    data class UpdateName(val value: String) : AuthenticationIntent

    data class UpdateEmail(val value: String) : AuthenticationIntent

    data class UpdatePassword(val value: String) : AuthenticationIntent

    data object SubmitRegistration : AuthenticationIntent

    data object SubmitPasswordLogin : AuthenticationIntent

    data object SubmitGoogleLogin : AuthenticationIntent

    data object SubmitPasswordReset : AuthenticationIntent
}

class AuthenticationStateMachine(
    private val auth: NativeAuthPort,
    private val transition: (AuthTransition) -> Unit,
) {
    private val mutableState = MutableStateFlow(AuthenticationState())
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    fun onIntent(intent: AuthenticationIntent) {
        when (intent) {
            AuthenticationIntent.ShowLogin -> show(AuthScreen.LOGIN)
            AuthenticationIntent.ShowRegistration -> show(AuthScreen.REGISTRATION)
            AuthenticationIntent.ShowPasswordReset -> show(AuthScreen.PASSWORD_RESET)
            is AuthenticationIntent.UpdateName -> updateForm { copy(name = intent.value) }
            is AuthenticationIntent.UpdateEmail -> updateForm { copy(email = intent.value) }
            is AuthenticationIntent.UpdatePassword -> updateForm { copy(password = intent.value) }
            AuthenticationIntent.SubmitRegistration -> submitRegistration()
            AuthenticationIntent.SubmitPasswordLogin -> submitPasswordLogin()
            AuthenticationIntent.SubmitGoogleLogin -> submitGoogleLogin()
            AuthenticationIntent.SubmitPasswordReset -> submitPasswordReset()
        }
    }

    private fun submitRegistration() {
        mutableState.value = mutableState.value.copy(validationAttempted = true)
        val form = mutableState.value
        if (!isValidDisplayName(form.name) || !form.email.isValidEmail()) return
        val current = beginSensitiveSubmit() ?: return
        auth.createAccount(current.name, current.email, current.password, authCallback { result ->
            when (result) {
                AuthResult.Cancelled -> finish()
                is AuthResult.Failure -> fail(result.code)
                is AuthResult.Success -> auth.sendVerification(resultCallback { verification ->
                    when (verification) {
                        is OperationResult.Failure -> fail(verification.code)
                        OperationResult.Success -> {
                            finish()
                            transition(AuthTransition.VerificationRequired(result.user))
                        }
                    }
                })
            }
        })
    }

    private fun submitPasswordLogin() {
        val current = beginSensitiveSubmit() ?: return
        auth.signInWithPassword(current.email, current.password, authCallback(::completeLogin))
    }

    private fun submitGoogleLogin() {
        if (!beginSubmit()) return
        auth.signInWithGoogle(authCallback(::completeLogin))
    }

    private fun submitPasswordReset() {
        mutableState.value = mutableState.value.copy(validationAttempted = true)
        val current = mutableState.value
        if (!current.email.isValidEmail()) return
        if (!beginSubmit()) return
        auth.sendPasswordReset(current.email, resultCallback {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                error = null,
                resetConfirmation = true,
            )
        })
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
        mutableState.value = current.copy(isLoading = true, error = null, resetConfirmation = false)
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

    private fun show(screen: AuthScreen) {
        if (mutableState.value.isLoading) return
        mutableState.value = mutableState.value.copy(
            screen = screen,
            password = "",
            error = null,
            resetConfirmation = false,
            validationAttempted = false,
        )
    }

    private fun updateForm(update: AuthenticationState.() -> AuthenticationState) {
        if (mutableState.value.isLoading) return
        mutableState.value = mutableState.value.update().copy(error = null, resetConfirmation = false)
    }

    private fun authCallback(block: (AuthResult) -> Unit) = object : AuthCallback {
        override fun complete(result: AuthResult) = block(result)
    }

    private fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
        override fun complete(result: OperationResult) = block(result)
    }
}


