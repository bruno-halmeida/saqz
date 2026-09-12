package br.com.saqz.groups.application.membership

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.PersistedMembershipRole
import br.com.saqz.sharedkernel.group.GroupAdministrationRevocation
import java.util.UUID

class ChangeMemberRole(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val membershipRepository: MembershipRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val administrationRevocation: GroupAdministrationRevocation = GroupAdministrationRevocation { _, _ -> },
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
        val changed = membershipRepository.change(ChangeMemberRoleCommand(groupId, userId, role))
        if (target.role == GroupRole.ADMIN) administrationRevocation.revoked(groupId, userId)
        ChangeMemberRoleResult.Success(changed)
    }
}
