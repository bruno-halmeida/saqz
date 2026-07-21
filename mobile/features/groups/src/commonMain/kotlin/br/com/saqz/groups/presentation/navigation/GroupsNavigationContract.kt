package br.com.saqz.groups.presentation.navigation

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.network.SessionMembershipDto

enum class GroupsDestination {
    SETUP,
    SELECTOR,
    LOADING,
    LOAD_ERROR,
    HOME,
    PROFILE_COMPLETION,
    PEOPLE,
    GAMES,
    GAME_DETAIL,
    FINANCE,
    OWN_CHARGES,
}

@Immutable
data class GroupsNavigationAccess(
    val showPeople: Boolean = false,
    val showGames: Boolean = false,
    val showFinance: Boolean = false,
    val canCompleteProfile: Boolean = false,
    val canMutateOperations: Boolean = false,
    val financeDestination: GroupsDestination? = null,
)

@Immutable
data class GroupsNavigationState(
    val destination: GroupsDestination = GroupsDestination.SETUP,
    val groupId: String? = null,
    val access: GroupsNavigationAccess = GroupsNavigationAccess(),
    val gameId: String? = null,
    val memberships: List<SessionMembershipDto> = emptyList(),
    val requestedGroupId: String? = null,
)

sealed interface GroupsNavigationIntent {
    data class Reconcile(
        val selection: GroupSelectionState,
        val memberships: List<SessionMembershipDto>,
    ) : GroupsNavigationIntent
    data class OpenGroup(val groupId: String) : GroupsNavigationIntent
    data object OpenGroups : GroupsNavigationIntent
    data object OpenHome : GroupsNavigationIntent
    data object OpenProfileCompletion : GroupsNavigationIntent
    data object OpenPeople : GroupsNavigationIntent
    data object OpenGames : GroupsNavigationIntent
    data class OpenGameDetail(val gameId: String) : GroupsNavigationIntent
    data object OpenFinance : GroupsNavigationIntent
}

sealed interface GroupsNavigationEffect {
    data class DestinationChanged(val destination: GroupsDestination, val groupId: String) : GroupsNavigationEffect
}

object GroupsNavigationTags {
    const val List = "groups-list"
    const val ListItemPrefix = "groups-list-item-"
    const val Home = "groups-home"
    const val Summary = "groups-summary"
    const val SummaryPhoto = "groups-summary-photo"
    const val SummaryPhotoSkeleton = "groups-summary-photo-skeleton"
    const val SummaryPhotoImage = "groups-summary-photo-image"
    const val SummaryPhotoFallback = "groups-summary-photo-fallback"
    const val NextGame = "groups-next-game"
    const val Shortcuts = "groups-shortcuts"
    const val ShortcutGames = "groups-shortcut-games"
    const val ShortcutPeople = "groups-shortcut-people"
    const val ShortcutFinance = "groups-shortcut-finance"
    const val ShortcutSettings = "groups-shortcut-settings"
    const val Notices = "groups-notices"
    const val Members = "groups-members"
    const val Invite = "groups-invite"
    const val ProfileCompletion = "groups-profile-completion"
    const val People = "groups-people"
    const val Games = "groups-games"
    const val GameDetail = "groups-game-detail"
    const val Finance = "groups-finance"
    const val OwnCharges = "groups-own-charges"
    const val BottomMenu = "groups-bottom-menu"
}

fun GroupsDestination.showsGroupChrome(): Boolean = when (this) {
    GroupsDestination.HOME,
    GroupsDestination.PROFILE_COMPLETION,
    GroupsDestination.PEOPLE,
    GroupsDestination.GAMES,
    GroupsDestination.GAME_DETAIL,
    GroupsDestination.FINANCE,
    GroupsDestination.OWN_CHARGES,
    -> true
    GroupsDestination.SETUP,
    GroupsDestination.SELECTOR,
    GroupsDestination.LOADING,
    GroupsDestination.LOAD_ERROR,
    -> false
}

fun GroupsDestination.bottomMenuDestination(): GroupsDestination = when (this) {
    GroupsDestination.GAME_DETAIL -> GroupsDestination.GAMES
    else -> this
}

fun GroupsDestination.isGroupScoped(): Boolean = when (this) {
    GroupsDestination.HOME,
    GroupsDestination.PROFILE_COMPLETION,
    GroupsDestination.PEOPLE,
    GroupsDestination.GAMES,
    GroupsDestination.GAME_DETAIL,
    GroupsDestination.FINANCE,
    GroupsDestination.OWN_CHARGES,
    -> true
    GroupsDestination.SETUP,
    GroupsDestination.SELECTOR,
    GroupsDestination.LOADING,
    GroupsDestination.LOAD_ERROR,
    -> false
}