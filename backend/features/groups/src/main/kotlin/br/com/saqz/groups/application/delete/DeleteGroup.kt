package br.com.saqz.groups.application.delete

import br.com.saqz.groups.application.create.TransactionRunner
import java.util.UUID

sealed interface DeleteGroupResult {
    data object Success : DeleteGroupResult

    data object GroupNotFound : DeleteGroupResult

    data object AccessForbidden : DeleteGroupResult
}

interface GroupDeletionRepository {
    fun softDelete(actorUserId: UUID, groupId: UUID): DeleteGroupResult
}

class DeleteGroup(
    private val transactionRunner: TransactionRunner,
    private val repository: GroupDeletionRepository,
) {
    fun execute(actorUserId: UUID, groupId: UUID): DeleteGroupResult = transactionRunner.inTransaction {
        repository.softDelete(actorUserId, groupId)
    }
}
