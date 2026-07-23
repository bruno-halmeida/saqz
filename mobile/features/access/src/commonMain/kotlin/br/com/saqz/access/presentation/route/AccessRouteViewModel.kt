package br.com.saqz.access.presentation.route

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Reusable Access route adapter (T11): every entry-owned instance is
 * constructed with its own [mode] and projects the shared, singleton
 * [SessionAccessStateMachine] -- it owns no authentication/session state
 * machine of its own (ACCESSNAV-01, LIFE-01, LIFE-03, LIFE-05).
 */
class AccessRouteViewModel(
    private val mode: AccessRouteMode,
    private val session: SessionAccessStateMachine,
) : MviViewModel<AccessRouteState, AccessRouteIntent, AccessRouteEffect>(
    initialStateFor(mode, session.state.value),
) {

    init {
        if (mode == AccessRouteMode.BOOTSTRAP) {
            session.state
                .onEach { current -> update { current.toBootstrapState() } }
                .launchIn(viewModelScope)
        }
    }

    override fun onIntent(intent: AccessRouteIntent) {
        when (intent) {
            AccessRouteIntent.RetryBootstrap -> {
                if (mode == AccessRouteMode.BOOTSTRAP) session.onIntent(SessionIntent.RetryBootstrap)
            }
        }
    }
}

private fun initialStateFor(mode: AccessRouteMode, session: SessionAccessState): AccessRouteState =
    when (mode) {
        AccessRouteMode.STARTING -> AccessRouteState.Starting
        AccessRouteMode.BOOTSTRAP -> session.toBootstrapState()
    }

private fun SessionAccessState.toBootstrapState(): AccessRouteState.Bootstrap = when (this) {
    SessionAccessState.Bootstrapping -> AccessRouteState.Bootstrap(isLoading = true, failed = false)
    SessionAccessState.BootstrapError -> AccessRouteState.Bootstrap(isLoading = false, failed = true)
    else -> AccessRouteState.Bootstrap(isLoading = false, failed = false)
}
