package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState

internal data class AccessCoreState(
    val authObserved: Boolean,
    val authentication: AuthenticationState,
    val session: SessionAccessState,
    val selection: GroupSelectionState,
    val administration: GroupAdministrationState,
)
