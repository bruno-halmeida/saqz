package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent

/**
 * Orchestrator command surface (T24): domain pass-throughs plus the two cross-route
 * events (SwitchGroup, ConfirmLogout) that Roots raise as effects. Per-route screen
 * commands live on the route-adapter ViewModels.
 */
sealed interface AccessIntent {
    data class Authentication(val intent: AuthenticationIntent) : AccessIntent

    data class Session(val intent: SessionIntent) : AccessIntent

    data class Selection(val intent: GroupSelectionIntent) : AccessIntent

    data class Administration(val intent: GroupAdministrationIntent) : AccessIntent

    data class DeferredInvite(val intent: DeferredInviteIntent) : AccessIntent

    data class DeferredAttendance(val intent: DeferredAttendanceLinkIntent) : AccessIntent

    data object SwitchGroup : AccessIntent

    data object ConfirmLogout : AccessIntent
}
