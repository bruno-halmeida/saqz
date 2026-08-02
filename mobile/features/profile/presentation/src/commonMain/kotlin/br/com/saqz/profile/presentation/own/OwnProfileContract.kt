package br.com.saqz.profile.presentation.own

import androidx.compose.runtime.Immutable

@Immutable
data class OwnProfileState(
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val user: OwnProfileUserUi? = null,
    val stats: OwnProfileStatsUi? = null,
    val groups: List<OwnProfileGroupUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !isLoading && !loadError && groups.isEmpty()

    val showAttendance: Boolean
        get() = stats?.attendanceLabel != null
}

@Immutable
data class OwnProfileUserUi(
    val displayName: String,
    val subtitle: String?,
    val photoUrl: String?,
)

@Immutable
data class OwnProfileStatsUi(
    val gamesLabel: String,
    val attendanceLabel: String?,
    val groupsLabel: String,
)

@Immutable
data class OwnProfileGroupUi(
    val id: String,
    val name: String,
    val details: String?,
    val membershipLabel: String,
    val isMonthly: Boolean,
)

sealed interface OwnProfileIntent {
    data object Refresh : OwnProfileIntent
    data object EditData : OwnProfileIntent
    data object OpenSettings : OwnProfileIntent
    data object OpenMonthlyPayments : OwnProfileIntent
    data object OpenNotifications : OwnProfileIntent
    data object ChangePassword : OwnProfileIntent
    data object SignOut : OwnProfileIntent
    data class OpenGroup(val groupId: String) : OwnProfileIntent
}

sealed interface OwnProfileEffect {
    data object OpenEditor : OwnProfileEffect
    data object OpenPasswordRecovery : OwnProfileEffect
    data object SignedOut : OwnProfileEffect
}

internal fun profileSubtitle(nickname: String?, city: String?): String? =
    listOfNotNull(nickname?.takeIf(String::isNotBlank), city?.takeIf(String::isNotBlank))
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
