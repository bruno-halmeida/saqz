package br.com.saqz.access.application.membership

import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.domain.GroupAccessDecision
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupAction
import java.util.UUID

class ListAccessMemberships(
    private val groupReadRepository: GroupReadRepository,
    private val membershipRepository: MembershipRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID): ListAccessMembershipsResult {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return ListAccessMembershipsResult.GroupNotFound
        return when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ROLES)) {
            GroupAccessDecision.GroupNotFound -> ListAccessMembershipsResult.GroupNotFound
            GroupAccessDecision.Forbidden -> ListAccessMembershipsResult.AccessForbidden
            GroupAccessDecision.Allowed -> ListAccessMembershipsResult.Success(membershipRepository.list(groupId))
        }
    }
}
