package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.SessionDto
import br.com.saqz.network.SessionGateway
import br.com.saqz.network.SessionInvalidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionAccessState {
    data object SignedOut : SessionAccessState

    data class AwaitingVerification(
        val user: NativeUser,
        val isLoading: Boolean = false,
        val error: AuthUiError? = null,
        val verificationSent: Boolean = false,
    ) : SessionAccessState

    data class CompletingName(
        val user: NativeUser,
        val name: String = "",
        val isLoading: Boolean = false,
        val error: AuthUiError? = null,
        val invalidName: Boolean = false,
    ) : SessionAccessState

    data object Bootstrapping : SessionAccessState

    data object BootstrapError : SessionAccessState

    data class Ready(val session: SessionDto) : SessionAccessState
}

sealed interface SessionIntent {
    data class Accept(val transition: AuthTransition) : SessionIntent

    data object ConfirmVerification : SessionIntent

    data object ResendVerification : SessionIntent

    data class UpdateName(val value: String) : SessionIntent

    data object CompleteName : SessionIntent

    data object RetryBootstrap : SessionIntent

    data object Logout : SessionIntent
}

class SessionAccessStateMachine(
    private val auth: NativeAuthPort,
    private val localState: LocalAccessStatePort,
    private val session: SessionGateway,
    private val scope: CoroutineScope,
) : SessionInvalidator {
    private val mutableState = MutableStateFlow<SessionAccessState>(SessionAccessState.SignedOut)
    val state: StateFlow<SessionAccessState> = mutableState.asStateFlow()
    private var currentUser: NativeUser? = null
    private var loggingOut = false

    fun onIntent(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.Accept -> when (val transition = intent.transition) {
                is AuthTransition.Authenticated -> routeIdentity(transition.user)
                is AuthTransition.VerificationRequired -> awaitVerification(transition.user)
            }
            SessionIntent.ConfirmVerification -> confirmVerification()
            SessionIntent.ResendVerification -> resendVerification()
            is SessionIntent.UpdateName -> updateName(intent.value)
            SessionIntent.CompleteName -> completeName()
            SessionIntent.RetryBootstrap -> retryBootstrap()
            SessionIntent.Logout -> logout()
        }
    }

    private fun confirmVerification() {
        val current = mutableState.value as? SessionAccessState.AwaitingVerification ?: return
        if (current.isLoading) return
        mutableState.value = current.copy(isLoading = true, error = null, verificationSent = false)
        auth.reloadUser(authCallback { result ->
            when (result) {
                AuthResult.Cancelled -> awaitVerification(current.user)
                is AuthResult.Failure -> verificationFailure(current.user, result.code)
                is AuthResult.Success -> {
                    if (result.user.emailVerified) forceRefreshAndBootstrap(result.user)
                    else awaitVerification(result.user)
                }
            }
        })
    }

    private fun resendVerification() {
        val current = mutableState.value as? SessionAccessState.AwaitingVerification ?: return
        if (current.isLoading) return
        mutableState.value = current.copy(isLoading = true, error = null, verificationSent = false)
        auth.sendVerification(resultCallback { result ->
            mutableState.value = when (result) {
                OperationResult.Success -> current.copy(verificationSent = true)
                is OperationResult.Failure -> current.copy(error = result.code.toUiError())
            }
        })
    }

    private fun updateName(value: String) {
        val current = mutableState.value as? SessionAccessState.CompletingName ?: return
        if (!current.isLoading) mutableState.value = current.copy(name = value, error = null, invalidName = false)
    }

    private fun completeName() {
        val current = mutableState.value as? SessionAccessState.CompletingName ?: return
        if (current.isLoading) return
        val name = normalizedDisplayName(current.name)
        if (name == null) {
            mutableState.value = current.copy(invalidName = true)
            return
        }
        mutableState.value = current.copy(isLoading = true, error = null, invalidName = false)
        auth.updateDisplayName(name, authCallback { result ->
            when (result) {
                AuthResult.Cancelled -> mutableState.value = current.copy(name = name)
                is AuthResult.Failure -> mutableState.value = current.copy(name = name, error = result.code.toUiError())
                is AuthResult.Success -> forceRefreshAndBootstrap(result.user)
            }
        })
    }

    private fun retryBootstrap() {
        val user = currentUser ?: return
        if (mutableState.value !is SessionAccessState.BootstrapError) return
        bootstrap(user)
    }

    private fun logout() {
        if (loggingOut) return
        loggingOut = true
        localState.writeSelectedGroupId(null, resultCallback {
            localState.writePendingInvite(null, resultCallback {
                auth.signOut(resultCallback {
                    currentUser = null
                    loggingOut = false
                    mutableState.value = SessionAccessState.SignedOut
                })
            })
        })
    }

    override fun invalidate() = onIntent(SessionIntent.Logout)

    private fun routeIdentity(user: NativeUser) {
        currentUser = user
        when {
            !user.emailVerified -> awaitVerification(user)
            normalizedDisplayName(user.displayName.orEmpty()) == null -> {
                mutableState.value = SessionAccessState.CompletingName(user)
            }
            else -> bootstrap(user)
        }
    }

    private fun awaitVerification(user: NativeUser) {
        currentUser = user
        mutableState.value = SessionAccessState.AwaitingVerification(user)
    }

    private fun verificationFailure(user: NativeUser, code: NativeFailureCode) {
        mutableState.value = SessionAccessState.AwaitingVerification(user, error = code.toUiError())
    }

    private fun forceRefreshAndBootstrap(user: NativeUser) {
        currentUser = user
        auth.idToken(true, object : TokenCallback {
            override fun complete(result: TokenResult) {
                when (result) {
                    is TokenResult.Failure -> identityFailure(user, result.code)
                    is TokenResult.Success -> routeIdentity(user)
                }
            }
        })
    }

    private fun identityFailure(user: NativeUser, code: NativeFailureCode) {
        mutableState.value = if (normalizedDisplayName(user.displayName.orEmpty()) == null) {
            SessionAccessState.CompletingName(user, error = code.toUiError())
        } else {
            SessionAccessState.AwaitingVerification(user, error = code.toUiError())
        }
    }

    private fun bootstrap(user: NativeUser) {
        currentUser = user
        mutableState.value = SessionAccessState.Bootstrapping
        scope.launch {
            mutableState.value = when (val result = session.bootstrap()) {
                is NetworkResult.Success -> SessionAccessState.Ready(result.value)
                is NetworkResult.Failure -> when {
                    result.error.isEmailNotVerified() -> {
                        val unverified = user.copy(emailVerified = false)
                        currentUser = unverified
                        SessionAccessState.AwaitingVerification(unverified)
                    }
                    else -> SessionAccessState.BootstrapError
                }
            }
        }
    }

    private fun authCallback(block: (AuthResult) -> Unit) = object : AuthCallback {
        override fun complete(result: AuthResult) = block(result)
    }

    private fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
        override fun complete(result: OperationResult) = block(result)
    }
}

private fun NetworkError.isEmailNotVerified(): Boolean =
    this is NetworkError.ApiProblemError && problem.status == 403 && problem.code == "EMAIL_NOT_VERIFIED"


