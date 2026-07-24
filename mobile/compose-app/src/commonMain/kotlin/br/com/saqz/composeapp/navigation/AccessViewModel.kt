package br.com.saqz.composeapp.navigation

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Access gate view model (T24, sliced for C1): projects auth/authentication/session and
 * routes ConfirmLogout to the shared session. Group selection/administration/invite/
 * attendance coordination died with the groups feature entirely -- this view model no
 * longer knows those screens exist.
 */
internal class AccessViewModel(
    private val runtime: AccessRuntimeContract,
) : MviViewModel<AccessUiState, AccessIntent, Nothing>(AccessUiState()) {
    private var closed = false

    init {
        update { initialState() }
        combine(
            runtime.authObservedState,
            runtime.authenticationState,
            runtime.sessionState,
        ) { authObserved, authentication, session ->
            AccessUiState(authObserved = authObserved, authentication = authentication, session = session)
        }.onEach { projected -> update { projected } }.launchIn(viewModelScope)
        runtime.onIntent(AccessRuntimeIntent.Start)
    }

    override fun onIntent(intent: AccessIntent) {
        when (intent) {
            is AccessIntent.Authentication -> runtime.onIntent(AccessRuntimeIntent.Authentication(intent.intent))
            is AccessIntent.Session -> runtime.onIntent(AccessRuntimeIntent.Session(intent.intent))
            AccessIntent.ConfirmLogout -> confirmLogout()
        }
    }

    override fun onCleared() {
        closeRuntimeOnce()
    }

    private fun initialState(): AccessUiState = AccessUiState(
        authObserved = runtime.authObservedState.value,
        authentication = runtime.authenticationState.value,
        session = runtime.sessionState.value,
    )

    private fun confirmLogout() {
        runtime.onIntent(AccessRuntimeIntent.Session(SessionIntent.Logout))
    }

    private fun closeRuntimeOnce() {
        if (closed) return
        closed = true
        runtime.onIntent(AccessRuntimeIntent.Close)
    }
}
