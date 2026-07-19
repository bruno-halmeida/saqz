package br.com.saqz.groups.application.create

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupValidationError
import br.com.saqz.groups.domain.group.ValidGroupProfileDefaults
import java.util.UUID

enum class GroupProfileStatus {
    COMPLETE,
    INCOMPLETE,
}

data class CreateGroupCommand(
    val ownerUserId: UUID,
    val creationKey: UUID,
    val timeZone: IanaTimeZone,
    val profile: ValidGroupProfileDefaults,
)

data class StoredGroup(
    val id: UUID,
    val ownerUserId: UUID,
    val creationKey: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val version: Long,
    val profileStatus: GroupProfileStatus,
)

data class CreatedGroup(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val version: Long,
    val role: GroupRole,
    val profileStatus: GroupProfileStatus,
)

enum class CreateGroupField {
    TIME_ZONE,
}

sealed interface CreateGroupResult {
    data class Success(val group: CreatedGroup) : CreateGroupResult

    data class Invalid(val errors: List<GroupValidationError>) : CreateGroupResult
}
