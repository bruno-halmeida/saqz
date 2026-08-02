package br.com.saqz.access.application.session

import java.util.UUID

interface AccountDeletionRepository {
    /** Returns the active account id, or null when there is nothing left to delete. */
    fun softDelete(subject: String): UUID?
}

interface AccountGroupCleanup {
    fun deleteOwnedGroups(ownerUserId: UUID)

    fun removeMemberships(userId: UUID)
}

interface AccountTransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}

class DeleteAccount(
    private val transactionRunner: AccountTransactionRunner,
    private val repository: AccountDeletionRepository,
    private val groupCleanup: AccountGroupCleanup,
) {
    fun execute(subject: String) = transactionRunner.inTransaction {
        repository.softDelete(subject)?.let { userId ->
            groupCleanup.deleteOwnedGroups(userId)
            groupCleanup.removeMemberships(userId)
        }
    }
}
