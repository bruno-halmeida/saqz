package br.com.saqz.groups.application.game.recurrence

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MaterializeWeeklySeriesTest {
    @Test fun `create materializes one bounded occurrence per identity`() {
        val fixture = fixture()
        val result = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.useCase.execute(fixture.rule, DATE))

        assertEquals(12, result.generated)
        assertEquals(12, result.inserted)
        assertEquals(12, fixture.repository.identities.size)
    }

    @Test fun `retry is idempotent even when generated ids differ`() {
        val fixture = fixture()
        val first = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.useCase.execute(fixture.rule, DATE))
        val retry = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.useCase.execute(fixture.rule, DATE))

        assertEquals(12, first.inserted)
        assertEquals(0, retry.inserted)
        assertEquals(24, fixture.ids.calls)
        assertEquals(12, fixture.repository.identities.size)
    }

    @Test fun `invalid rule performs no transaction id allocation or write`() {
        val fixture = fixture()
        val invalid = fixture.rule.copy(slots = emptyList())
        assertTrue(fixture.useCase.execute(invalid, DATE) is MaterializeWeeklySeriesResult.Invalid)
        assertEquals(0, fixture.transaction.calls)
        assertEquals(0, fixture.ids.calls)
        assertEquals(0, fixture.repository.calls)
    }

    @Test fun `invalid slot snapshot performs no write`() {
        val fixture = fixture()
        val invalid = fixture.rule.copy(slots = listOf(fixture.rule.slots.single().copy(title = "X")))
        assertTrue(fixture.useCase.execute(invalid, DATE) is MaterializeWeeklySeriesResult.Invalid)
        assertEquals(0, fixture.repository.calls)
        assertEquals(0, fixture.ids.calls)
    }

    @Test fun `two slots on same weekday retain distinct bounded identities`() {
        val first = slot(DayOfWeek.WEDNESDAY, LocalTime.of(19, 0))
        val second = slot(DayOfWeek.WEDNESDAY, LocalTime.of(21, 0))
        val fixture = fixture(rule(slots = listOf(first, second)))
        val result = assertIs<MaterializeWeeklySeriesResult.Success>(fixture.useCase.execute(fixture.rule, DATE))

        assertEquals(24, result.generated)
        assertEquals(24, result.inserted)
        assertEquals(setOf(first.slotKey, second.slotKey), fixture.repository.identities.map { it.third }.toSet())
    }

    private fun fixture(rule: WeeklySeriesRule = rule()): Fixture {
        val transaction = RecordingTransaction()
        val repository = IdentityRepository()
        val ids = RecordingIds()
        return Fixture(
            MaterializeWeeklySeries(
                transaction,
                repository,
                ids,
                Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC),
            ),
            transaction,
            repository,
            ids,
            rule,
        )
    }

    private class RecordingTransaction : TransactionRunner {
        var calls = 0
        override fun <T> inTransaction(block: () -> T): T { calls++; return block() }
    }

    private class RecordingIds : GameIdFactory {
        var calls = 0
        override fun create(): UUID = UUID.randomUUID().also { calls++ }
    }

    private class IdentityRepository : OccurrenceMaterializationRepository {
        var calls = 0
        val identities = mutableSetOf<Triple<UUID, LocalDate, UUID>>()
        override fun insertIfAbsent(occurrences: List<MaterializedGameOccurrence>): Int {
            calls++
            return occurrences.count {
                identities.add(Triple(it.occurrence.seriesId, it.occurrence.localDate, it.occurrence.slot.slotKey))
            }
        }
    }

    private data class Fixture(
        val useCase: MaterializeWeeklySeries,
        val transaction: RecordingTransaction,
        val repository: IdentityRepository,
        val ids: RecordingIds,
        val rule: WeeklySeriesRule,
    )

    private fun rule(slots: List<WeeklySlotRule> = listOf(slot(DayOfWeek.WEDNESDAY))) =
        WeeklySeriesRule(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "America/Sao_Paulo", DATE, slots = slots)

    private fun slot(day: DayOfWeek, time: LocalTime = LocalTime.of(19, 30)) = WeeklySlotRule(
        UUID.randomUUID(),
        day,
        time,
        90,
        GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2"),
        24,
        180,
        2500,
        "Treino semanal",
    )

    private companion object { val DATE: LocalDate = LocalDate.of(2026, 1, 7) }
}
