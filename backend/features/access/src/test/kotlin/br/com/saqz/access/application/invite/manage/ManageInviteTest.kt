package br.com.saqz.access.application.invite.manage

import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.application.group.read.GroupReadSnapshot
import br.com.saqz.access.application.invite.InviteCode
import br.com.saqz.access.application.invite.InviteLinkFactory
import br.com.saqz.access.application.invite.InviteToken
import br.com.saqz.access.application.invite.InviteTokenDigest
import br.com.saqz.access.application.invite.SecureTokenGenerator
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ManageInviteTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val firstToken = token(1)
    private val secondToken = token(2)

    @Test
    fun `owner rotates invite with digest-only persistence command`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.rotate.execute(actor, groupId)

        assertEquals(RotateInviteResult.Success(inviteUri(firstToken.code)), result)
        assertEquals(listOf(RotateInviteCommand(groupId, firstToken.digest, actor)), fixture.repository.rotations)
        assertEquals(listOf(firstToken.code), fixture.links.codes)
        assertEquals(1, fixture.transaction.calls)
    }

    @Test
    fun `admin rotates invite`() {
        val fixture = fixture(GroupRole.ADMIN)

        assertEquals(RotateInviteResult.Success(inviteUri(firstToken.code)), fixture.rotate.execute(actor, groupId))
        assertEquals(1, fixture.repository.rotations.size)
    }

    @Test
    fun `athlete cannot rotate invite`() {
        val fixture = fixture(GroupRole.ATHLETE)

        assertSame(RotateInviteResult.AccessForbidden, fixture.rotate.execute(actor, groupId))
        assertNoRotation(fixture)
    }

    @Test
    fun `nonmember rotate is indistinguishable from missing group`() {
        val fixture = fixture(role = null)

        assertSame(RotateInviteResult.GroupNotFound, fixture.rotate.execute(actor, groupId))
        assertNoRotation(fixture)
    }

    @Test
    fun `missing group cannot rotate invite`() {
        val fixture = fixture(GroupRole.OWNER, groupExists = false)

        assertSame(RotateInviteResult.GroupNotFound, fixture.rotate.execute(actor, groupId))
        assertNoRotation(fixture)
    }

    @Test
    fun `new rotation replaces the previously active digest`() {
        val fixture = fixture(GroupRole.OWNER, tokens = listOf(firstToken, secondToken))

        fixture.rotate.execute(actor, groupId)
        fixture.rotate.execute(actor, groupId)

        assertEquals(secondToken.digest, fixture.repository.activeDigest)
        assertEquals(listOf(firstToken.digest, secondToken.digest), fixture.repository.rotations.map { it.digest })
    }

    @Test
    fun `link factory failure leaves invite persistence untouched`() {
        val fixture = fixture(GroupRole.OWNER)
        fixture.links.failure = IllegalStateException("link unavailable")

        assertFailsWith<IllegalStateException> { fixture.rotate.execute(actor, groupId) }
        assertTrue(fixture.repository.rotations.isEmpty())
        assertEquals(null, fixture.repository.activeDigest)
    }

    @Test
    fun `owner expiration is idempotent`() {
        val fixture = fixture(GroupRole.OWNER)

        assertSame(ExpireInviteResult.Success, fixture.expire.execute(actor, groupId))
        assertSame(ExpireInviteResult.Success, fixture.expire.execute(actor, groupId))

        assertEquals(listOf(groupId, groupId), fixture.repository.expirations)
    }

    @Test
    fun `admin expires invite`() {
        val fixture = fixture(GroupRole.ADMIN)

        assertSame(ExpireInviteResult.Success, fixture.expire.execute(actor, groupId))
        assertEquals(listOf(groupId), fixture.repository.expirations)
    }

    @Test
    fun `athlete cannot expire invite`() {
        val fixture = fixture(GroupRole.ATHLETE)

        assertSame(ExpireInviteResult.AccessForbidden, fixture.expire.execute(actor, groupId))
        assertTrue(fixture.repository.expirations.isEmpty())
    }

    @Test
    fun `nonmember and missing group expiration return the same result`() {
        val nonmember = fixture(role = null)
        val missing = fixture(GroupRole.OWNER, groupExists = false)

        assertSame(ExpireInviteResult.GroupNotFound, nonmember.expire.execute(actor, groupId))
        assertSame(ExpireInviteResult.GroupNotFound, missing.expire.execute(actor, groupId))
        assertTrue(nonmember.repository.expirations.isEmpty())
        assertTrue(missing.repository.expirations.isEmpty())
    }

    private fun fixture(
        role: GroupRole?,
        groupExists: Boolean = true,
        tokens: List<InviteToken> = listOf(firstToken),
    ): Fixture {
        val transaction = RecordingTransactionRunner()
        val repository = RecordingInviteManagementRepository()
        val links = RecordingInviteLinkFactory()
        val policy = GroupAccessPolicy()
        val read = FixedGroupReadRepository(if (groupExists) role else null, groupExists)
        val generator = QueuedTokenGenerator(tokens)
        return Fixture(
            RotateInvite(transaction, read, repository, policy, generator, links),
            ExpireInvite(transaction, read, repository, policy),
            transaction,
            repository,
            links,
        )
    }

    private fun assertNoRotation(fixture: Fixture) {
        assertTrue(fixture.repository.rotations.isEmpty())
        assertTrue(fixture.links.codes.isEmpty())
    }

    private fun token(seed: Int): InviteToken {
        val bytes = ByteArray(32)
        bytes[31] = seed.toByte()
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.US_ASCII))
        return InviteToken(InviteCode.from(raw), InviteTokenDigest.from(digest))
    }

    private fun inviteUri(code: InviteCode) = URI("https://join.saqz.app/invite/${code.value}")

    private data class Fixture(
        val rotate: RotateInvite,
        val expire: ExpireInvite,
        val transaction: RecordingTransactionRunner,
        val repository: RecordingInviteManagementRepository,
        val links: RecordingInviteLinkFactory,
    )

    private inner class FixedGroupReadRepository(
        private val role: GroupRole?,
        private val groupExists: Boolean,
    ) : GroupReadRepository {
        override fun find(key: GroupReadKey): GroupReadSnapshot? = if (groupExists) {
            GroupReadSnapshot(
                groupId,
                AccessName.from("Training Group"),
                IanaTimeZone.from("UTC"),
                role,
                1,
            )
        } else {
            null
        }
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0

        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return block()
        }
    }

    private class RecordingInviteManagementRepository : InviteManagementRepository {
        val rotations = mutableListOf<RotateInviteCommand>()
        val expirations = mutableListOf<UUID>()
        var activeDigest: InviteTokenDigest? = null

        override fun rotate(command: RotateInviteCommand) {
            rotations += command
            activeDigest = command.digest
        }

        override fun expire(groupId: UUID) {
            expirations += groupId
            activeDigest = null
        }
    }

    private class QueuedTokenGenerator(tokens: List<InviteToken>) : SecureTokenGenerator {
        private val remaining = ArrayDeque(tokens)

        override fun generate(): InviteToken = remaining.removeFirst()
    }

    private inner class RecordingInviteLinkFactory : InviteLinkFactory {
        val codes = mutableListOf<InviteCode>()
        var failure: RuntimeException? = null

        override fun create(code: InviteCode): URI {
            codes += code
            failure?.let { throw it }
            return inviteUri(code)
        }
    }
}
