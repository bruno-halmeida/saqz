package br.com.saqz.access.application.group.read

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import java.util.UUID

data class GroupReadKey(
    val actorUserId: UUID,
    val groupId: UUID,
)

data class GroupReadSnapshot(
    val id: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val role: GroupRole?,
    val version: Long,
)

data class GroupView(
    val id: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val role: GroupRole,
    val version: Long,
)

sealed interface GetGroupResult {
    data class Success(val group: GroupView) : GetGroupResult

    data object GroupNotFound : GetGroupResult

    data object AccessForbidden : GetGroupResult
}
