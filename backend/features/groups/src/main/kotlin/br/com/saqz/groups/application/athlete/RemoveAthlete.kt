package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

class RemoveAthlete(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val athleteRepository: AthleteRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID, userId: UUID): RemoveAthleteResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction RemoveAthleteResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction RemoveAthleteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction RemoveAthleteResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        val target = athleteRepository.find(groupId, userId)
            ?: return@inTransaction RemoveAthleteResult.Success
        if (target.role == GroupRole.OWNER) return@inTransaction RemoveAthleteResult.OwnerImmutable
        athleteRepository.remove(groupId, userId)
        RemoveAthleteResult.Success
    }
}
