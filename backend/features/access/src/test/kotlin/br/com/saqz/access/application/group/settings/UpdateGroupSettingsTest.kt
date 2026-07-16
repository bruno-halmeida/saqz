package br.com.saqz.access.application.group.settings

import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.application.group.read.GroupReadSnapshot
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class UpdateGroupSettingsTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

    @Test
    fun `owner updates name and timezone atomically with exact expected version`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.useCase.execute(actor, groupId, 3, "  New Group  ", "Europe/Lisbon")

        assertEquals(UpdateGroupSettingsResult.Success(updated(GroupRole.OWNER)), result)
        assertEquals(
            listOf(UpdateGroupSettingsCommand(groupId, 3, AccessName.from("New Group"), IanaTimeZone.from("Europe/Lisbon"))),
            fixture.settings.commands,
        )
    }

    @Test
    fun `admin can update both settings`() {
        assertTrue(fixture(GroupRole.ADMIN).useCase.execute(actor, groupId, 3, "New Group", "Europe/Lisbon") is UpdateGroupSettingsResult.Success)
    }

    @Test
    fun `athlete is forbidden without a write`() {
        val fixture = fixture(GroupRole.ATHLETE)

        assertSame(UpdateGroupSettingsResult.AccessForbidden, fixture.useCase.execute(actor, groupId, 3, "New Group", "UTC"))
        assertTrue(fixture.settings.commands.isEmpty())
    }

    @Test
    fun `nonmember receives group not found without a write`() {
        val fixture = fixture(role = null)

        assertSame(UpdateGroupSettingsResult.GroupNotFound, fixture.useCase.execute(actor, groupId, 3, "New Group", "UTC"))
        assertTrue(fixture.settings.commands.isEmpty())
    }

    @Test
    fun `missing group and nonmember have the same result`() {
        val missing = fixture(role = null, exists = false).useCase.execute(actor, groupId, 3, "New Group", "UTC")
        val nonmember = fixture(role = null).useCase.execute(actor, groupId, 3, "New Group", "UTC")

        assertSame(UpdateGroupSettingsResult.GroupNotFound, missing)
        assertSame(missing, nonmember)
    }

    @Test
    fun `invalid name does not open a transaction`() {
        assertInvalid(" ", "UTC", setOf(UpdateGroupSettingsField.NAME))
    }

    @Test
    fun `invalid timezone does not open a transaction`() {
        assertInvalid("New Group", "Mars/Olympus", setOf(UpdateGroupSettingsField.TIME_ZONE))
    }

    @Test
    fun `all invalid fields are reported without opening a transaction`() {
        assertInvalid("\n", "", setOf(UpdateGroupSettingsField.NAME, UpdateGroupSettingsField.TIME_ZONE))
    }

    @Test
    fun `stale expected version returns conflict without a write`() {
        val fixture = fixture(GroupRole.OWNER, currentVersion = 4)

        assertSame(UpdateGroupSettingsResult.VersionConflict, fixture.useCase.execute(actor, groupId, 3, "New Group", "UTC"))
        assertTrue(fixture.settings.commands.isEmpty())
    }

    @Test
    fun `compare and set loss returns version conflict`() {
        val fixture = fixture(GroupRole.ADMIN, writeResult = SettingsWriteResult.VersionConflict)

        assertSame(UpdateGroupSettingsResult.VersionConflict, fixture.useCase.execute(actor, groupId, 3, "New Group", "UTC"))
        assertEquals(1, fixture.settings.commands.size)
    }

    @Test
    fun `write failure escapes transaction for rollback`() {
        val failure = IllegalStateException("write failed")
        val fixture = fixture(GroupRole.OWNER, failure = failure)

        assertSame(failure, assertFailsWith { fixture.useCase.execute(actor, groupId, 3, "New Group", "UTC") })
        assertEquals(1, fixture.transaction.rollbacks)
        assertEquals(1, fixture.settings.commands.size)
    }

    private fun assertInvalid(name: String, timeZone: String, fields: Set<UpdateGroupSettingsField>) {
        val fixture = fixture(GroupRole.OWNER)

        assertEquals(UpdateGroupSettingsResult.Invalid(fields), fixture.useCase.execute(actor, groupId, 3, name, timeZone))
        assertEquals(0, fixture.transaction.calls)
        assertTrue(fixture.settings.commands.isEmpty())
    }

    private fun fixture(
        role: GroupRole?,
        exists: Boolean = true,
        currentVersion: Long = 3,
        writeResult: SettingsWriteResult = SettingsWriteResult.Updated(stored()),
        failure: RuntimeException? = null,
    ): Fixture {
        val transaction = RecordingTransactionRunner()
        val read = RecordingReadRepository(if (exists) snapshot(role, currentVersion) else null)
        val settings = RecordingSettingsRepository(writeResult, failure)
        return Fixture(UpdateGroupSettings(transaction, read, settings, GroupAccessPolicy()), transaction, settings)
    }

    private fun snapshot(role: GroupRole?, version: Long) = GroupReadSnapshot(
        groupId, AccessName.from("Old Group"), IanaTimeZone.from("UTC"), role, version,
    )

    private fun stored() = StoredGroupSettings(
        groupId, AccessName.from("New Group"), IanaTimeZone.from("Europe/Lisbon"), 4,
    )

    private fun updated(role: GroupRole) = UpdatedGroupSettings(
        groupId, AccessName.from("New Group"), IanaTimeZone.from("Europe/Lisbon"), role, 4,
    )

    private data class Fixture(
        val useCase: UpdateGroupSettings,
        val transaction: RecordingTransactionRunner,
        val settings: RecordingSettingsRepository,
    )

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0
        var rollbacks = 0
        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return try { block() } catch (failure: Throwable) {
                rollbacks += 1
                throw failure
            }
        }
    }

    private class RecordingReadRepository(private val result: GroupReadSnapshot?) : GroupReadRepository {
        override fun find(key: GroupReadKey) = result
    }

    private class RecordingSettingsRepository(
        private val result: SettingsWriteResult,
        private val failure: RuntimeException?,
    ) : GroupSettingsRepository {
        val commands = mutableListOf<UpdateGroupSettingsCommand>()
        override fun update(command: UpdateGroupSettingsCommand): SettingsWriteResult {
            commands += command
            failure?.let { throw it }
            return result
        }
    }
}
