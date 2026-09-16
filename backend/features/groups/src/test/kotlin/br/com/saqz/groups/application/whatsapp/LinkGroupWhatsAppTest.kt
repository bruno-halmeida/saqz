package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LinkGroupWhatsAppTest {
    @Test
    fun `owner links a group enabling the channel with the instance phone in digits`() {
        val fixture = fixture()

        val result = assertIs<LinkGroupWhatsAppResult.Linked>(
            fixture.useCase.execute(actor, groupId, "https://chat.whatsapp.com/AbCdEf123456"),
        )

        assertEquals(WHA_GROUP_JID, result.binding.whatsappJid)
        assertEquals("AbCdEf123456", result.binding.inviteCode)
        assertEquals("Vôlei do CERET", result.binding.groupName)
        assertEquals(INSTANCE_PHONE, result.binding.instanceJid)
        assertEquals(true, result.binding.enabled)
        assertEquals(null, result.binding.brokenAt)
        assertEquals(groupId, result.binding.groupId)
        assertEquals(actor, result.binding.createdBy)
        assertEquals(GroupWhatsAppBindingStatus.ACTIVE, result.binding.status())
        assertEquals(listOf("AbCdEf123456"), fixture.directory.joins)
        assertEquals(listOf(WHA_GROUP_JID), fixture.directory.infos)
        assertEquals(listOf(result.binding), fixture.bindings.upserted)
    }

    @Test
    fun `admin links a pure invite code`() {
        val fixture = fixture(role = GroupRole.ADMIN)

        assertIs<LinkGroupWhatsAppResult.Linked>(fixture.useCase.execute(actor, groupId, "AbCdEf123456"))

        assertEquals(listOf("AbCdEf123456"), fixture.directory.joins)
    }

    @Test
    fun `athlete cannot link`() {
        val fixture = fixture(role = GroupRole.ATHLETE)

        assertSame(LinkGroupWhatsAppResult.AccessForbidden, fixture.useCase.execute(actor, groupId, "AbCdEf123456"))
        assertTrue(fixture.bindings.upserted.isEmpty())
        assertTrue(fixture.directory.joins.isEmpty())
    }

    @Test
    fun `missing group cannot link`() {
        val fixture = fixture(groupExists = false)

        assertSame(LinkGroupWhatsAppResult.GroupNotFound, fixture.useCase.execute(actor, groupId, "AbCdEf123456"))
        assertTrue(fixture.directory.joins.isEmpty())
    }

    @Test
    fun `unrecognizable link is rejected before touching the provider`() {
        val fixture = fixture()

        assertSame(LinkGroupWhatsAppResult.InvalidInvite, fixture.useCase.execute(actor, groupId, "not a link"))
        assertTrue(fixture.directory.joins.isEmpty())
        assertTrue(fixture.directory.infos.isEmpty())
    }

    @Test
    fun `invalid invite from the provider is reported`() {
        for (error in listOf<DirectoryError>(DirectoryError.InvalidInvite, DirectoryError.NotInGroup)) {
            val fixture = fixture()
            fixture.directory.inviteFailure = error

            assertSame(LinkGroupWhatsAppResult.InvalidInvite, fixture.useCase.execute(actor, groupId, "AbCdEf123456"))
            assertTrue(fixture.bindings.upserted.isEmpty())
        }
    }

    @Test
    fun `disconnected instance is reported`() {
        val fixture = fixture()
        fixture.directory.instanceFailure = DirectoryError.Disconnected

        assertSame(
            LinkGroupWhatsAppResult.InstanceDisconnected,
            fixture.useCase.execute(actor, groupId, "AbCdEf123456"),
        )
        assertTrue(fixture.directory.joins.isEmpty())
    }

    @Test
    fun `provider failure is reported as unavailable`() {
        val fixture = fixture()
        fixture.directory.inviteFailure = DirectoryError.Unavailable("timeout")

        assertSame(
            LinkGroupWhatsAppResult.ProviderUnavailable,
            fixture.useCase.execute(actor, groupId, "AbCdEf123456"),
        )
    }

    @Test
    fun `no whatsapp admin matches an active saqz member`() {
        val fixture = fixture(phones = listOf("5511900000000"))

        assertSame(
            LinkGroupWhatsAppResult.UnresolvableAdmins,
            fixture.useCase.execute(actor, groupId, "AbCdEf123456"),
        )
        assertTrue(fixture.directory.joins.isEmpty())
    }

    @Test
    fun `invite without an admin phone is rejected as unresolvable`() {
        val fixture = fixture()
        fixture.directory.invite = WhatsAppGroupInfo(WHA_GROUP_JID, "Vôlei do CERET", emptyList())

        assertSame(
            LinkGroupWhatsAppResult.UnresolvableAdmins,
            fixture.useCase.execute(actor, groupId, "AbCdEf123456"),
        )
    }

    @Test
    fun `a jid already bound to another saqz group is rejected`() {
        val fixture = fixture()
        fixture.bindings.byJid = binding(otherGroupId)

        assertSame(LinkGroupWhatsAppResult.JidInUse, fixture.useCase.execute(actor, groupId, "AbCdEf123456"))
        assertTrue(fixture.directory.joins.isEmpty())
    }

    @Test
    fun `relinking the same group is a normal upsert`() {
        val fixture = fixture()
        fixture.bindings.byJid = binding(groupId).copy(inviteCode = "OldInvite01", groupName = "Old name")

        val result = assertIs<LinkGroupWhatsAppResult.Linked>(
            fixture.useCase.execute(actor, groupId, "AbCdEf123456"),
        )

        assertEquals("AbCdEf123456", result.binding.inviteCode)
        assertEquals(listOf("AbCdEf123456"), fixture.directory.joins)
        assertEquals(listOf(result.binding), fixture.bindings.upserted)
    }

    @Test
    fun `join that does not confirm membership is a pending entry`() {
        val fixture = fixture()
        fixture.directory.member = false

        assertSame(LinkGroupWhatsAppResult.JoinPending, fixture.useCase.execute(actor, groupId, "AbCdEf123456"))
        assertTrue(fixture.bindings.upserted.isEmpty())
    }

    @Test
    fun `group no longer reachable after join is a pending entry`() {
        val fixture = fixture()
        fixture.directory.infoFailure = DirectoryError.NotInGroup

        assertSame(LinkGroupWhatsAppResult.JoinPending, fixture.useCase.execute(actor, groupId, "AbCdEf123456"))
        assertTrue(fixture.bindings.upserted.isEmpty())
    }

    @Test
    fun `join rejected by the provider is an invalid invite`() {
        val fixture = fixture()
        fixture.directory.joinFailure = DirectoryError.NotInGroup

        assertSame(LinkGroupWhatsAppResult.InvalidInvite, fixture.useCase.execute(actor, groupId, "AbCdEf123456"))
        assertTrue(fixture.bindings.upserted.isEmpty())
    }

    private fun fixture(
        role: GroupRole? = GroupRole.OWNER,
        groupExists: Boolean = true,
        phones: List<String> = listOf(ADMIN_PHONE),
    ): Fixture {
        val read = FixedGroupReadRepository(role, groupExists)
        val bindings = RecordingBindings()
        bindings.phones = phones
        val directory = FakeDirectory()
        return Fixture(LinkGroupWhatsApp(read, bindings, directory), bindings, directory)
    }

    private fun binding(targetGroup: UUID) = GroupWhatsAppBinding(
        groupId = targetGroup,
        whatsappJid = WHA_GROUP_JID,
        inviteCode = "Invite1234",
        groupName = "Vôlei do CERET",
        instanceJid = INSTANCE_PHONE,
        enabled = true,
        brokenAt = null,
        createdBy = actor,
    )

    private data class Fixture(
        val useCase: LinkGroupWhatsApp,
        val bindings: RecordingBindings,
        val directory: FakeDirectory,
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

    private class RecordingBindings : GroupWhatsAppBindingRepository {
        var byJid: GroupWhatsAppBinding? = null
        var phones: List<String> = emptyList()
        val upserted = mutableListOf<GroupWhatsAppBinding>()

        override fun find(groupId: UUID): GroupWhatsAppBinding? = null

        override fun findByJid(whatsappJid: String): GroupWhatsAppBinding? = byJid

        override fun upsert(binding: GroupWhatsAppBinding) {
            upserted += binding
        }

        override fun setEnabled(groupId: UUID, enabled: Boolean) = Unit

        override fun markBroken(groupId: UUID) = Unit

        override fun memberPhones(groupId: UUID): List<String> = phones
    }

    private class FakeDirectory : WhatsAppGroupDirectory {
        var instance: String = INSTANCE_PHONE
        var instanceFailure: DirectoryError? = null
        var invite: WhatsAppGroupInfo = WhatsAppGroupInfo(WHA_GROUP_JID, "Vôlei do CERET", listOf(ADMIN_PHONE))
        var inviteFailure: DirectoryError? = null
        var joinFailure: DirectoryError? = null
        var info: WhatsAppGroupInfo = WhatsAppGroupInfo(WHA_GROUP_JID, "Vôlei do CERET", listOf(ADMIN_PHONE))
        var infoFailure: DirectoryError? = null
        var member: Boolean = true
        val joins = mutableListOf<String>()
        val infos = mutableListOf<String>()

        override fun instanceStatus(): String {
            instanceFailure?.let { throw it }
            return instance
        }

        override fun inviteInfo(inviteCode: String): WhatsAppGroupInfo {
            inviteFailure?.let { throw it }
            return invite
        }

        override fun join(inviteCode: String) {
            joins += inviteCode
            joinFailure?.let { throw it }
        }

        override fun groupInfo(jid: String): WhatsAppGroupInfo {
            infos += jid
            infoFailure?.let { throw it }
            return info
        }

        override fun isMember(jid: String): Boolean = member
    }

    private companion object {
        val actor: UUID = UUID.randomUUID()
        val groupId: UUID = UUID.randomUUID()
        val otherGroupId: UUID = UUID.randomUUID()
        const val WHA_GROUP_JID = "120363000000000000@g.us"
        const val INSTANCE_PHONE = "551153040175"
        const val ADMIN_PHONE = "5511988887777"
    }
}
