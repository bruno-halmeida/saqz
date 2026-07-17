package br.com.saqz.access.presentation

import br.com.saqz.access.port.AuthCallback
import br.com.saqz.access.port.AuthResult
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
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
)

sealed interface AuthTransition {
    data class Authenticated(val user: NativeUser) : AuthTransition

    data class VerificationRequired(val user: NativeUser) : AuthTransition
}

class AuthenticationCoordinator(
    private val auth: NativeAuthPort,
    private val transition: (AuthTransition) -> Unit,
) {
    private val mutableState = MutableStateFlow(AuthenticationState())
    val state: StateFlow<AuthenticationState> = mutableState.asStateFlow()

    fun showLogin() = show(AuthScreen.LOGIN)

    fun showRegistration() = show(AuthScreen.REGISTRATION)

    fun showPasswordReset() = show(AuthScreen.PASSWORD_RESET)

    fun updateName(value: String) = updateForm { copy(name = value) }

    fun updateEmail(value: String) = updateForm { copy(email = value) }

    fun updatePassword(value: String) = updateForm { copy(password = value) }

    fun submitRegistration() {
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

    fun submitPasswordLogin() {
        val current = beginSensitiveSubmit() ?: return
        auth.signInWithPassword(current.email, current.password, authCallback(::completeLogin))
    }

    fun submitGoogleLogin() {
        if (!beginSubmit()) return
        auth.signInWithGoogle(authCallback(::completeLogin))
    }

    fun submitPasswordReset() {
        val current = mutableState.value
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

private fun NativeFailureCode.toUiError(): AuthUiError = when (this) {
    NativeFailureCode.INVALID_CREDENTIALS -> AuthUiError.INVALID_CREDENTIALS
    NativeFailureCode.EMAIL_IN_USE -> AuthUiError.EMAIL_IN_USE
    NativeFailureCode.WEAK_PASSWORD -> AuthUiError.WEAK_PASSWORD
    NativeFailureCode.AUTH_METHOD_CONFLICT -> AuthUiError.AUTH_METHOD_CONFLICT
    NativeFailureCode.NETWORK_UNAVAILABLE -> AuthUiError.NETWORK_UNAVAILABLE
    NativeFailureCode.PROVIDER_UNAVAILABLE -> AuthUiError.PROVIDER_UNAVAILABLE
    NativeFailureCode.UNKNOWN -> AuthUiError.UNKNOWN
}
