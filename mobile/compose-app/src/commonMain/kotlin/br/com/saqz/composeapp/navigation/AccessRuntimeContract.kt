package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.InviteToolState
import br.com.saqz.network.SessionInvalidator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal interface AccessRuntimeContract {
    val state: StateFlow<AccessOrchestratorState> get() = error("Only AccessOrchestrator exposes combined state")
    val effects: Flow<AccessOrchestratorEffect> get() = emptyFlow()
    val authObservedState: StateFlow<Boolean>
    val authenticationState: StateFlow<AuthenticationState>
    val sessionState: StateFlow<SessionAccessState>
    val selectionState: StateFlow<GroupSelectionState>
    val administrationState: StateFlow<GroupAdministrationState>
    val inviteToolState: StateFlow<InviteToolState>
    val attendanceDestinationState: StateFlow<AttendanceLinkDestination?>
    val groupProfileGateway: GroupProfileGateway
    val groupPhotoGateway: GroupPhotoGateway
    val sessionInvalidator: SessionInvalidator

    fun onIntent(intent: AccessRuntimeIntent)

    fun newRequestId(): String
}
