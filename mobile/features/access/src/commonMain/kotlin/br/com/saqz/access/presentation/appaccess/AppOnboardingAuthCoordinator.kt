package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.domain.appaccess.AppAccessGateway
import br.com.saqz.access.domain.appaccess.AppAccessSession
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.collections.LinkedHashSet

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
    private var confirmationCurrentOwnerId: String? = null
    private var authObserved = false
    private val attemptedCodes = LinkedHashSet<String>()

    fun redeem(code: String) {
        if (code.isBlank() || code in attemptedCodes) return
        // A newly issued link is an explicit new attempt; never replay a consumed code.
        reset()
        if (mutableState.value !is AppOnboardingAuthState.Idle || !attemptedCodes.add(code)) return
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
        val currentOwner = confirmationCurrentOwnerId
        val current = currentSession()
        if (currentOwner != null && current?.user?.id != currentOwner) {
            pending = null
            expectedOwnerUserId = null
            confirmationCurrentOwnerId = null
            mutableState.value = AppOnboardingAuthState.Failed(AppAccessError.IdentityMismatch)
            return
        }
        pending = null
        confirmationCurrentOwnerId = null
        signIn(session, generation, currentOwner)
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
        confirmationCurrentOwnerId = null
        mutableState.value = AppOnboardingAuthState.Idle
    }

    fun cancel() {
        generation++
        pending = null
        expectedOwnerUserId = null
        confirmationCurrentOwnerId = null
        mutableState.value = AppOnboardingAuthState.Idle
    }

    /**
     * Marks the single native observer as resolved. A signed-out observation is enough to
     * authorize the handoff; a signed-in observation still waits for the session machine to
     * verify the backend owner before touching Firebase again.
     */
    fun onAuthObservation(state: AuthState) {
        authObserved = true
        if (state is AuthState.SignedOut) onSessionResolution(null, resolving = sessionResolving())
    }

    /**
     * The session machine is authoritative for whether native auth has resolved. In
     * particular, bootstrap and identity-completion states are still an active identity and
     * must not trigger a second custom-token sign-in.
     */
    fun onSessionResolution(session: AccessSession?, resolving: Boolean) {
        if (mutableState.value !is AppOnboardingAuthState.WaitingForSessionResolution) return
        if (!authObserved || resolving) return
        val redeemed = pending ?: return
        when {
            session == null -> {
                pending = null
                signIn(redeemed, generation)
            }
            session.user.id != redeemed.ownerUserId -> {
                confirmationCurrentOwnerId = session.user.id
                mutableState.value = AppOnboardingAuthState.NeedsAccountConfirmation(redeemed.ownerUserId)
            }
            else -> {
                pending = null
                expectedOwnerUserId = null
                confirmationCurrentOwnerId = null
                mutableState.value = AppOnboardingAuthState.Completed(pendingOnboardingCompleted)
            }
        }
    }

    fun onAuthenticatedSession(session: AccessSession) {
        val expected = expectedOwnerUserId ?: return
        if (mutableState.value !is AppOnboardingAuthState.SigningIn) return
        if (session.user.id != expected) {
            expectedOwnerUserId = null
            confirmationCurrentOwnerId = null
            mutableState.value = AppOnboardingAuthState.Failed(AppAccessError.IdentityMismatch)
            return
        }
        expectedOwnerUserId = null
        confirmationCurrentOwnerId = null
        mutableState.value = AppOnboardingAuthState.Completed(pendingOnboardingCompleted)
    }

    private fun acceptRedeemed(token: Int, session: AppAccessSession) {
        if (token != generation) return
        expectedOwnerUserId = session.ownerUserId
        pendingOnboardingCompleted = session.onboardingCompleted
        val current = currentSession()
        if (!authObserved || sessionResolving() && current == null) {
            pending = session
            mutableState.value = AppOnboardingAuthState.WaitingForSessionResolution
            return
        }
        if (current != null && current.user.id != session.ownerUserId) {
            pending = session
            confirmationCurrentOwnerId = current.user.id
            mutableState.value = AppOnboardingAuthState.NeedsAccountConfirmation(session.ownerUserId)
            return
        }
        if (current != null) {
            pending = null
            expectedOwnerUserId = null
            confirmationCurrentOwnerId = null
            mutableState.value = AppOnboardingAuthState.Completed(session.onboardingCompleted)
            return
        }
        signIn(session, token)
    }

    private fun signIn(session: AppAccessSession, token: Int, confirmedCurrentOwnerId: String? = null) {
        if (token != generation) return
        val current = currentSession()
        if (confirmedCurrentOwnerId != null && current?.user?.id != confirmedCurrentOwnerId) {
            expectedOwnerUserId = null
            mutableState.value = AppOnboardingAuthState.Failed(AppAccessError.IdentityMismatch)
            return
        }
        if (confirmedCurrentOwnerId == null && current != null && current.user.id != session.ownerUserId) {
            pending = session
            confirmationCurrentOwnerId = current.user.id
            mutableState.value = AppOnboardingAuthState.NeedsAccountConfirmation(session.ownerUserId)
            return
        }
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
