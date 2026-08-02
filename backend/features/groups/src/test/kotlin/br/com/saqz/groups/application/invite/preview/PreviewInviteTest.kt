package br.com.saqz.groups.application.invite.preview

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PreviewInviteTest {
    private val now = Instant.parse("2026-07-16T18:00:00Z")
    private val actor = UUID.randomUUID()
    private val ipAddress = "203.0.113.10"
    private val code = InviteCode.from(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 5 }))
    private val card = PreviewInviteCard(
        groupName = "Vôlei do CERET",
        city = "São Paulo",
        composition = GroupComposition.MIXED,
        level = GroupLevel.INTERMEDIATE,
        memberCount = 18,
        regularSlots = listOf(PreviewRegularSlot(DayOfWeek.TUESDAY, LocalTime.of(19, 30))),
        inviterName = "Bruno Almeida",
        entryRequiresApproval = false,
        expiresAt = null,
        nextGame = PreviewNextGame(now.plusSeconds(3600), "CERET", "Quadra 2"),
    )

    @Test
    fun `valid invite returns the complete card with next game and does not count`() {
        val fixture = fixture(target = PreviewableInvite(false, null, card))

        val result = fixture.useCase.execute(actor, ipAddress, code.value)

        assertEquals(PreviewInviteResult.Success(card), result)
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
        assertEquals(null, fixture.anonymousLimiter.retryAfterSeconds(ipAddress, now))
    }

    @Test
    fun `valid invite without next game returns null next game`() {
        val fixture = fixture(target = PreviewableInvite(false, null, card.copy(nextGame = null)))

        val result = assertIs<PreviewInviteResult.Success>(fixture.useCase.execute(null, ipAddress, code.value))

        assertEquals(null, result.card.nextGame)
        assertEquals(null, fixture.anonymousLimiter.retryAfterSeconds(ipAddress, now))
    }

    @Test
    fun `valid anonymous invite does not consume the invalid window`() {
        val fixture = fixture(target = null)
        repeat(29) { fixture.useCase.execute(null, ipAddress, "bad-$it") }
        fixture.repository.target = PreviewableInvite(false, null, card)

        assertIs<PreviewInviteResult.Success>(fixture.useCase.execute(null, ipAddress, code.value))
        fixture.repository.target = null
        assertSame(PreviewInviteResult.Invalid, fixture.useCase.execute(null, ipAddress, "bad-29"))
        assertIs<PreviewInviteResult.AttemptLimit>(fixture.useCase.execute(null, ipAddress, "bad-30"))
    }

    @Test
    fun `malformed code returns invalid without digest lookup and records an anonymous attempt`() {
        val fixture = fixture(target = PreviewableInvite(false, null, card))

        assertSame(PreviewInviteResult.Invalid, fixture.useCase.execute(null, ipAddress, "malformed"))
        assertTrue(fixture.repository.lookups.isEmpty())
        assertEquals(null, fixture.anonymousLimiter.retryAfterSeconds(ipAddress, now))
    }

    @Test
    fun `unknown code returns invalid and records an authenticated attempt`() {
        val fixture = fixture(target = null)

        assertSame(PreviewInviteResult.Invalid, fixture.useCase.execute(actor, ipAddress, code.value))
        assertEquals(1, fixture.repository.invalidAttempts.single().invalidCount)
        assertEquals(listOf(InviteTokenDigest.sha256(code)), fixture.repository.lookups)
    }

    @Test
    fun `deleted group is indistinguishable from invalid and does not count`() {
        val fixture = fixture(target = PreviewableInvite(true, null, card))

        assertSame(PreviewInviteResult.Invalid, fixture.useCase.execute(actor, ipAddress, code.value))
        assertTrue(fixture.repository.invalidAttempts.isEmpty())
    }

    @Test
    fun `expired invite returns expiry instant without becoming invalid`() {
        val expiredAt = now.minusSeconds(1)
        val fixture = fixture(target = PreviewableInvite(false, expiredAt, card.copy(expiresAt = expiredAt)))

        assertEquals(PreviewInviteResult.Expired(expiredAt), fixture.useCase.execute(null, ipAddress, code.value))
        assertEquals(null, fixture.anonymousLimiter.retryAfterSeconds(ipAddress, now))
    }

    @Test
    fun `anonymous limit allows thirty invalid attempts and blocks the thirty-first`() {
        val fixture = fixture(target = null)

        repeat(30) { assertSame(PreviewInviteResult.Invalid, fixture.useCase.execute(null, ipAddress, "bad-$it")) }
        val result = fixture.useCase.execute(null, ipAddress, "bad-final")

        val limit = assertIs<PreviewInviteResult.AttemptLimit>(result)
        assertEquals(600, limit.retryAfterSeconds)
    }

    @Test
    fun `anonymous windows are isolated by ip and expire after ten minutes`() {
        val fixture = fixture(target = null)
        repeat(30) { fixture.useCase.execute(null, ipAddress, "bad-$it") }

        assertFalse(fixture.useCase.execute(null, "203.0.113.11", "malformed") is PreviewInviteResult.AttemptLimit)
        fixture.clock.current = now.plusSeconds(601)
        assertFalse(fixture.useCase.execute(null, ipAddress, "malformed") is PreviewInviteResult.AttemptLimit)
    }

    private fun fixture(target: PreviewableInvite?): Fixture {
        val repository = RecordingPreviewRepository(target)
        val limiter = AnonymousInvitePreviewRateLimiter()
        val clock = MutableClock(now)
        return Fixture(
            repository = repository,
            anonymousLimiter = limiter,
            useCase = PreviewInvite(DirectTransactionRunner, repository, limiter, clock),
            clock = clock,
        )
    }

    private class Fixture(
        val repository: RecordingPreviewRepository,
        val anonymousLimiter: AnonymousInvitePreviewRateLimiter,
        val useCase: PreviewInvite,
        val clock: MutableClock,
    )

    private object DirectTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class MutableClock(
        var current: Instant,
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current
    }

    private class RecordingPreviewRepository(
        var target: PreviewableInvite?,
    ) : PreviewInviteRepository {
        val lookups = mutableListOf<InviteTokenDigest>()
        val invalidAttempts = mutableListOf<RecordInvalidPreviewInviteAttempt>()
        private val windows = mutableMapOf<UUID, PreviewInviteAttemptWindow>()

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): PreviewInviteAttemptWindow =
            windows.getOrPut(userId) { PreviewInviteAttemptWindow(initializedAt, 0) }

        override fun recordInvalidAttempt(command: RecordInvalidPreviewInviteAttempt) {
            invalidAttempts += command
            windows[command.userId] = PreviewInviteAttemptWindow(command.windowStartedAt, command.invalidCount)
        }

        override fun findInvite(digest: InviteTokenDigest, now: Instant): PreviewableInvite? {
            lookups += digest
            return target
        }
    }
}
