package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.util.UUID

class ListAthletes(
    private val groupReadRepository: GroupReadRepository,
    private val rosterRepository: AthleteRosterRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID, filter: AthleteRosterFilter): ListAthletesResult {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return ListAthletesResult.GroupNotFound
        return when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> ListAthletesResult.GroupNotFound
            GroupAccessDecision.Forbidden -> ListAthletesResult.AccessForbidden
            GroupAccessDecision.Allowed -> ListAthletesResult.Success(rosterRepository.list(groupId, filter))
        }
    }
}
