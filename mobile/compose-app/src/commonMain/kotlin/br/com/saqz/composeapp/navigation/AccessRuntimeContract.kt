package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.ui.InviteToolState
import kotlinx.coroutines.flow.StateFlow

internal interface AccessRuntimeContract {
    val authObservedState: StateFlow<Boolean>
    val authenticationState: StateFlow<AuthenticationState>
    val sessionState: StateFlow<SessionAccessState>
    val selectionState: StateFlow<GroupSelectionState>
    val administrationState: StateFlow<GroupAdministrationState>
    val inviteToolState: StateFlow<InviteToolState>
    val attendanceDestinationState: StateFlow<AttendanceLinkDestination?>
    val groupProfileGateway: GroupProfileGateway
    val groupPhotoGateway: GroupPhotoGateway

    fun onIntent(intent: AccessRuntimeIntent)

    fun newRequestId(): String
}
