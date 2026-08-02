package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import java.util.UUID

class RejectEntryRequest(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val entryRequestRepository: EntryRequestRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID, userId: UUID): RejectEntryRequestResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction RejectEntryRequestResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction RejectEntryRequestResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction RejectEntryRequestResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        entryRequestRepository.delete(groupId, userId)
        RejectEntryRequestResult.Success
    }
}
