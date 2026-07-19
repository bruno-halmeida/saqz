package br.com.saqz.groups.application.invite.manage

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.util.UUID

class RotateInvite(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val inviteRepository: InviteManagementRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val tokenGenerator: SecureTokenGenerator,
    private val linkFactory: InviteLinkFactory,
) {
    fun execute(actor: UUID, groupId: UUID): RotateInviteResult = transactionRunner.inTransaction {
        val group = readRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction RotateInviteResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_INVITE)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction RotateInviteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction RotateInviteResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }

        val token = tokenGenerator.generate()
        val inviteUrl = linkFactory.create(token.code)
        inviteRepository.rotate(RotateInviteCommand(groupId, token.digest, actor))
        RotateInviteResult.Success(inviteUrl)
    }
}
