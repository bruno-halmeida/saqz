package br.com.saqz.groups.presentation.home

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val displayName: String? = null,
    val home: HomeReadModel? = null,
    val member: HomeMemberUi? = null,
    val responding: Boolean = false,
    val responseFailed: Boolean = false,
    val toast: HomeToast? = null,
)

@Immutable
data class HomeMemberUi(
    val subtitle: String,
    val nextGame: HomeNextGameUi?,
    val lastCompletedGame: HomeLastCompletedGameUi?,
    val groups: List<HomeGroupUi>,
)

@Immutable
data class HomeNextGameUi(
    val groupId: String,
    val gameId: String,
    val groupName: String,
    val dateTime: String,
    val local: String,
    val deadline: String,
    val confirmedSummary: String,
    val confirmedCount: Int,
    val capacity: Int,
    val rosterNames: List<String>,
    val ownAttendance: AttendanceStatus?,
    val weekday: String,
    val time: String,
)

@Immutable
data class HomeLastCompletedGameUi(
    val day: String,
    val month: String,
    val title: String,
    val summary: String,
)

@Immutable
data class HomeGroupUi(
    val id: String,
    val name: String,
    val meta: String,
)

enum class HomeToast {
    Confirmed,
    Declined,
}

sealed interface HomeIntent {
    data object Retry : HomeIntent
    data class Respond(val intent: br.com.saqz.groups.domain.attendance.AttendanceIntent) : HomeIntent
    data object DismissToast : HomeIntent
    data object OpenGroups : HomeIntent
    data class OpenGroup(val groupId: String) : HomeIntent
}

sealed interface HomeEffect {
    data object OpenGroups : HomeEffect
    data class OpenGroup(val groupId: String) : HomeEffect
}
