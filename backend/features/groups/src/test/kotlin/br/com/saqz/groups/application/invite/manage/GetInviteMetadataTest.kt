package br.com.saqz.groups.application.invite.manage

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.InviteToken
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GetInviteMetadataTest {
    private val now = Instant.parse("2026-07-16T18:00:00Z")
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val createdAt = now.minusSeconds(3_600)
    private val createdByName = "Lucas Prado"

    @Test
    fun `admin receives the short code and url of the active invite`() {
        val metadata = InviteMetadata(null, createdAt, createdByName, shortCode = "44DF8SA8")
        val fixture = fixture(GroupRole.ADMIN, metadata)

        val result = fixture.useCase.execute(actor, groupId)

        assertEquals(
            GetInviteMetadataResult.Success(
                InviteMetadataView(
                    true, null, createdAt, createdByName, metadata.revision,
                    code = "44DF-8SA8", inviteUrl = URI("https://links.saqz.app/?saqz_invite=44DF8SA8"),
                ),
            ),
            result,
        )
        assertTrue(fixture.repository.ensured.isEmpty())
        assertTrue(fixture.repository.rotations.isEmpty())
    }

    @Test
    fun `legacy invite without short code gets one and keeps the long token`() {
        val metadata = InviteMetadata(null, createdAt, createdByName)
        val fixture = fixture(GroupRole.OWNER, metadata)

        val result = fixture.useCase.execute(actor, groupId) as GetInviteMetadataResult.Success

        assertEquals("2222-2222", result.metadata.code)
        assertEquals(listOf("22222222"), fixture.repository.ensured)
        assertTrue(fixture.repository.rotations.isEmpty())
        assertEquals(metadata.revision, result.metadata.revision)
    }

    @Test
    fun `group without invite is born with a short code`() {
        val fixture = fixture(GroupRole.OWNER, null)

        val result = fixture.useCase.execute(actor, groupId) as GetInviteMetadataResult.Success

        assertEquals("2222-2222", result.metadata.code)
        assertEquals(true, result.metadata.active)
        assertEquals(listOf("22222222"), fixture.repository.ensured)
    }

    @Test
    fun `the drawn code is redrawn when another group already uses it`() {
        val fixture = fixture(GroupRole.OWNER, null)
        fixture.repository.taken += "22222222"

        val result = fixture.useCase.execute(actor, groupId) as GetInviteMetadataResult.Success

        assertEquals("3333-3333", result.metadata.code)
    }

    @Test
    fun `expired legacy invite is replaced by a fresh permanent one`() {
        val metadata = InviteMetadata(now.minusSeconds(1), createdAt, createdByName)
        val fixture = fixture(GroupRole.ADMIN, metadata)

        val result = fixture.useCase.execute(actor, groupId) as GetInviteMetadataResult.Success

        assertEquals("2222-2222", result.metadata.code)
        assertEquals(null, result.metadata.expiresAt)
        assertEquals(1, fixture.repository.rotations.size)
    }

    @Test
    fun `athlete cannot read invite metadata`() {
        val fixture = fixture(GroupRole.ATHLETE, InviteMetadata(null, createdAt, createdByName, shortCode = "44DF8SA8"))

        assertSame(GetInviteMetadataResult.AccessForbidden, fixture.useCase.execute(actor, groupId))
        assertTrue(fixture.repository.lookups.isEmpty())
        assertTrue(fixture.repository.ensured.isEmpty())
    }

    @Test
    fun `nonmember cannot read invite metadata`() {
        val fixture = fixture(null, InviteMetadata(null, createdAt, createdByName, shortCode = "44DF8SA8"))

        assertSame(GetInviteMetadataResult.GroupNotFound, fixture.useCase.execute(actor, groupId))
        assertTrue(fixture.repository.lookups.isEmpty())
    }

    private fun fixture(role: GroupRole?, metadata: InviteMetadata?): Fixture {
        val repository = RecordingInviteManagementRepository(metadata)
        val read = FixedGroupReadRepository(role)
        return Fixture(
            GetInviteMetadata(
                RecordingTransactionRunner(),
                read,
                repository,
                GroupAccessPolicy(),
                Clock.fixed(now, ZoneOffset.UTC),
                SequentialGenerator(),
                InviteLinkFactory { code -> URI("https://links.saqz.app/?saqz_invite=${code.value}") },
            ),
            repository,
        )
    }

    private data class Fixture(
        val useCase: GetInviteMetadata,
        val repository: RecordingInviteManagementRepository,
    )

    private class SequentialGenerator : SecureTokenGenerator {
        private var seed = 0

        override fun generate(): InviteToken {
            val code = InviteCode.from(("23456789"[seed++ % 8]).toString().repeat(8))
            return InviteToken(code, InviteTokenDigest.sha256(code))
        }
    }

    private class RecordingTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class FixedGroupReadRepository(private val role: GroupRole?) : GroupReadRepository {
        override fun find(key: GroupReadKey): GroupReadSnapshot = GroupReadSnapshot(
            id = key.groupId,
            name = AccessName.from("Training Group"),
            timeZone = IanaTimeZone.from("UTC"),
            role = role,
            version = 1,
        )
    }

    private class RecordingInviteManagementRepository(
        private var metadata: InviteMetadata?,
    ) : InviteManagementRepository {
        val lookups = mutableListOf<UUID>()
        val ensured = mutableListOf<String>()
        val rotations = mutableListOf<RotateInviteCommand>()
        val taken = mutableSetOf<String>()

        override fun rotate(command: RotateInviteCommand) {
            rotations += command
            metadata = InviteMetadata(null, Instant.EPOCH, "Lucas Prado", command.revision, command.code.value)
        }

        override fun ensureShortCode(groupId: UUID, shortCode: String, createdByUserId: UUID) {
            ensured += shortCode
            metadata = (metadata ?: InviteMetadata(null, Instant.EPOCH, "Lucas Prado")).copy(shortCode = shortCode)
        }

        override fun isShortCodeTaken(shortCode: String): Boolean = shortCode in taken

        override fun findMetadata(groupId: UUID): InviteMetadata? {
            lookups += groupId
            return metadata
        }

        override fun expire(groupId: UUID) = Unit
    }
}
