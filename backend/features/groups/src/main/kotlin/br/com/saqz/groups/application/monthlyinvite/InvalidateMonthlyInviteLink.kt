package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.time.Clock
import java.util.UUID

sealed interface InvalidateMonthlyInviteLinkResult {
    data object Success : InvalidateMonthlyInviteLinkResult

    data object GroupNotFound : InvalidateMonthlyInviteLinkResult

    data object AccessForbidden : InvalidateMonthlyInviteLinkResult
}

/**
 * "Invalidar link" (CONV-011): o código deixa de valer na hora; as linhas de convidados não mudam e o convite
 * público não é tocado. Sem link vivo, não faz nada.
 */
class InvalidateMonthlyInviteLink(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val repository: MonthlyInviteLinkRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val clock: Clock,
) {
    fun execute(actor: UUID, groupId: UUID): InvalidateMonthlyInviteLinkResult = transactionRunner.inTransaction {
        val group = readRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction InvalidateMonthlyInviteLinkResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_INVITE)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction InvalidateMonthlyInviteLinkResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction InvalidateMonthlyInviteLinkResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        val current = repository.lockAndFind(groupId)
        if (current?.shortCode != null) repository.invalidate(groupId, clock.instant())
        InvalidateMonthlyInviteLinkResult.Success
    }
}
