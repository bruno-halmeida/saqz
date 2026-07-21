package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AttendanceShareSnapshotTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val snapshot = AttendanceShareSnapshot(
        title = "Treino da quinta",
        startsAt = Instant.parse("2026-08-12T22:30:00Z"),
        timeZone = "America/Sao_Paulo",
        venue = "Arena Central",
        capacity = 12,
        confirmed = listOf(
            AttendanceShareSnapshotPerson("Ana"),
            AttendanceShareSnapshotPerson("Bruno"),
        ),
        waitlisted = listOf(AttendanceShareSnapshotPerson("Carla", 1)),
        declined = listOf(AttendanceShareSnapshotPerson("Diego")),
    )

    @Test
    fun `owner reads organizer snapshot`() {
        val fixture = fixture(GroupRole.OWNER)

        assertEquals(ReadAttendanceShareSnapshotResult.Success(snapshot), fixture.useCase.execute(actor, groupId, gameId))
        assertEquals(1, fixture.repository.reads)
    }

    @Test
    fun `admin reads organizer snapshot`() {
        val fixture = fixture(GroupRole.ADMIN)

        assertEquals(ReadAttendanceShareSnapshotResult.Success(snapshot), fixture.useCase.execute(actor, groupId, gameId))
    }

    @Test
    fun `athlete is forbidden from reading organizer snapshot`() {
        val fixture = fixture(GroupRole.ATHLETE)

        assertSame(ReadAttendanceShareSnapshotResult.AccessForbidden, fixture.useCase.execute(actor, groupId, gameId))
        assertEquals(0, fixture.repository.reads)
    }

    @Test
    fun `missing game remains hidden`() {
        val fixture = fixture(null)

        assertSame(ReadAttendanceShareSnapshotResult.GameNotFound, fixture.useCase.execute(actor, groupId, gameId))
        assertEquals(0, fixture.repository.reads)
    }

    private fun fixture(role: GroupRole?): Fixture {
        val repository = RecordingAttendanceShareSnapshotRepository(
            access = role?.let(::AttendanceShareSnapshotAccess),
            snapshot = snapshot,
        )
        return Fixture(
            ReadAttendanceShareSnapshot(NoOpTransactionRunner, repository, GroupAccessPolicy()),
            repository,
        )
    }

    private data class Fixture(
        val useCase: ReadAttendanceShareSnapshot,
        val repository: RecordingAttendanceShareSnapshotRepository,
    )

    private object NoOpTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class RecordingAttendanceShareSnapshotRepository(
        private val access: AttendanceShareSnapshotAccess?,
        private val snapshot: AttendanceShareSnapshot,
    ) : AttendanceShareSnapshotRepository {
        var reads = 0

        override fun findSnapshotAccess(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceShareSnapshotAccess? = access

        override fun readSnapshot(groupId: UUID, gameId: UUID): AttendanceShareSnapshot {
            reads += 1
            return snapshot
        }
    }
}
