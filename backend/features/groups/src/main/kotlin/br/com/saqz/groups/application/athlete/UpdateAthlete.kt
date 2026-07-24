package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.util.UUID

class UpdateAthlete(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val athleteRepository: AthleteRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, command: UpdateAthleteCommand): UpdateAthleteResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, command.groupId))
            ?: return@inTransaction UpdateAthleteResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction UpdateAthleteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction UpdateAthleteResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        athleteRepository.find(command.groupId, command.userId)
            ?: return@inTransaction UpdateAthleteResult.GroupNotFound
        UpdateAthleteResult.Success(athleteRepository.update(command))
    }
}
