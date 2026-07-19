package br.com.saqz.groups.application.membership

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
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
