package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState

/**
 * Core orchestrator projection (T24): auth/session/selection/administration only.
 * Per-route screen state (settings form, invite tool, create-group form, dialogs)
 * is owned by the route-adapter ViewModels behind each NavEntry.
 */
@Immutable
internal data class AccessUiState(
    val authObserved: Boolean = false,
    val authentication: AuthenticationState = AuthenticationState(),
    val session: SessionAccessState = SessionAccessState.SignedOut,
    val selection: GroupSelectionState = GroupSelectionState.NoGroup,
    val administration: GroupAdministrationState = GroupAdministrationState(),
)

internal typealias AccessRootSnapshot = AccessUiState
