package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.athlete.AthleteAttributeValidator
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import java.util.UUID

class SaveEntryRequestAthleteProfile(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val entryRequestRepository: EntryRequestRepository,
) {
    fun execute(
        actor: UUID,
        groupId: UUID,
        command: SaveEntryRequestAthleteProfileCommand,
    ): SaveEntryRequestAthleteProfileResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction SaveEntryRequestAthleteProfileResult.GroupNotFound
        if (entryRequestRepository.find(groupId, actor) == null) {
            return@inTransaction SaveEntryRequestAthleteProfileResult.RequestNotFound
        }
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
        if (errors.isNotEmpty()) return@inTransaction SaveEntryRequestAthleteProfileResult.Invalid(errors)
        if (!entryRequestRepository.saveAthleteProfile(command.copy(groupId = groupId, userId = actor))) {
            return@inTransaction SaveEntryRequestAthleteProfileResult.RequestNotFound
        }
        SaveEntryRequestAthleteProfileResult.Success
    }
}
