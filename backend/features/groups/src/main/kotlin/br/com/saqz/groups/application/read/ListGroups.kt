package br.com.saqz.groups.application.read

import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupModality
import java.util.UUID

data class GroupSummaryReadModel(
    val id: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val role: GroupRole,
    val profileStatus: GroupProfileStatus,
    val modality: GroupModality?,
    val city: String?,
    val memberCount: Int,
)

interface GroupSummariesReadRepository {
    fun findAllFor(actorUserId: UUID): List<GroupSummaryReadModel>
}

class ListGroups(
    private val repository: GroupSummariesReadRepository,
) {
    fun execute(actor: UUID): List<GroupSummaryReadModel> = repository.findAllFor(actor)
}
