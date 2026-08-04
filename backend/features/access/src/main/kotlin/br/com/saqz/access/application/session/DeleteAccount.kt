package br.com.saqz.access.application.session

import java.util.UUID

interface AccountDeletionRepository {
    /** Returns the active account id, or null when there is nothing left to delete. */
    fun softDelete(subject: String): UUID?

    /** Instante da suspensão de plataforma, ou null quando a conta pode agir. */
    fun suspendedAt(subject: String): java.time.Instant? = null
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
    /** false = conta suspensa; nada foi apagado. */
    fun execute(subject: String): Boolean = transactionRunner.inTransaction {
        // Suspenso não apaga a conta nem demole os grupos que organiza.
        if (repository.suspendedAt(subject) != null) {
            return@inTransaction false
        }
        repository.softDelete(subject)?.let { userId ->
            groupCleanup.deleteOwnedGroups(userId)
            groupCleanup.removeMemberships(userId)
        }
        true
    }
}
