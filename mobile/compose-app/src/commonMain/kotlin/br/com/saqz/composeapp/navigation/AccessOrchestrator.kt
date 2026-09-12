package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeLinkPort
import br.com.saqz.access.domain.port.AppOnboardingCodeListener
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthCoordinator
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Access orchestrator (T24, sliced for C1): owns the auth-observation lifecycle and
 * projects auth/authentication/session state. Selection/administration/invite/attendance
 * coordination (product-wide, groups-owned) is gone -- it dies with the module that
 * consumed it, not here.
 */
internal class AccessOrchestrator(
    private val auth: NativeAuthPort,
    private val authentication: AuthenticationStateMachine,
    private val session: SessionAccessStateMachine,
    private val links: NativeLinkPort,
    private val appOnboarding: AppOnboardingAuthCoordinator,
    private val scope: CoroutineScope,
) : AccessRuntimeContract {
    private val mutableAuthObservedState = MutableStateFlow(false)
    override val authObservedState: StateFlow<Boolean> = mutableAuthObservedState.asStateFlow()
    override val authenticationState: StateFlow<AuthenticationState> = authentication.state
    override val sessionState: StateFlow<SessionAccessState> = session.state
    override val appOnboardingState: StateFlow<br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState> = appOnboarding.state
    private var authSubscription: Cancelable? = null
    private var onboardingSubscription: Cancelable? = null

    init {
        scope.launch {
            session.state.collect { state ->
                when (state) {
                    is SessionAccessState.Ready -> {
                        appOnboarding.onSessionResolution(state.session, resolving = false)
                        appOnboarding.onAuthenticatedSession(state.session)
                    }
                    is SessionAccessState.CompletingIdentity ->
                        appOnboarding.onSessionResolution(state.session, resolving = state.session == null)
                    SessionAccessState.SignedOut -> appOnboarding.onSessionResolution(null, resolving = false)
                    SessionAccessState.Bootstrapping,
                    SessionAccessState.BootstrapError,
                    -> appOnboarding.onSessionResolution(null, resolving = true)
                }
            }
        }
    }

    override fun onIntent(intent: AccessRuntimeIntent) {
        when (intent) {
            AccessRuntimeIntent.Start -> start()
            AccessRuntimeIntent.Close -> close()
            is AccessRuntimeIntent.Authentication -> authentication.onIntent(intent.intent)
            is AccessRuntimeIntent.Session -> {
                if (intent.intent == SessionIntent.Logout) appOnboarding.cancel()
                session.onIntent(intent.intent)
            }
        }
    }

    private fun start() {
        if (authSubscription != null) return
        authSubscription = auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) {
                mutableAuthObservedState.value = true
                appOnboarding.onAuthObservation(state)
                // Signed out needs no fan-out: Login is the only signed-out destination
                // and it already renders the authentication machine's own state.
                if (state is AuthState.SignedIn) {
                    session.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(state.user)))
                }
            }
        })
        onboardingSubscription = links.startAppOnboarding(object : AppOnboardingCodeListener {
            override fun onAppOnboardingCode(code: String) = appOnboarding.redeem(code)
        })
    }

    private fun close() {
        authSubscription?.cancel()
        authSubscription = null
        onboardingSubscription?.cancel()
        onboardingSubscription = null
    }
}
