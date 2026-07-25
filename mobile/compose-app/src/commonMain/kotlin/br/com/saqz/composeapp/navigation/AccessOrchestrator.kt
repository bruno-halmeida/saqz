package br.com.saqz.composeapp.navigation

import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
) : AccessRuntimeContract {
    private val mutableAuthObservedState = MutableStateFlow(false)
    override val authObservedState: StateFlow<Boolean> = mutableAuthObservedState.asStateFlow()
    override val authenticationState: StateFlow<AuthenticationState> = authentication.state
    override val sessionState: StateFlow<SessionAccessState> = session.state
    private var authSubscription: Cancelable? = null

    override fun onIntent(intent: AccessRuntimeIntent) {
        when (intent) {
            AccessRuntimeIntent.Start -> start()
            AccessRuntimeIntent.Close -> close()
            is AccessRuntimeIntent.Authentication -> authentication.onIntent(intent.intent)
            is AccessRuntimeIntent.Session -> session.onIntent(intent.intent)
        }
    }

    private fun start() {
        if (authSubscription != null) return
        authSubscription = auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) {
                mutableAuthObservedState.value = true
                // Signed out needs no fan-out: Login is the only signed-out destination
                // and it already renders the authentication machine's own state.
                if (state is AuthState.SignedIn) {
                    session.onIntent(SessionIntent.Accept(AuthTransition.Authenticated(state.user)))
                }
            }
        })
    }

    private fun close() {
        authSubscription?.cancel()
        authSubscription = null
    }
}
