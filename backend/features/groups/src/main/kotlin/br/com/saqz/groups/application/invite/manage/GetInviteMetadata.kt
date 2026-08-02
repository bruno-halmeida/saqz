package br.com.saqz.groups.application.invite.manage

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.time.Clock
import java.util.UUID

class GetInviteMetadata(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val inviteRepository: InviteManagementRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val clock: Clock,
) {
    fun execute(actor: UUID, groupId: UUID): GetInviteMetadataResult = transactionRunner.inTransaction {
        val group = readRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction GetInviteMetadataResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_INVITE)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction GetInviteMetadataResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction GetInviteMetadataResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }

        val metadata = inviteRepository.findMetadata(groupId)
        GetInviteMetadataResult.Success(metadata.toView(clock.instant()))
    }

    private fun InviteMetadata?.toView(now: java.time.Instant): InviteMetadataView {
        if (this == null) return InviteMetadataView(false, null, null, null)
        if (now.isAfter(expiresAt)) return InviteMetadataView(false, expiresAt, null, null)
        return InviteMetadataView(true, expiresAt, createdAt, createdByName)
    }
}
