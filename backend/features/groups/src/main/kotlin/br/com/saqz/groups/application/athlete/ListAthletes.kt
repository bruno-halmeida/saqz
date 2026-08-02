package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

data class ListAthletesSuccess(
    val athletes: List<AthleteRosterEntry>,
    val role: GroupRole,
) : ListAthletesResult

class ListAthletes(
    private val groupReadRepository: GroupReadRepository,
    private val rosterRepository: AthleteRosterRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID, filter: AthleteRosterFilter): ListAthletesResult {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return ListAthletesResult.GroupNotFound

        val role = group.role ?: return ListAthletesResult.GroupNotFound
        if (role == GroupRole.ATHLETE && filter.financialStatus != null) {
            return ListAthletesResult.AccessForbidden
        }

        return when (accessPolicy.authorize(role, GroupAction.READ_GROUP)) {
            GroupAccessDecision.GroupNotFound -> ListAthletesResult.GroupNotFound
            GroupAccessDecision.Forbidden -> ListAthletesResult.AccessForbidden
            GroupAccessDecision.Allowed -> ListAthletesSuccess(rosterRepository.list(actor, groupId, filter), role)
        }
    }
}
