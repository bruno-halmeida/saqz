package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRoleDto

enum class GroupFinanceVisibility { ORGANIZER, OWN_CHARGES }

enum class GroupRouteAction { COMPLETE_PROFILE, PEOPLE, GAMES, FINANCE }

data class GroupRouteAccess(
    val peopleVisible: Boolean,
    val gamesVisible: Boolean,
    val financeVisibility: GroupFinanceVisibility,
    val profileCompletionVisible: Boolean,
    val operationsMutable: Boolean,
    val semanticActions: List<GroupRouteAction>,
)

object GroupRoutePolicy {
    fun evaluate(role: GroupRoleDto, profileStatus: GroupProfileStatusDto): GroupRouteAccess {
        val organizer = role == GroupRoleDto.OWNER || role == GroupRoleDto.ADMIN
        val complete = profileStatus == GroupProfileStatusDto.COMPLETE
        return GroupRouteAccess(
            peopleVisible = organizer,
            gamesVisible = true,
            financeVisibility = if (organizer) GroupFinanceVisibility.ORGANIZER else GroupFinanceVisibility.OWN_CHARGES,
            profileCompletionVisible = organizer && !complete,
            operationsMutable = organizer && complete,
            semanticActions = buildList {
                if (organizer && !complete) add(GroupRouteAction.COMPLETE_PROFILE)
                if (organizer) add(GroupRouteAction.PEOPLE)
                add(GroupRouteAction.GAMES)
                add(GroupRouteAction.FINANCE)
            },
        )
    }

    fun canRenderPrivateData(boundGroupId: String?, selectedGroupId: String?): Boolean =
        boundGroupId != null && boundGroupId == selectedGroupId
}
