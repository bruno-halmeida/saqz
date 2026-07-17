package br.com.saqz.access.application.invite.redeem

import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.invite.InviteCode
import br.com.saqz.access.application.invite.InviteTokenDigest
import br.com.saqz.access.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RedeemInviteTest {
    private val now = Instant.parse("2026-07-16T18:00:00Z")
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val code = InviteCode.from(
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 5 }),
    )

    @Test
    fun `valid invite creates athlete membership once`() {
        val fixture = fixture()

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), result)
        assertEquals(listOf(RedeemMembershipCommand(groupId, actor)), fixture.repository.redemptions)
    }

    @Test
    fun `valid invite lookup uses only SHA-256 digest`() {
        val fixture = fixture()

        fixture.useCase.execute(actor, code.value)

        assertEquals(listOf(InviteTokenDigest.sha256(code)), fixture.repository.lookups)
    }

    @Test
    fun `valid invite does not record or reset attempt window`() {
        val original = InviteAttemptWindow(now.minusSeconds(120), 4)
        val fixture = fixture().also { it.repository.windows[actor] = original }

        fixture.useCase.execute(actor, code.value)

        assertTrue(fixture.repository.invalidAttempts.isEmpty())
        assertEquals(original, fixture.repository.windows[actor])
    }

    @Test
    fun `existing owner role is preserved`() {
        assertPreservedRole(GroupRole.OWNER)
    }

    @Test
    fun `existing admin role is preserved`() {
        assertPreservedRole(GroupRole.ADMIN)
    }

    @Test
    fun `existing athlete retry is idempotent`() {
        val fixture = fixture().also { it.repository.roles[actor] = GroupRole.ATHLETE }

        val first = fixture.useCase.execute(actor, code.value)
        val second = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), first)
        assertEquals(first, second)
        assertEquals(1, fixture.repository.roles.size)
    }

    @Test
    fun `two actors can redeem the same reusable invite`() {
        val fixture = fixture()
        val secondActor = UUID.randomUUID()

        val first = fixture.useCase.execute(actor, code.value)
        val second = fixture.useCase.execute(secondActor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), first)
        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ATHLETE), second)
        assertEquals(setOf(actor, secondActor), fixture.repository.roles.keys)
        assertEquals(2, fixture.repository.lookups.size)
    }

    @Test
    fun `unknown invite records one invalid attempt`() {
        val fixture = fixture(target = null)

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(InviteAttemptWindow(now, 1), fixture.repository.windows[actor])
    }

    @Test
    fun `malformed invite records failure without digest lookup`() {
        val fixture = fixture()

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, "malformed"))
        assertTrue(fixture.repository.lookups.isEmpty())
        assertEquals(1, fixture.repository.invalidAttempts.size)
    }

    @Test
    fun `expired and rotated invite reveal no group`() {
        val fixture = fixture(target = null)

        val expired = fixture.useCase.execute(actor, code.value)
        val rotated = fixture.useCase.execute(actor, code.value)

        assertSame(RedeemInviteResult.InvalidOrExpired, expired)
        assertSame(expired, rotated)
        assertTrue(expired.toString().contains("InvalidOrExpired"))
        assertTrue(!expired.toString().contains(groupId.toString()))
    }

    @Test
    fun `first ten invalid attempts are permitted`() {
        val fixture = fixture(target = null)

        val results = List(10) { fixture.useCase.execute(actor, code.value) }

        assertTrue(results.all { it === RedeemInviteResult.InvalidOrExpired })
        assertEquals(10, fixture.repository.windows.getValue(actor).invalidCount)
    }

    @Test
    fun `eleventh attempt in the window is rate limited without lookup`() {
        val fixture = fixture(target = null)
        repeat(10) { fixture.useCase.execute(actor, code.value) }
        fixture.repository.lookups.clear()

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.AttemptLimit(600), result)
        assertTrue(fixture.repository.lookups.isEmpty())
        assertEquals(10, fixture.repository.windows.getValue(actor).invalidCount)
    }

    @Test
    fun `rate limit reports full window at its start`() {
        val fixture = fixture().also { it.repository.windows[actor] = InviteAttemptWindow(now, 10) }

        assertEquals(RedeemInviteResult.AttemptLimit(600), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `rate limit rounds a partial final second up`() {
        val clockNow = now.plusMillis(599_100)
        val fixture = fixture(clockNow = clockNow).also {
            it.repository.windows[actor] = InviteAttemptWindow(now, 10)
        }

        assertEquals(RedeemInviteResult.AttemptLimit(1), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `attempt exactly at window end starts a fresh window`() {
        val fixture = fixture(target = null).also {
            it.repository.windows[actor] = InviteAttemptWindow(now.minus(Duration.ofMinutes(10)), 10)
        }

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(InviteAttemptWindow(now, 1), fixture.repository.windows[actor])
    }

    @Test
    fun `rate-limited actor cannot probe even a valid invite`() {
        val fixture = fixture().also { it.repository.windows[actor] = InviteAttemptWindow(now, 10) }

        assertEquals(RedeemInviteResult.AttemptLimit(600), fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.lookups.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    private fun assertPreservedRole(role: GroupRole) {
        val fixture = fixture().also { it.repository.roles[actor] = role }

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, role), result)
        assertEquals(role, fixture.repository.roles[actor])
    }

    private fun fixture(
        target: RedeemableInvite? = RedeemableInvite(groupId),
        clockNow: Instant = now,
    ): Fixture {
        val repository = RecordingRedemptionRepository(target)
        val transaction = RecordingTransactionRunner()
        return Fixture(
            RedeemInvite(transaction, repository, Clock.fixed(clockNow, ZoneOffset.UTC)),
            repository,
            transaction,
        )
    }

    private data class Fixture(
        val useCase: RedeemInvite,
        val repository: RecordingRedemptionRepository,
        val transaction: RecordingTransactionRunner,
    )

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0
        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return block()
        }
    }

    private class RecordingRedemptionRepository(
        private val target: RedeemableInvite?,
    ) : InviteRedemptionRepository {
        val windows = mutableMapOf<UUID, InviteAttemptWindow>()
        val lookups = mutableListOf<InviteTokenDigest>()
        val invalidAttempts = mutableListOf<RecordInvalidInviteAttempt>()
        val redemptions = mutableListOf<RedeemMembershipCommand>()
        val roles = mutableMapOf<UUID, GroupRole>()

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): InviteAttemptWindow =
            windows[userId] ?: InviteAttemptWindow(initializedAt, 0)

        override fun findInvite(digest: InviteTokenDigest): RedeemableInvite? {
            lookups += digest
            return target
        }

        override fun recordInvalidAttempt(command: RecordInvalidInviteAttempt) {
            invalidAttempts += command
            windows[command.userId] = InviteAttemptWindow(command.windowStartedAt, command.invalidCount)
        }

        override fun redeemMembership(command: RedeemMembershipCommand): GroupRole {
            redemptions += command
            return roles.getOrPut(command.userId) { GroupRole.ATHLETE }
        }
    }
}
