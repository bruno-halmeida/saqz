package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.util.UUID

class ListEntryRequests(
    private val groupReadRepository: GroupReadRepository,
    private val entryRequestRepository: EntryRequestRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID): ListEntryRequestsResult {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return ListEntryRequestsResult.GroupNotFound
        return when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> ListEntryRequestsResult.GroupNotFound
            GroupAccessDecision.Forbidden -> ListEntryRequestsResult.AccessForbidden
            GroupAccessDecision.Allowed -> ListEntryRequestsResult.Success(entryRequestRepository.list(groupId))
        }
    }
}
