package br.com.saqz.access.application.group.create

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import java.util.UUID

class CreateGroup(
    private val transactionRunner: TransactionRunner,
    private val repository: GroupCreationRepository,
) {
    fun execute(
        actor: UUID,
        requestId: UUID,
        name: String,
        timeZone: String,
    ): CreateGroupResult {
        val validName = runCatching { AccessName.from(name) }.getOrNull()
        val validTimeZone = runCatching { IanaTimeZone.from(timeZone) }.getOrNull()
        val invalidFields = buildSet {
            if (validName == null) add(CreateGroupField.NAME)
            if (validTimeZone == null) add(CreateGroupField.TIME_ZONE)
        }
        if (invalidFields.isNotEmpty()) return CreateGroupResult.Invalid(invalidFields)

        val stored = transactionRunner.inTransaction {
            repository.create(
                CreateGroupCommand(
                    ownerUserId = actor,
                    creationKey = requestId,
                    name = requireNotNull(validName),
                    timeZone = requireNotNull(validTimeZone),
                ),
            )
        }
        return CreateGroupResult.Success(
            CreatedGroup(
                id = stored.id,
                name = stored.name.value,
                timeZone = stored.timeZone.value,
                version = stored.version,
                role = GroupRole.OWNER,
            ),
        )
    }
}
