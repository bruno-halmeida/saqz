package br.com.saqz.access.application.membership

import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.domain.GroupAccessDecision
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupAction
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.PersistedMembershipRole
import java.util.UUID

class ChangeMemberRole(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val membershipRepository: MembershipRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(
        actor: UUID,
        groupId: UUID,
        userId: UUID,
        role: PersistedMembershipRole,
    ): ChangeMemberRoleResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction ChangeMemberRoleResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ROLES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction ChangeMemberRoleResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction ChangeMemberRoleResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        val target = membershipRepository.find(groupId, userId)
            ?: return@inTransaction ChangeMemberRoleResult.GroupNotFound
        if (target.role == GroupRole.OWNER) return@inTransaction ChangeMemberRoleResult.OwnerImmutable
        val requestedRole = GroupRole.valueOf(role.name)
        if (target.role == requestedRole) return@inTransaction ChangeMemberRoleResult.Success(target)
        ChangeMemberRoleResult.Success(
            membershipRepository.change(ChangeMemberRoleCommand(groupId, userId, role)),
        )
    }
}
