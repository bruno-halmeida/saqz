package br.com.saqz.groups.application.delete

import br.com.saqz.groups.application.create.TransactionRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class DeleteGroupTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private lateinit var repository: RecordingRepository
    private lateinit var deleteGroup: DeleteGroup

    @BeforeEach
    fun setUp() {
        repository = RecordingRepository()
        deleteGroup = DeleteGroup(
            transactionRunner = DirectTransactionRunner,
            repository = repository,
        )
    }

    @Test
    fun `owner deletes active group`() {
        assertEquals(DeleteGroupResult.Success, deleteGroup.execute(actor, group))
        assertEquals(actor to group, repository.lastRequest)
    }

    @Test
    fun `non-owner receives forbidden result`() {
        repository.result = DeleteGroupResult.AccessForbidden

        assertEquals(DeleteGroupResult.AccessForbidden, deleteGroup.execute(actor, group))
    }

    @Test
    fun `missing or already deleted group receives not found result`() {
        repository.result = DeleteGroupResult.GroupNotFound

        assertEquals(DeleteGroupResult.GroupNotFound, deleteGroup.execute(actor, group))
    }

    private object DirectTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class RecordingRepository : GroupDeletionRepository {
        var result: DeleteGroupResult = DeleteGroupResult.Success
        var lastRequest: Pair<UUID, UUID>? = null

        override fun softDelete(actorUserId: UUID, groupId: UUID): DeleteGroupResult {
            lastRequest = actorUserId to groupId
            return result
        }
    }
}
