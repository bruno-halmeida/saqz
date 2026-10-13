package br.com.saqz.groups.application.invite.manage

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Lê o convite do grupo para qualquer admin, em qualquer aparelho: devolve o código curto e a URL.
 * Grupo novo (sem convite) ou antigo (só com o token longo) ganha o código curto aqui, na primeira
 * leitura, sem invalidar o token longo já enviado.
 */
class GetInviteMetadata(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val inviteRepository: InviteManagementRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val clock: Clock,
    private val tokenGenerator: SecureTokenGenerator,
    private val linkFactory: InviteLinkFactory,
) {
    fun execute(actor: UUID, groupId: UUID): GetInviteMetadataResult = transactionRunner.inTransaction {
        val group = readRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction GetInviteMetadataResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_INVITE)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction GetInviteMetadataResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction GetInviteMetadataResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }

        GetInviteMetadataResult.Success(ensured(actor, groupId, clock.instant()).toView())
    }

    private fun ensured(actor: UUID, groupId: UUID, now: Instant): InviteMetadata {
        val current = inviteRepository.findMetadata(groupId)
        if (current?.expiresAt?.let(now::isAfter) == true) {
            // Convite legado já vencido: não há link vivo a preservar, então nasce um novo, sem prazo.
            val code = newUniqueCode(tokenGenerator, inviteRepository)
            inviteRepository.rotate(RotateInviteCommand(groupId, code, actor, expiresAt = null))
        } else if (current?.shortCode == null) {
            val code = newUniqueCode(tokenGenerator, inviteRepository)
            inviteRepository.ensureShortCode(groupId, code.value, actor)
        } else {
            return current
        }
        return requireNotNull(inviteRepository.findMetadata(groupId))
    }

    private fun InviteMetadata.toView(): InviteMetadataView {
        val code = InviteCode.from(requireNotNull(shortCode))
        return InviteMetadataView(
            active = true,
            expiresAt = expiresAt,
            createdAt = createdAt,
            createdByName = createdByName,
            revision = revision,
            code = code.display,
            inviteUrl = linkFactory.create(code),
        )
    }
}
