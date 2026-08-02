package br.com.saqz.access.application.session

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteAccountTest {
    private val userId = UUID.randomUUID()

    @Test
    fun `deleting an active account runs group cleanup inside the transaction`() {
        val repository = RecordingRepository(userId)
        val cleanup = RecordingCleanup(repository.events)
        val transaction = RecordingTransactionRunner(repository.events)
        val useCase = DeleteAccount(transaction, repository, cleanup)

        useCase.execute("firebase-user")

        assertEquals(listOf("begin", "softDelete", "deleteOwnedGroups", "removeMemberships", "commit"), repository.events)
        assertEquals(listOf(userId), cleanup.deletedOwners)
        assertEquals(listOf(userId), cleanup.removedUsers)
    }

    @Test
    fun `repeating deletion is a no-op for an account already deleted`() {
        val repository = RecordingRepository(null)
        val cleanup = RecordingCleanup(repository.events)
        val transaction = RecordingTransactionRunner(repository.events)
        val useCase = DeleteAccount(transaction, repository, cleanup)

        useCase.execute("firebase-user")

        assertTrue(cleanup.deletedOwners.isEmpty())
        assertTrue(cleanup.removedUsers.isEmpty())
        assertEquals(listOf("begin", "softDelete", "commit"), repository.events)
    }

    private class RecordingTransactionRunner(
        private val events: MutableList<String>,
    ) : AccountTransactionRunner {
        override fun <T> inTransaction(block: () -> T): T {
            events += "begin"
            return block().also { events += "commit" }
        }
    }

    private class RecordingRepository(private val result: UUID?) : AccountDeletionRepository {
        val events = mutableListOf<String>()

        override fun softDelete(subject: String): UUID? {
            events += "softDelete"
            return result
        }
    }

    private class RecordingCleanup(
        private val events: MutableList<String>,
    ) : AccountGroupCleanup {
        val deletedOwners = mutableListOf<UUID>()
        val removedUsers = mutableListOf<UUID>()

        override fun deleteOwnedGroups(ownerUserId: UUID) {
            events += "deleteOwnedGroups"
            deletedOwners += ownerUserId
        }

        override fun removeMemberships(userId: UUID) {
            events += "removeMemberships"
            removedUsers += userId
        }
    }
}
