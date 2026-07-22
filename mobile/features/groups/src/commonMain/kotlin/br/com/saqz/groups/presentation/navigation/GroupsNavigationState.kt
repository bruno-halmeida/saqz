package br.com.saqz.groups.presentation.navigation

import androidx.compose.runtime.Immutable
import br.com.saqz.network.SessionMembershipDto

@Immutable
data class GroupsNavigationState(
    val destination: GroupsDestination = GroupsDestination.SETUP,
    val groupId: String? = null,
    val access: GroupsNavigationAccess = GroupsNavigationAccess(),
    val gameId: String? = null,
    val memberships: List<SessionMembershipDto> = emptyList(),
    val requestedGroupId: String? = null,
)
