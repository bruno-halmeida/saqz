package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.ui.InviteToolState

@Immutable
internal data class AccessUiState(
    val authObserved: Boolean = false,
    val authentication: AuthenticationState = AuthenticationState(),
    val session: SessionAccessState = SessionAccessState.SignedOut,
    val selection: GroupSelectionState = GroupSelectionState.NoGroup,
    val administration: GroupAdministrationState = GroupAdministrationState(),
    val page: AccessPage = AccessPage.CONTEXT,
    val createName: String = "",
    val createTimeZone: String = "",
    val createValidationAttempted: Boolean = false,
    val createFlowKey: String = "",
    val settingsName: String = "",
    val settingsTimeZone: String = "",
    val invite: InviteToolState = InviteToolState(),
    val showLogoutConfirmation: Boolean = false,
    val showExpireConfirmation: Boolean = false,
)

internal typealias AccessRootSnapshot = AccessUiState
