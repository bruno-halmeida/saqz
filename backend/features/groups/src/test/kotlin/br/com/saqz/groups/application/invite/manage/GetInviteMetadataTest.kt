package br.com.saqz.groups.application.invite.manage

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
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
    fun `owner receives active invite metadata`() {
        val metadata = InviteMetadata(now.plusSeconds(60), createdAt, createdByName)
        val fixture = fixture(GroupRole.OWNER, metadata)

        val result = fixture.useCase.execute(actor, groupId)

        assertEquals(
            GetInviteMetadataResult.Success(
                InviteMetadataView(true, metadata.expiresAt, metadata.createdAt, metadata.createdByName),
            ),
            result,
        )
        assertEquals(listOf(groupId), fixture.repository.lookups)
    }

    @Test
    fun `expired invite returns only its expiration`() {
        val metadata = InviteMetadata(now.minusSeconds(1), createdAt, createdByName)
        val fixture = fixture(GroupRole.ADMIN, metadata)

        val result = fixture.useCase.execute(actor, groupId)

        assertEquals(
            GetInviteMetadataResult.Success(InviteMetadataView(false, metadata.expiresAt, null, null)),
            result,
        )
    }

    @Test
    fun `missing invite returns inactive metadata without expiration`() {
        val fixture = fixture(GroupRole.OWNER, null)

        assertEquals(
            GetInviteMetadataResult.Success(InviteMetadataView(false, null, null, null)),
            fixture.useCase.execute(actor, groupId),
        )
    }

    @Test
    fun `athlete cannot read invite metadata`() {
        val fixture = fixture(GroupRole.ATHLETE, InviteMetadata(now.plusSeconds(60), createdAt, createdByName))

        assertSame(GetInviteMetadataResult.AccessForbidden, fixture.useCase.execute(actor, groupId))
        assertTrue(fixture.repository.lookups.isEmpty())
    }

    @Test
    fun `nonmember cannot read invite metadata`() {
        val fixture = fixture(null, InviteMetadata(now.plusSeconds(60), createdAt, createdByName))

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
            ),
            repository,
        )
    }

    private data class Fixture(
        val useCase: GetInviteMetadata,
        val repository: RecordingInviteManagementRepository,
    )

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
        private val metadata: InviteMetadata?,
    ) : InviteManagementRepository {
        val lookups = mutableListOf<UUID>()

        override fun rotate(command: RotateInviteCommand) = Unit

        override fun findMetadata(groupId: UUID): InviteMetadata? {
            lookups += groupId
            return metadata
        }

        override fun expire(groupId: UUID) = Unit
    }
}
