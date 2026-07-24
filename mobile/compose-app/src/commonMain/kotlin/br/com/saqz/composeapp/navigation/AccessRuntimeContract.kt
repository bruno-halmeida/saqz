package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import kotlinx.coroutines.flow.StateFlow

internal interface AccessRuntimeContract {
    val authObservedState: StateFlow<Boolean>
    val authenticationState: StateFlow<AuthenticationState>
    val sessionState: StateFlow<SessionAccessState>

    fun onIntent(intent: AccessRuntimeIntent)
}
