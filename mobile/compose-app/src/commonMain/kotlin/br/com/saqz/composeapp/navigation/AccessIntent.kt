package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.groups.data.PersistedRoleDto
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent

sealed interface AccessIntent {
    data class Authentication(val intent: AuthenticationIntent) : AccessIntent

    data class Session(val intent: SessionIntent) : AccessIntent

    data class Selection(val intent: GroupSelectionIntent) : AccessIntent

    data class Administration(val intent: GroupAdministrationIntent) : AccessIntent

    data class DeferredInvite(val intent: DeferredInviteIntent) : AccessIntent

    data class DeferredAttendance(val intent: DeferredAttendanceLinkIntent) : AccessIntent

    data object OpenCreateGroup : AccessIntent

    data class UpdateCreateName(val value: String) : AccessIntent

    data class UpdateCreateTimeZone(val value: String) : AccessIntent

    data object SubmitCreateGroup : AccessIntent

    data object ClosePage : AccessIntent

    data object SwitchGroup : AccessIntent

    data object OpenSettings : AccessIntent

    data object OpenMemberships : AccessIntent

    data object OpenInvite : AccessIntent

    data object RequestLogout : AccessIntent

    data object ConfirmLogout : AccessIntent

    data object CancelLogout : AccessIntent

    data class UpdateSettingsName(val value: String) : AccessIntent

    data class UpdateSettingsTimeZone(val value: String) : AccessIntent

    data object SaveSettings : AccessIntent

    data object ReloadSettings : AccessIntent

    data object GenerateInvite : AccessIntent

    data class ShareInvite(val url: String) : AccessIntent

    data class ShareFinished(val successful: Boolean) : AccessIntent

    data object RequestExpireInvite : AccessIntent

    data object ConfirmExpireInvite : AccessIntent

    data object CancelExpireInvite : AccessIntent

    data object RetryInvite : AccessIntent

    data class ChangeRole(val userId: String, val role: PersistedRoleDto) : AccessIntent
}
