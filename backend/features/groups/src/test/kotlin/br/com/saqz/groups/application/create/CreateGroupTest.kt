package br.com.saqz.groups.application.create

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CreateGroupTest {
    private val actorId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val stored = StoredGroup(
        id = groupId,
        ownerUserId = actorId,
        creationKey = requestId,
        name = AccessName.from("Training Club"),
        timeZone = IanaTimeZone.from("America/Sao_Paulo"),
        version = 1,
    )

    @Test
    fun `valid input returns the created group with synthesized owner role`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actorId, requestId, "Training Club", "America/Sao_Paulo")

        assertEquals(
            CreateGroupResult.Success(
                CreatedGroup(groupId, "Training Club", "America/Sao_Paulo", 1, GroupRole.OWNER),
            ),
            result,
        )
    }

    @Test
    fun `actor is persisted as the sole owner with the request id creation key`() {
        val fixture = fixture()

        fixture.useCase.execute(actorId, requestId, "Training Club", "America/Sao_Paulo")

        assertEquals(actorId, fixture.repository.commands.single().ownerUserId)
        assertEquals(requestId, fixture.repository.commands.single().creationKey)
    }

    @Test
    fun `name is normalized before the transaction begins`() {
        val fixture = fixture(stored = stored.copy(name = AccessName.from("Training Club")))

        fixture.useCase.execute(actorId, requestId, "  Training Club  ", "America/Sao_Paulo")

        assertEquals("Training Club", fixture.repository.commands.single().name.value)
    }

    @Test
    fun `repository command contains no owner membership row`() {
        val fixture = fixture()

        fixture.useCase.execute(actorId, requestId, "Training Club", "America/Sao_Paulo")

        assertEquals(
            CreateGroupCommand(
                ownerUserId = actorId,
                creationKey = requestId,
                name = AccessName.from("Training Club"),
                timeZone = IanaTimeZone.from("America/Sao_Paulo"),
            ),
            fixture.repository.commands.single(),
        )
    }

    @Test
    fun `retry with the same request id returns the repository group unchanged`() {
        val fixture = fixture()

        val first = fixture.useCase.execute(actorId, requestId, "Training Club", "America/Sao_Paulo")
        val second = fixture.useCase.execute(actorId, requestId, "Changed Name", "Europe/Lisbon")

        assertEquals(first, second)
        assertEquals(listOf(requestId, requestId), fixture.repository.commands.map { it.creationKey })
    }

    @Test
    fun `a user with memberships elsewhere can create a group`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actorId, requestId, "Training Club", "America/Sao_Paulo")

        assertTrue(result is CreateGroupResult.Success)
        assertEquals(1, fixture.transaction.calls)
    }

    @Test
    fun `invalid name returns field error without opening transaction`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actorId, requestId, " ", "America/Sao_Paulo")

        assertEquals(CreateGroupResult.Invalid(setOf(CreateGroupField.NAME)), result)
        assertEquals(0, fixture.transaction.calls)
        assertTrue(fixture.repository.commands.isEmpty())
    }

    @Test
    fun `invalid timezone returns field error without opening transaction`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actorId, requestId, "Training Club", "Mars/Olympus")

        assertEquals(CreateGroupResult.Invalid(setOf(CreateGroupField.TIME_ZONE)), result)
        assertEquals(0, fixture.transaction.calls)
        assertTrue(fixture.repository.commands.isEmpty())
    }

    @Test
    fun `all invalid fields are reported together without opening transaction`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actorId, requestId, "\n", "")

        assertEquals(
            CreateGroupResult.Invalid(setOf(CreateGroupField.NAME, CreateGroupField.TIME_ZONE)),
            result,
        )
        assertEquals(0, fixture.transaction.calls)
    }

    @Test
    fun `repository failure escapes the transaction for rollback`() {
        val failure = IllegalStateException("write failed")
        val transaction = RecordingTransactionRunner()
        val repository = RecordingGroupCreationRepository(stored, failure)
        val useCase = CreateGroup(transaction, repository)

        val thrown = assertFailsWith<IllegalStateException> {
            useCase.execute(actorId, requestId, "Training Club", "America/Sao_Paulo")
        }

        assertSame(failure, thrown)
        assertEquals(1, transaction.calls)
        assertEquals(1, transaction.rollbacks)
        assertEquals(1, repository.commands.size)
    }

    private fun fixture(stored: StoredGroup = this.stored): Fixture {
        val transaction = RecordingTransactionRunner()
        val repository = RecordingGroupCreationRepository(stored)
        return Fixture(CreateGroup(transaction, repository), transaction, repository)
    }

    private data class Fixture(
        val useCase: CreateGroup,
        val transaction: RecordingTransactionRunner,
        val repository: RecordingGroupCreationRepository,
    )

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0
        var rollbacks = 0

        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return try {
                block()
            } catch (failure: Throwable) {
                rollbacks += 1
                throw failure
            }
        }
    }

    private class RecordingGroupCreationRepository(
        private val stored: StoredGroup,
        private val failure: Throwable? = null,
    ) : GroupCreationRepository {
        val commands = mutableListOf<CreateGroupCommand>()

        override fun create(command: CreateGroupCommand): StoredGroup {
            commands += command
            failure?.let { throw it }
            return stored
        }
    }
}
