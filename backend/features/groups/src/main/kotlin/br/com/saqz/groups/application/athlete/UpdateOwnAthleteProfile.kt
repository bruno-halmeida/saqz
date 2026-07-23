package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.AthletePosition
import java.util.UUID

class UpdateOwnAthleteProfile(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val athleteRepository: AthleteRepository,
) {
    fun execute(
        actor: UUID,
        groupId: UUID,
        position: AthletePosition?,
    ): UpdateOwnAthleteProfileResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction UpdateOwnAthleteProfileResult.GroupNotFound
        if (group.role == null) return@inTransaction UpdateOwnAthleteProfileResult.GroupNotFound
        UpdateOwnAthleteProfileResult.Success(athleteRepository.updatePosition(groupId, actor, position))
    }
}
