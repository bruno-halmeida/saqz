package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent
import br.com.saqz.network.SessionDto

internal sealed interface AccessRuntimeIntent {
    data object Start : AccessRuntimeIntent

    data object Close : AccessRuntimeIntent

    data class Authentication(val intent: AuthenticationIntent) : AccessRuntimeIntent

    data class Session(val intent: SessionIntent) : AccessRuntimeIntent

    data class Selection(val intent: GroupSelectionIntent) : AccessRuntimeIntent

    data class Administration(val intent: GroupAdministrationIntent) : AccessRuntimeIntent

    data class DeferredInvite(val intent: DeferredInviteIntent) : AccessRuntimeIntent

    data class DeferredAttendance(val intent: DeferredAttendanceLinkIntent) : AccessRuntimeIntent

    data object ConsumeAttendanceDestination : AccessRuntimeIntent

    data class ShowGroupSelector(val session: SessionDto) : AccessRuntimeIntent

    data object RotateInvite : AccessRuntimeIntent

    data object ExpireInvite : AccessRuntimeIntent

    data class ShareFinished(val successful: Boolean) : AccessRuntimeIntent
}
