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
    fun execute(actor: UUID, groupId: UUID, position: AthletePosition?): UpdateOwnAthleteProfileResult = execute(
        actor,
        groupId,
        UpdateOwnAthleteProfileCommand(
            groupId = groupId,
            userId = actor,
            nickname = null,
            position = position,
            secondaryPosition = null,
            level = null,
            preferredSide = null,
            heightCm = null,
        ),
    )

    fun execute(
        actor: UUID,
        groupId: UUID,
        command: UpdateOwnAthleteProfileCommand,
    ): UpdateOwnAthleteProfileResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction UpdateOwnAthleteProfileResult.GroupNotFound
        if (group.role == null) return@inTransaction UpdateOwnAthleteProfileResult.GroupNotFound
        val errors = AthleteAttributeValidator.validate(
            group.profile?.modality,
            command.nickname,
            command.position,
            command.secondaryPosition,
            command.level,
            command.preferredSide,
            command.heightCm,
            monthlyFeeCents = null,
            monthlyDueDay = null,
        )
        if (errors.isNotEmpty()) return@inTransaction UpdateOwnAthleteProfileResult.Invalid(errors)
        UpdateOwnAthleteProfileResult.Success(
            athleteRepository.updateOwn(command.copy(groupId = groupId, userId = actor)),
        )
    }
}
