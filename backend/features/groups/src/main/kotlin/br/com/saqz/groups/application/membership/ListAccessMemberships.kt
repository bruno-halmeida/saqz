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
        // Papéis na lista são leitura de gestão de elenco (dono e admin), não promoção.
        // Trocar o papel em si continua em MANAGE_ROLES, só o dono.
        return when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> ListAccessMembershipsResult.GroupNotFound
            GroupAccessDecision.Forbidden -> ListAccessMembershipsResult.AccessForbidden
            GroupAccessDecision.Allowed -> ListAccessMembershipsResult.Success(membershipRepository.list(groupId))
        }
    }
}
