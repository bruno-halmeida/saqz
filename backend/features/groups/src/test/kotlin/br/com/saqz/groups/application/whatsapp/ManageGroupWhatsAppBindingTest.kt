package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ManageGroupWhatsAppBindingTest {
    @Test
    fun `owner reads an active binding`() {
        val fixture = fixture(GroupRole.OWNER, binding = binding())

        val result = assertIs<ManageGroupWhatsAppBindingResult.Bound>(fixture.useCase.get(actor, groupId))

        assertEquals(WHA_GROUP_JID, result.binding.whatsappJid)
        assertEquals(GroupWhatsAppBindingStatus.ACTIVE, result.binding.status())
    }

    @Test
    fun `admin reads a broken binding preserving the status`() {
        val fixture = fixture(GroupRole.ADMIN, binding = binding().copy(brokenAt = Instant.parse("2026-09-16T12:00:00Z")))

        val result = assertIs<ManageGroupWhatsAppBindingResult.Bound>(fixture.useCase.get(actor, groupId))

        assertEquals(GroupWhatsAppBindingStatus.BROKEN, result.binding.status())
    }

    @Test
    fun `number removed from the whatsapp group breaks the binding as soon as it is read`() {
        val fixture = fixture(GroupRole.OWNER, binding = binding(), directory = FakeDirectory(member = false))

        val result = assertIs<ManageGroupWhatsAppBindingResult.Bound>(fixture.useCase.get(actor, groupId))

        assertEquals(GroupWhatsAppBindingStatus.BROKEN, result.binding.status())
        assertEquals(listOf(groupId), fixture.bindings.broken)
    }

    @Test
    fun `whatsapp group that no longer exists also breaks on read`() {
        val fixture = fixture(GroupRole.OWNER, binding = binding(), directory = FakeDirectory(failure = DirectoryError.NotInGroup))

        val result = assertIs<ManageGroupWhatsAppBindingResult.Bound>(fixture.useCase.get(actor, groupId))

        assertEquals(GroupWhatsAppBindingStatus.BROKEN, result.binding.status())
        assertEquals(listOf(groupId), fixture.bindings.broken)
    }

    @Test
    fun `provider down or disconnected keeps what is stored`() {
        for (failure in listOf(DirectoryError.Disconnected, DirectoryError.Unavailable("timeout"))) {
            val fixture = fixture(GroupRole.OWNER, binding = binding(), directory = FakeDirectory(failure = failure))

            val result = assertIs<ManageGroupWhatsAppBindingResult.Bound>(fixture.useCase.get(actor, groupId))

            assertEquals(GroupWhatsAppBindingStatus.ACTIVE, result.binding.status())
            assertTrue(fixture.bindings.broken.isEmpty())
        }
    }

    @Test
    fun `a binding already broken is not checked again`() {
        val directory = FakeDirectory(member = true)
        val fixture = fixture(
            GroupRole.OWNER,
            binding = binding().copy(brokenAt = Instant.parse("2026-09-16T12:00:00Z")),
            directory = directory,
        )

        fixture.useCase.get(actor, groupId)

        assertEquals(0, directory.checks)
    }

    @Test
    fun `group without binding reports not bound`() {
        assertSame(ManageGroupWhatsAppBindingResult.NotBound, fixture(GroupRole.OWNER, binding = null).useCase.get(actor, groupId))
    }

    @Test
    fun `athlete cannot read or change the binding`() {
        val fixture = fixture(GroupRole.ATHLETE, binding = binding())

        assertSame(ManageGroupWhatsAppBindingResult.AccessForbidden, fixture.useCase.get(actor, groupId))
        assertSame(ManageGroupWhatsAppBindingResult.AccessForbidden, fixture.useCase.setEnabled(actor, groupId, false))
        assertTrue(fixture.bindings.enabled.isEmpty())
    }

    @Test
    fun `missing group cannot be read or changed`() {
        val fixture = fixture(GroupRole.OWNER, binding = binding(), groupExists = false)

        assertSame(ManageGroupWhatsAppBindingResult.GroupNotFound, fixture.useCase.get(actor, groupId))
        assertSame(ManageGroupWhatsAppBindingResult.GroupNotFound, fixture.useCase.setEnabled(actor, groupId, true))
    }

    @Test
    fun `owner disables preserving the binding`() {
        val fixture = fixture(GroupRole.OWNER, binding = binding())

        val result = assertIs<ManageGroupWhatsAppBindingResult.Bound>(fixture.useCase.setEnabled(actor, groupId, false))

        assertEquals(false, result.binding.enabled)
        assertEquals(GroupWhatsAppBindingStatus.DISABLED, result.binding.status())
        assertEquals(listOf(groupId to false), fixture.bindings.enabled)
        assertEquals(WHA_GROUP_JID, result.binding.whatsappJid)
    }

    @Test
    fun `owner re-enables the same binding without joining again`() {
        val fixture = fixture(GroupRole.OWNER, binding = binding().copy(enabled = false))

        val result = assertIs<ManageGroupWhatsAppBindingResult.Bound>(fixture.useCase.setEnabled(actor, groupId, true))

        assertEquals(true, result.binding.enabled)
        assertEquals(GroupWhatsAppBindingStatus.ACTIVE, result.binding.status())
        assertEquals(listOf(groupId to true), fixture.bindings.enabled)
    }

    @Test
    fun `changing a missing binding is not bound`() {
        assertSame(
            ManageGroupWhatsAppBindingResult.NotBound,
            fixture(GroupRole.OWNER, binding = null).useCase.setEnabled(actor, groupId, true),
        )
    }

    private fun fixture(
        role: GroupRole?,
        binding: GroupWhatsAppBinding?,
        groupExists: Boolean = true,
        directory: WhatsAppGroupDirectory = FakeDirectory(member = true),
    ): Fixture {
        val bindings = RecordingBindings(binding)
        val read = FixedGroupReadRepository(role, groupExists)
        return Fixture(ManageGroupWhatsAppBinding(read, bindings, directory), bindings)
    }

    /** O WhatsApp como a leitura enxerga: o número está no grupo, saiu, ou o provedor falhou. */
    private class FakeDirectory(
        private val member: Boolean = true,
        private val failure: DirectoryError? = null,
    ) : WhatsAppGroupDirectory {
        var checks = 0

        override fun instanceStatus(): String = "551153040175"

        override fun inviteInfo(inviteCode: String): WhatsAppGroupInfo = error("unused")

        override fun join(inviteCode: String) = error("unused")

        override fun groupInfo(jid: String): WhatsAppGroupInfo = error("unused")

        override fun isMember(jid: String): Boolean {
            checks++
            failure?.let { throw it }
            return member
        }
    }

    private fun binding() = GroupWhatsAppBinding(
        groupId = groupId,
        whatsappJid = WHA_GROUP_JID,
        inviteCode = "Invite1234",
        groupName = "Vôlei do CERET",
        instanceJid = "551153040175",
        enabled = true,
        brokenAt = null,
        createdBy = actor,
    )

    private data class Fixture(
        val useCase: ManageGroupWhatsAppBinding,
        val bindings: RecordingBindings,
    )

    private class FixedGroupReadRepository(
        private val role: GroupRole?,
        private val groupExists: Boolean,
    ) : GroupReadRepository {
        override fun find(key: GroupReadKey): GroupReadSnapshot? = if (groupExists) {
            GroupReadSnapshot(groupId, AccessName.from("Training Group"), IanaTimeZone.from("UTC"), role, 1)
        } else {
            null
        }
    }

    private class RecordingBindings(private val binding: GroupWhatsAppBinding?) : GroupWhatsAppBindingRepository {
        val enabled = mutableListOf<Pair<UUID, Boolean>>()
        val broken = mutableListOf<UUID>()

        override fun find(groupId: UUID): GroupWhatsAppBinding? = binding

        override fun findByJid(whatsappJid: String): GroupWhatsAppBinding? = binding

        override fun upsert(binding: GroupWhatsAppBinding) = Unit

        override fun cancelPendingByGroup(groupId: UUID): Int = 0

        override fun setEnabled(groupId: UUID, enabled: Boolean) {
            this.enabled += groupId to enabled
        }

        override fun markBroken(groupId: UUID) {
            broken += groupId
        }

        override fun memberPhones(groupId: UUID): List<String> = emptyList()
    }

    private companion object {
        val actor: UUID = UUID.randomUUID()
        val groupId: UUID = UUID.randomUUID()
        const val WHA_GROUP_JID = "120363000000000000@g.us"
    }
}
