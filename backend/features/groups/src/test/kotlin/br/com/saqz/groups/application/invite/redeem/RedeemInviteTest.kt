package br.com.saqz.groups.application.invite.redeem

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.plan.ClosedAthleteOccupancy
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
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
    private val ownerId = UUID.randomUUID()
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
    fun `approval-enabled invite creates a pending request without membership`() {
        val fixture = fixture(target = RedeemableInvite(groupId, entryRequiresApproval = true))

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Pending(groupId), result)
        assertTrue(fixture.repository.redemptions.isEmpty())
        assertEquals(listOf(CreateEntryRequestCommand(groupId, actor, now)), fixture.repository.entryRequests)
    }

    @Test
    fun `repeating approval-enabled redeem keeps the original request timestamp`() {
        val fixture = fixture(target = RedeemableInvite(groupId, entryRequiresApproval = true))

        assertEquals(RedeemInviteResult.Pending(groupId), fixture.useCase.execute(actor, code.value))
        fixture.clock.current = now.plusSeconds(30)
        assertEquals(RedeemInviteResult.Pending(groupId), fixture.useCase.execute(actor, code.value))

        assertEquals(2, fixture.repository.entryRequests.size)
        assertEquals(now, fixture.repository.entryRequests.first().requestedAt)
        assertEquals(now, fixture.repository.persistedRequestAt[actor])
    }

    @Test
    fun `existing member joins immediately even when approval is enabled`() {
        val fixture = fixture(target = RedeemableInvite(groupId, entryRequiresApproval = true)).also {
            it.repository.roles[actor] = GroupRole.ADMIN
            it.repository.openMembers += actor
        }

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, GroupRole.ADMIN), result)
        assertTrue(fixture.repository.entryRequests.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `approval-enabled redeem checks athlete limit before creating the request`() {
        val fixture = fixture(
            target = RedeemableInvite(groupId, entryRequiresApproval = true),
            athleteLimit = 0,
        )

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.entryRequests.isEmpty())
    }

    @Test
    fun `invite for deleted group has its own outcome without invalid attempt or membership`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(60), groupDeleted = true))

        assertSame(RedeemInviteResult.GroupDeleted, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
        assertTrue(fixture.repository.redemptions.isEmpty())
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
        val fixture = fixture().also {
            it.repository.roles[actor] = GroupRole.ATHLETE
            it.repository.openMembers += actor
        }

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
    fun `athlete entry within titular cap of 25 succeeds`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..24).map { UUID.randomUUID() }
        }

        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.ATHLETE),
            fixture.useCase.execute(actor, code.value),
        )
    }

    @Test
    fun `athlete entry above titular cap of 25 is refused`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..25).map { UUID.randomUUID() }
        }

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `waitlist occupants count toward the athlete cap and block entry`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..20).map { UUID.randomUUID() }
            it.repository.openWaitlist += (1..5).map { UUID.randomUUID() }
        }

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `recently closed athletes still block entry under the cap`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.closed += (1..25).map {
                ClosedAthleteOccupancy(UUID.randomUUID(), now.minusSeconds(3_600))
            }
        }

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `recently closed athlete cannot reenter when owner has no subscription`() {
        val fixture = fixture(athleteLimit = 0).also {
            it.repository.closed += ClosedAthleteOccupancy(actor, now.minusSeconds(3_600))
        }

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `pending downgrade uses the lower athlete limit from the port`() {
        val fixture = fixture(athleteLimit = 25).also {
            it.repository.openMembers += (1..25).map { UUID.randomUUID() }
        }

        assertEquals(RedeemInviteResult.AthleteLimitExceeded, fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `owner redeeming own invite bypasses athlete cap`() {
        val fixture = fixture(athleteLimit = 0).also {
            it.repository.openMembers += (1..25).map { UUID.randomUUID() }
            it.repository.roles[ownerId] = GroupRole.OWNER
        }

        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.OWNER),
            fixture.useCase.execute(ownerId, code.value),
        )
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `unknown invite records one invalid attempt`() {
        val fixture = fixture(target = null)

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(InviteAttemptWindow(now, 1), fixture.repository.windows[actor])
    }

    @Test
    fun `expired invite is invalid and records one invalid attempt`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.minusSeconds(1)))

        assertSame(RedeemInviteResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(1, fixture.repository.invalidAttempts.size)
        assertEquals(InviteAttemptWindow(now, 1), fixture.repository.windows[actor])
        assertTrue(fixture.repository.redemptions.isEmpty())
    }

    @Test
    fun `non-expired invite proceeds to membership redemption`() {
        val fixture = fixture(target = RedeemableInvite(groupId, now.plusSeconds(1)))

        assertEquals(
            RedeemInviteResult.Success(groupId, GroupRole.ATHLETE),
            fixture.useCase.execute(actor, code.value),
        )
        assertEquals(listOf(RedeemMembershipCommand(groupId, actor)), fixture.repository.redemptions)
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
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
        val fixture = fixture().also {
            it.repository.roles[actor] = role
            it.repository.openMembers += actor
        }

        val result = fixture.useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.Success(groupId, role), result)
        assertEquals(role, fixture.repository.roles[actor])
    }

    private fun fixture(
        clockNow: Instant = now,
        target: RedeemableInvite? = RedeemableInvite(groupId, clockNow.plusSeconds(60), groupDeleted = false),
        athleteLimit: Int? = null,
    ): Fixture {
        val repository = RecordingRedemptionRepository(target, ownerId)
        val transaction = RecordingTransactionRunner()
        val clock = MutableClock(clockNow)
        return Fixture(
            RedeemInvite(
                transaction,
                repository,
                FixedSubscriptionLimits(athleteLimit = athleteLimit),
                clock,
            ),
            repository,
            transaction,
            clock,
        )
    }

    private data class Fixture(
        val useCase: RedeemInvite,
        val repository: RecordingRedemptionRepository,
        val transaction: RecordingTransactionRunner,
        val clock: MutableClock,
    )

    private class FixedSubscriptionLimits(
        private val groupLimit: Int? = null,
        private val athleteLimit: Int? = null,
    ) : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = groupLimit
        override fun athleteLimitFor(ownerId: UUID): Int? = athleteLimit
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0
        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return block()
        }
    }

    private class MutableClock(initial: Instant) : Clock() {
        var current = initial

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId) = this

        override fun instant() = current
    }

    private class RecordingRedemptionRepository(
        private val target: RedeemableInvite?,
        private val ownerUserId: UUID,
    ) : InviteRedemptionRepository {
        val windows = mutableMapOf<UUID, InviteAttemptWindow>()
        val lookups = mutableListOf<InviteTokenDigest>()
        val invalidAttempts = mutableListOf<RecordInvalidInviteAttempt>()
        val redemptions = mutableListOf<RedeemMembershipCommand>()
        val entryRequests = mutableListOf<CreateEntryRequestCommand>()
        val persistedRequestAt = mutableMapOf<UUID, Instant>()
        val roles = mutableMapOf<UUID, GroupRole>()
        val openMembers = mutableSetOf<UUID>()
        val openWaitlist = mutableSetOf<UUID>()
        val closed = mutableListOf<ClosedAthleteOccupancy>()

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

        override fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy? {
            if (target == null || target.groupId != groupId) return null
            return GroupAthleteOccupancy(
                ownerUserId = ownerUserId,
                openMemberIds = openMembers.toSet(),
                openWaitlistIds = openWaitlist.toSet(),
                closedOccupancies = closed.toList(),
            )
        }

        override fun findMembershipRole(groupId: UUID, userId: UUID): GroupRole? = roles[userId]

        override fun createEntryRequest(command: CreateEntryRequestCommand) {
            entryRequests += command
            persistedRequestAt.putIfAbsent(command.userId, command.requestedAt)
        }

        override fun redeemMembership(command: RedeemMembershipCommand): GroupRole {
            redemptions += command
            return roles.getOrPut(command.userId) { GroupRole.ATHLETE }
        }
    }
}
