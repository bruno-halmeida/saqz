package br.com.saqz.composeapp.navigation

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.session.SessionInvalidator
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Access orchestrator (T24): projects auth/session/selection/administration and owns
 * cross-route domain events (group switch, logout, deferred attendance). All per-route
 * screen state lives in the route-adapter ViewModels behind each NavEntry.
 */
internal class AccessViewModel(
    runtimeFactory: (CoroutineScope) -> AccessRuntimeContract,
) : MviViewModel<AccessUiState, AccessIntent, AccessUiEffect>(AccessUiState()) {
    private val runtime = runtimeFactory(viewModelScope)
    internal val groupProfileGateway: GroupProfileGateway get() = runtime.groupProfileGateway
    internal val groupPhotoGateway: GroupPhotoGateway get() = runtime.groupPhotoGateway
    internal val sessionInvalidator: SessionInvalidator get() = runtime.sessionInvalidator
    private var closed = false

    init {
        update { initialState() }
        combine(
            runtime.authObservedState,
            runtime.authenticationState,
            runtime.sessionState,
            runtime.selectionState,
            runtime.administrationState,
        ) { authObserved, authentication, session, selection, administration ->
            AccessUiState(
                authObserved = authObserved,
                authentication = authentication,
                session = session,
                selection = selection,
                administration = administration,
            )
        }.onEach { projected -> update { projected } }.launchIn(viewModelScope)

        runtime.effects.onEach { effect ->
            if (effect is AccessOrchestratorEffect.OpenAttendanceGame) {
                emit(AccessUiEffect.OpenAttendanceGame(effect.gameId))
            }
        }.launchIn(viewModelScope)
        runtime.onIntent(AccessRuntimeIntent.Start)
    }

    override fun onIntent(intent: AccessIntent) {
        when (intent) {
            is AccessIntent.Authentication -> runtime.onIntent(AccessRuntimeIntent.Authentication(intent.intent))
            is AccessIntent.Session -> runtime.onIntent(AccessRuntimeIntent.Session(intent.intent))
            is AccessIntent.Selection -> runtime.onIntent(AccessRuntimeIntent.Selection(intent.intent))
            is AccessIntent.Administration -> runtime.onIntent(AccessRuntimeIntent.Administration(intent.intent))
            is AccessIntent.DeferredInvite -> runtime.onIntent(AccessRuntimeIntent.DeferredInvite(intent.intent))
            is AccessIntent.DeferredAttendance -> runtime.onIntent(AccessRuntimeIntent.DeferredAttendance(intent.intent))
            AccessIntent.SwitchGroup -> switchGroup()
            AccessIntent.ConfirmLogout -> confirmLogout()
        }
    }

    internal fun newCommandKey(): String = runtime.newRequestId()

    override fun onCleared() {
        closeRuntimeOnce()
    }

    internal fun clearForTest() {
        closeRuntimeOnce()
    }

    private fun initialState(): AccessUiState = AccessUiState(
        authObserved = runtime.authObservedState.value,
        authentication = runtime.authenticationState.value,
        session = runtime.sessionState.value,
        selection = runtime.selectionState.value,
        administration = runtime.administrationState.value,
    )

    private fun switchGroup() {
        val ready = runtime.sessionState.value as? SessionAccessState.Ready ?: return
        runtime.onIntent(AccessRuntimeIntent.ShowGroupSelector(ready.session))
    }

    private fun confirmLogout() {
        runtime.onIntent(AccessRuntimeIntent.DeferredInvite(DeferredInviteIntent.Logout))
        runtime.onIntent(AccessRuntimeIntent.DeferredAttendance(DeferredAttendanceLinkIntent.Logout))
        runtime.onIntent(AccessRuntimeIntent.Session(SessionIntent.Logout))
    }

    private fun closeRuntimeOnce() {
        if (closed) return
        closed = true
        runtime.onIntent(AccessRuntimeIntent.Close)
    }
}
