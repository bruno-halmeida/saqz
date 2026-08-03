package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.attendance.AutoConfirmationCandidate
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.ResolvedWeeklyOccurrence
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertTrue

class AutoConfirmAttendanceTest {
    @Test
    fun `materialization ignores cancelled occurrences`() {
        val repository = RecordingRepository()
        val service = AutoConfirmAttendance(
            object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
            repository,
            { NOW },
        )
        val occurrence = ResolvedWeeklyOccurrence(
            GROUP,
            SERIES,
            REVISION,
            WeeklySlotRule(SLOT, java.time.DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90, VENUE, 2, 180, null, "Treino"),
            LocalDate.of(2026, 8, 5),
            LocalTime.of(19, 30),
            IanaTimeZone.from("America/Sao_Paulo"),
            Instant.parse("2026-08-05T22:30:00Z"),
        )

        service.applyMaterialized(listOf(MaterializedGameOccurrence(GAME, occurrence, GameStatus.DRAFT, NOW)))

        assertTrue(repository.saved.isEmpty())
        assertTrue(repository.events.isEmpty())
    }

    private class RecordingRepository : AutoConfirmationRepository {
        val saved = mutableListOf<AttendanceRecord>()
        val events = mutableListOf<AttendanceEvent>()
        override fun updateOwnOptIn(groupId: UUID, memberId: UUID, enabled: Boolean) = AutoConfirmationOptInUpdate.GroupNotFound
        override fun lockGame(groupId: UUID, gameId: UUID): AutoConfirmationGame? = null
        override fun lockOccurrence(groupId: UUID, seriesId: UUID, localDate: LocalDate, slotKey: UUID) =
            AutoConfirmationGame(GROUP, GAME, OWNER, GameStatus.CANCELLED, 2, 0, true)
        override fun candidates(gameId: UUID) = listOf(AutoConfirmationCandidate(MEMBER, AthleteMembershipType.MENSALISTA, true, NOW))
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = 1L
        override fun save(record: AttendanceRecord) { saved += record }
        override fun append(event: AttendanceEvent) { events += event }
    }

    private companion object {
        val OWNER = UUID.randomUUID()
        val MEMBER = UUID.randomUUID()
        val GROUP = UUID.randomUUID()
        val GAME = UUID.randomUUID()
        val SERIES = UUID.randomUUID()
        val REVISION = UUID.randomUUID()
        val SLOT = UUID.randomUUID()
        val NOW = Instant.parse("2026-08-03T12:00:00Z")
        val VENUE = GameVenueSnapshot(null, "Arena Central", "Rua das Flores 100", null)
    }
}
