package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.game.GameStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ResolveAttendanceLinkTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val now = Instant.parse("2026-07-21T18:00:00Z")
    private val code = AttendanceLinkCode.from("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA")

    @Test
    fun `current member resolves published game before deadline`() {
        val fixture = fixture(target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.PUBLISHED, now.plusSeconds(60)))

        assertEquals(ResolveAttendanceLinkResult.Success(groupId, gameId), fixture.useCase.execute(actor, code.value))
        assertEquals(0, fixture.repository.invalidAttempts.size)
    }

    @Test
    fun `resolution succeeds exactly at deadline`() {
        val fixture = fixture(target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.PUBLISHED, now))

        assertEquals(ResolveAttendanceLinkResult.Success(groupId, gameId), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `malformed code is terminal and increments the window`() {
        val fixture = fixture()

        assertSame(ResolveAttendanceLinkResult.InvalidOrExpired, fixture.useCase.execute(actor, "not-a-code"))
        assertEquals(1, fixture.repository.invalidAttempts.single().invalidCount)
    }

    @Test
    fun `unknown capability is terminal and increments the window`() {
        val fixture = fixture(target = null)

        assertSame(ResolveAttendanceLinkResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(1, fixture.repository.invalidAttempts.single().invalidCount)
    }

    @Test
    fun `cancelled game is closed with the cancelled reason`() {
        val fixture = fixture(target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.CANCELLED, now.plusSeconds(60)))

        assertEquals(ResolveAttendanceLinkResult.GameClosed(AttendanceLinkClosedReason.GAME_CANCELLED), fixture.useCase.execute(actor, code.value))
        assertEquals(1, fixture.repository.invalidAttempts.single().invalidCount)
    }

    @Test
    fun `draft game is closed with the unpublished reason`() {
        val fixture = fixture(target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.DRAFT, now.plusSeconds(60)))

        assertEquals(ResolveAttendanceLinkResult.GameClosed(AttendanceLinkClosedReason.GAME_UNPUBLISHED), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `completed game is closed with the started reason`() {
        val fixture = fixture(target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.COMPLETED, now.plusSeconds(60)))

        assertEquals(ResolveAttendanceLinkResult.GameClosed(AttendanceLinkClosedReason.GAME_STARTED), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `cancelled game that already started keeps the cancelled reason`() {
        val fixture = fixture(
            target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.CANCELLED, now.minusSeconds(3600), startsAt = now.minusSeconds(60)),
        )

        assertEquals(ResolveAttendanceLinkResult.GameClosed(AttendanceLinkClosedReason.GAME_CANCELLED), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `resolution succeeds after the deadline while the game has not started`() {
        val fixture = fixture(
            target = AttendanceLinkResolvableTarget(
                groupId, gameId, GameStatus.PUBLISHED, now.minusSeconds(3600), startsAt = now.plusNanos(1),
            ),
        )

        assertEquals(ResolveAttendanceLinkResult.Success(groupId, gameId), fixture.useCase.execute(actor, code.value))
        assertEquals(0, fixture.repository.invalidAttempts.size)
    }

    @Test
    fun `game start instant is closed with the started reason`() {
        val fixture = fixture(
            target = AttendanceLinkResolvableTarget(
                groupId, gameId, GameStatus.PUBLISHED, now.minusSeconds(3600), startsAt = now,
            ),
        )

        assertEquals(ResolveAttendanceLinkResult.GameClosed(AttendanceLinkClosedReason.GAME_STARTED), fixture.useCase.execute(actor, code.value))
        assertEquals(1, fixture.repository.invalidAttempts.single().invalidCount)
    }

    @Test
    fun `ten invalid attempts are still invalid and the eleventh is limited`() {
        val fixture = fixture(window = AttendanceLinkAttemptWindow(now, 9), target = null)

        assertSame(ResolveAttendanceLinkResult.InvalidOrExpired, fixture.useCase.execute(actor, code.value))
        assertEquals(10, fixture.repository.invalidAttempts.last().invalidCount)
        assertEquals(ResolveAttendanceLinkResult.AttemptLimit(600), fixture.useCase.execute(actor, code.value))
    }

    @Test
    fun `successful resolution does not reset the existing invalid window`() {
        val fixture = fixture(
            window = AttendanceLinkAttemptWindow(now.minusSeconds(30), 4),
            target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.PUBLISHED, now.plusSeconds(60)),
        )

        assertEquals(ResolveAttendanceLinkResult.Success(groupId, gameId), fixture.useCase.execute(actor, code.value))
        assertEquals(4, fixture.repository.window.invalidCount)
        assertEquals(0, fixture.repository.invalidAttempts.size)
    }

    @Test
    fun `repeated success is equivalent and side effect free`() {
        val fixture = fixture(target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.PUBLISHED, now.plusSeconds(60)))

        val first = fixture.useCase.execute(actor, code.value)
        val second = fixture.useCase.execute(actor, code.value)

        assertEquals(first, second)
        assertEquals(2, fixture.repository.resolutionLookups)
        assertEquals(0, fixture.repository.invalidAttempts.size)
    }

    @Test
    fun `repository failure becomes unavailable`() {
        val fixture = fixture(target = AttendanceLinkResolvableTarget(groupId, gameId, GameStatus.PUBLISHED, now.plusSeconds(60)))
        fixture.repository.failure = IllegalStateException("temporary")

        assertSame(ResolveAttendanceLinkResult.Unavailable, fixture.useCase.execute(actor, code.value))
    }

    private fun fixture(
        window: AttendanceLinkAttemptWindow = AttendanceLinkAttemptWindow(now, 0),
        target: AttendanceLinkResolvableTarget? = null,
    ): Fixture {
        val repository = RecordingAttendanceResolutionRepository(window, target)
        return Fixture(
            ResolveAttendanceLink(
                transactionRunner = NoOpTransactionRunner,
                repository = repository,
                clock = Clock.fixed(now, ZoneOffset.UTC),
            ),
            repository,
        )
    }

    private data class Fixture(
        val useCase: ResolveAttendanceLink,
        val repository: RecordingAttendanceResolutionRepository,
    )

    private object NoOpTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class RecordingAttendanceResolutionRepository(
        var window: AttendanceLinkAttemptWindow,
        private val target: AttendanceLinkResolvableTarget?,
    ) : AttendanceLinkRepository {
        val invalidAttempts = mutableListOf<RecordInvalidAttendanceLinkAttempt>()
        var resolutionLookups = 0
        var failure: RuntimeException? = null

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow = window

        override fun findResolvableTarget(actorId: UUID, digest: AttendanceLinkTokenDigest): AttendanceLinkResolvableTarget? {
            failure?.let { throw it }
            resolutionLookups += 1
            return target
        }

        override fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt) {
            invalidAttempts += command
            window = AttendanceLinkAttemptWindow(command.windowStartedAt, command.invalidCount)
        }
    }
}
