package br.com.saqz.access.application.group.create

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import java.util.UUID

data class CreateGroupCommand(
    val ownerUserId: UUID,
    val creationKey: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
)

data class StoredGroup(
    val id: UUID,
    val ownerUserId: UUID,
    val creationKey: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val version: Long,
)

data class CreatedGroup(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val version: Long,
    val role: GroupRole,
)

enum class CreateGroupField {
    NAME,
    TIME_ZONE,
}

sealed interface CreateGroupResult {
    data class Success(val group: CreatedGroup) : CreateGroupResult

    data class Invalid(val fields: Set<CreateGroupField>) : CreateGroupResult
}
