package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RotateAttendanceLinkTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val now = Instant.parse("2026-07-21T18:00:00Z")
    private val token = AttendanceLinkToken(
        AttendanceLinkCode.from("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"),
        AttendanceLinkTokenDigest.from(ByteArray(32) { (it + 1).toByte() }),
    )

    @Test
    fun `owner rotates published link before deadline`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.useCase.execute(actor, groupId, gameId)

        assertEquals(RotateAttendanceLinkResult.Success(fixture.links.url), result)
        assertEquals(listOf(RotateAttendanceLinkCommand(groupId, gameId, token.digest, actor)), fixture.repository.rotations)
        assertEquals(listOf(token.code), fixture.links.codes)
    }

    @Test
    fun `admin rotates published link exactly at deadline`() {
        val fixture = fixture(GroupRole.ADMIN, deadline = now)

        assertEquals(RotateAttendanceLinkResult.Success(fixture.links.url), fixture.useCase.execute(actor, groupId, gameId))
        assertEquals(1, fixture.repository.rotations.size)
    }

    @Test
    fun `athlete cannot rotate attendance link`() {
        val fixture = fixture(GroupRole.ATHLETE)

        assertSame(RotateAttendanceLinkResult.AccessForbidden, fixture.useCase.execute(actor, groupId, gameId))
        assertTrue(fixture.repository.rotations.isEmpty())
    }

    @Test
    fun `missing target remains hidden`() {
        val fixture = fixture(null)

        assertSame(RotateAttendanceLinkResult.GameNotFound, fixture.useCase.execute(actor, groupId, gameId))
        assertTrue(fixture.repository.rotations.isEmpty())
    }

    @Test
    fun `draft cancelled and completed games remain frozen`() {
        listOf(GameStatus.DRAFT, GameStatus.CANCELLED, GameStatus.COMPLETED).forEach { status ->
            val fixture = fixture(GroupRole.OWNER, status = status)

            assertSame(RotateAttendanceLinkResult.AttendanceFrozen, fixture.useCase.execute(actor, groupId, gameId), status.name)
        }
    }

    @Test
    fun `deadline plus one instant is rejected`() {
        val fixture = fixture(GroupRole.OWNER, deadline = now.minusNanos(1))

        assertSame(RotateAttendanceLinkResult.DeadlinePassed, fixture.useCase.execute(actor, groupId, gameId))
        assertTrue(fixture.repository.rotations.isEmpty())
    }

    @Test
    fun `branch failure leaves prior active digest unchanged`() {
        val fixture = fixture(GroupRole.OWNER)
        fixture.repository.activeDigest = AttendanceLinkTokenDigest.from(ByteArray(32) { 9 })
        fixture.links.failure = IllegalStateException("branch unavailable")

        assertFailsWith<IllegalStateException> { fixture.useCase.execute(actor, groupId, gameId) }
        assertEquals(AttendanceLinkTokenDigest.from(ByteArray(32) { 9 }), fixture.repository.activeDigest)
        assertTrue(fixture.repository.rotations.isEmpty())
    }

    private fun fixture(
        role: GroupRole?,
        status: GameStatus = GameStatus.PUBLISHED,
        deadline: Instant = now.plusSeconds(60),
    ): Fixture {
        val repository = RecordingAttendanceLinkRepository(
            rotatableTarget = role?.let { AttendanceLinkRotatableTarget(groupId, gameId, it, status, deadline) },
        )
        val links = RecordingAttendanceLinkFactory(URI("https://join.saqz.app/?saqz_attendance=${token.code.value}"))
        return Fixture(
            RotateAttendanceLink(
                transactionRunner = NoOpTransactionRunner,
                repository = repository,
                accessPolicy = GroupAccessPolicy(),
                tokenGenerator = AttendanceLinkTokenGenerator { token },
                linkFactory = links,
                clock = Clock.fixed(now, ZoneOffset.UTC),
            ),
            repository,
            links,
        )
    }

    private data class Fixture(
        val useCase: RotateAttendanceLink,
        val repository: RecordingAttendanceLinkRepository,
        val links: RecordingAttendanceLinkFactory,
    )

    private object NoOpTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class RecordingAttendanceLinkRepository(
        private val rotatableTarget: AttendanceLinkRotatableTarget?,
    ) : AttendanceLinkRepository {
        val rotations = mutableListOf<RotateAttendanceLinkCommand>()
        var activeDigest: AttendanceLinkTokenDigest? = null

        override fun lockRotatableTarget(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceLinkRotatableTarget? = rotatableTarget
        override fun rotate(command: RotateAttendanceLinkCommand) {
            rotations += command
            activeDigest = command.digest
        }

        override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow = error("unused")
        override fun findResolvableTarget(actorId: UUID, digest: AttendanceLinkTokenDigest): AttendanceLinkResolvableTarget? = error("unused")
        override fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt) = error("unused")
    }

    private class RecordingAttendanceLinkFactory(
        val url: URI,
    ) : AttendanceLinkFactory {
        val codes = mutableListOf<AttendanceLinkCode>()
        var failure: RuntimeException? = null

        override fun create(code: AttendanceLinkCode): URI {
            codes += code
            failure?.let { throw it }
            return url
        }
    }
}
