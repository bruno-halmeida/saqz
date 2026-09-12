package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.domain.appaccess.AppAccessGateway
import br.com.saqz.access.domain.appaccess.AppAccessSession
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppOnboardingAuthState {
    data object Idle : AppOnboardingAuthState
    data object Redeeming : AppOnboardingAuthState
    data object WaitingForSessionResolution : AppOnboardingAuthState
    data class NeedsAccountConfirmation(val ownerUserId: String) : AppOnboardingAuthState
    data object SigningIn : AppOnboardingAuthState
    data class Completed(val onboardingCompleted: Boolean) : AppOnboardingAuthState
    data class Failed(val error: AppAccessError) : AppOnboardingAuthState
}

/**
 * Serializes the app-link handoff around both the network redeem and Firebase mutation.
 * The custom token and code are held only in this object for the current attempt and never
 * become navigation or saved-state arguments.
 */
class AppOnboardingAuthCoordinator(
    private val auth: NativeAuthPort,
    private val gateway: AppAccessGateway,
    private val scope: CoroutineScope,
    private val currentSession: () -> AccessSession?,
    private val sessionResolving: () -> Boolean,
) {
    private val mutableState = MutableStateFlow<AppOnboardingAuthState>(AppOnboardingAuthState.Idle)
    val state: StateFlow<AppOnboardingAuthState> = mutableState.asStateFlow()
    private var generation = 0
    private var pending: AppAccessSession? = null
    private var pendingOnboardingCompleted = false
    private var expectedOwnerUserId: String? = null

    fun redeem(code: String) {
        if (code.isBlank() || mutableState.value !is AppOnboardingAuthState.Idle) return
        val token = ++generation
        pending = null
        pendingOnboardingCompleted = false
        mutableState.value = AppOnboardingAuthState.Redeeming
        scope.launch {
            val result = gateway.redeem(code)
            if (token != generation) return@launch
            when (result) {
                is SaqzResult.Failure -> mutableState.value = AppOnboardingAuthState.Failed(result.error)
                is SaqzResult.Success -> acceptRedeemed(token, result.value)
            }
        }
    }

    fun confirmAccountReplacement() {
        val session = pending ?: return
        if (mutableState.value !is AppOnboardingAuthState.NeedsAccountConfirmation) return
        pending = null
        signIn(session, generation)
    }

    /**
     * Explicitly starts a new handoff attempt after a terminal result. Duplicate link events
     * remain ignored until the UI makes this reset explicit.
     */
    fun reset() {
        if (mutableState.value !is AppOnboardingAuthState.Failed &&
            mutableState.value !is AppOnboardingAuthState.Completed
        ) return
        generation++
        pending = null
        pendingOnboardingCompleted = false
        expectedOwnerUserId = null
        mutableState.value = AppOnboardingAuthState.Idle
    }

    fun cancel() {
        generation++
        pending = null
        expectedOwnerUserId = null
        mutableState.value = AppOnboardingAuthState.Idle
    }

    /**
     * The session machine is authoritative for whether native auth has resolved. In
     * particular, bootstrap and identity-completion states are still an active identity and
     * must not trigger a second custom-token sign-in.
     */
    fun onSessionResolution(session: AccessSession?, resolving: Boolean) {
        if (mutableState.value !is AppOnboardingAuthState.WaitingForSessionResolution) return
        if (resolving) return
        val redeemed = pending ?: return
        if (session == null) {
            pending = null
            signIn(redeemed, generation)
            return
        }
        if (session.user.id != redeemed.ownerUserId) {
            mutableState.value = AppOnboardingAuthState.NeedsAccountConfirmation(redeemed.ownerUserId)
            return
        }
        pending = null
        expectedOwnerUserId = null
        mutableState.value = AppOnboardingAuthState.Completed(pendingOnboardingCompleted)
    }

    fun onAuthenticatedSession(session: AccessSession) {
        val expected = expectedOwnerUserId ?: return
        if (mutableState.value !is AppOnboardingAuthState.SigningIn) return
        if (session.user.id != expected) {
            expectedOwnerUserId = null
            mutableState.value = AppOnboardingAuthState.Failed(AppAccessError.IdentityMismatch)
            return
        }
        expectedOwnerUserId = null
        mutableState.value = AppOnboardingAuthState.Completed(pendingOnboardingCompleted)
    }

    private fun acceptRedeemed(token: Int, session: AppAccessSession) {
        if (token != generation) return
        expectedOwnerUserId = session.ownerUserId
        pendingOnboardingCompleted = session.onboardingCompleted
        val current = currentSession()
        if (sessionResolving() && current == null) {
            pending = session
            mutableState.value = AppOnboardingAuthState.WaitingForSessionResolution
            return
        }
        if (current != null && current.user.id != session.ownerUserId) {
            pending = session
            mutableState.value = AppOnboardingAuthState.NeedsAccountConfirmation(session.ownerUserId)
            return
        }
        if (current != null) {
            pending = null
            expectedOwnerUserId = null
            mutableState.value = AppOnboardingAuthState.Completed(session.onboardingCompleted)
            return
        }
        signIn(session, token)
    }

    private fun signIn(session: AppAccessSession, token: Int) {
        if (token != generation) return
        expectedOwnerUserId = session.ownerUserId
        pendingOnboardingCompleted = session.onboardingCompleted
        mutableState.value = AppOnboardingAuthState.SigningIn
        auth.signInWithCustomToken(session.customToken, object : AuthCallback {
            override fun complete(result: AuthResult) {
                if (token != generation) return
                if (result is AuthResult.Failure || result == AuthResult.Cancelled) {
                    mutableState.value = AppOnboardingAuthState.Failed(AppAccessError.ProviderUnavailable)
                }
            }
        })
    }
}
