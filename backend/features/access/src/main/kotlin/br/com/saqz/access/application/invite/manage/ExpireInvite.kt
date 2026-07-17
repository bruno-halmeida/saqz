package br.com.saqz.access.application.invite.manage

import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.domain.GroupAccessDecision
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupAction
import java.util.UUID

class ExpireInvite(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val inviteRepository: InviteManagementRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID): ExpireInviteResult = transactionRunner.inTransaction {
        val group = readRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction ExpireInviteResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_INVITE)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction ExpireInviteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction ExpireInviteResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }

        inviteRepository.expire(groupId)
        ExpireInviteResult.Success
    }
}
