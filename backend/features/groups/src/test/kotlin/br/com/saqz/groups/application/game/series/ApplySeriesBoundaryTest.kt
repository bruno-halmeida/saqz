package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.game.GameSnapshot
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

class ApplySeriesBoundaryTest {
    @Test fun `only-this edit requires replacement`() {
        val fixture = fixture()
        val result = fixture.useCase.onlyThis(fixture.only(SeriesBoundaryAction.EDIT))
        assertEquals(SeriesBoundaryResult.InvalidBoundary, result)
        assertEquals(0, fixture.repository.onlyCalls)
    }

    @Test fun `only-this edit delegates complete replacement`() {
        val fixture = fixture()
        val result = fixture.useCase.onlyThis(fixture.only(SeriesBoundaryAction.EDIT).copy(replacement = snapshot()))
        assertEquals(SeriesBoundaryResult.Applied, result)
        assertEquals(1, fixture.repository.onlyCalls)
    }

    @Test fun `only-this cancel does not require replacement`() {
        val fixture = fixture()
        assertEquals(SeriesBoundaryResult.Applied, fixture.useCase.onlyThis(fixture.only(SeriesBoundaryAction.CANCEL)))
        assertEquals(SeriesBoundaryAction.CANCEL, fixture.repository.only?.action)
    }

    @Test fun `future boundary rejects another group`() {
        val fixture = fixture()
        assertEquals(SeriesBoundaryResult.InvalidBoundary, fixture.future(fixture.rule.copy(groupId = UUID.randomUUID())))
        assertEquals(0, fixture.repository.futureCalls)
    }

    @Test fun `future boundary rejects successor beginning after boundary`() {
        val fixture = fixture()
        assertEquals(SeriesBoundaryResult.InvalidBoundary, fixture.future(fixture.rule.copy(localStartDate = DATE.plusDays(1))))
        assertEquals(0, fixture.repository.futureCalls)
    }

    @Test fun `future boundary returns all recurrence validation errors before persistence`() {
        val fixture = fixture()
        val invalid = fixture.rule.copy(slots = emptyList(), zoneId = "bad zone")
        val result = assertIs<SeriesBoundaryResult.Invalid>(fixture.future(invalid))
        assertEquals(setOf("zoneId", "slots"), result.errors.map { it.field }.toSet())
        assertEquals(0, fixture.repository.futureCalls)
    }

    @Test fun `future edit materializes only twelve-week horizon`() {
        val fixture = fixture()
        assertEquals(SeriesBoundaryResult.Applied, fixture.future(fixture.rule))
        assertEquals(12, fixture.repository.future?.occurrences?.size)
        assertTrue(fixture.repository.future!!.occurrences.all { it.occurrence.localDate < DATE.plusWeeks(12) })
    }

    @Test fun `future cancel retains explicit action`() {
        val fixture = fixture()
        fixture.future(fixture.rule, SeriesBoundaryAction.CANCEL)
        assertEquals(SeriesBoundaryAction.CANCEL, fixture.repository.future?.action)
    }

    private fun fixture(): Fixture {
        val group = UUID.randomUUID(); val lineage = UUID.randomUUID(); val successor = UUID.randomUUID()
        val rule = WeeklySeriesRule(group, lineage, successor, "America/Sao_Paulo", DATE, slots = listOf(slot()))
        val repository = RecordingRepository()
        return Fixture(group, UUID.randomUUID(), rule, repository, ApplySeriesBoundary(repository, UUID::randomUUID, CLOCK))
    }

    private fun slot() = WeeklySlotRule(UUID.randomUUID(), DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90, venue(), 24, 180, 2500, "Treino semanal")
    private fun venue() = GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2")
    private fun snapshot() = GameSnapshot("Treino alterado", venue(), DATE, LocalTime.of(20, 0), IanaTimeZone.from("America/Sao_Paulo"), Instant.parse("2026-01-07T23:00:00Z"), 90, 24, Instant.parse("2026-01-07T20:00:00Z"), 2500, null)

    private class RecordingRepository : SeriesBoundaryRepository {
        var onlyCalls = 0; var futureCalls = 0
        var only: OnlyThisBoundaryCommand? = null; var future: FutureBoundaryCommand? = null
        override fun applyOnlyThis(command: OnlyThisBoundaryCommand): SeriesBoundaryResult { onlyCalls++; only = command; return SeriesBoundaryResult.Applied }
        override fun applyThisAndFuture(command: FutureBoundaryCommand): SeriesBoundaryResult { futureCalls++; future = command; return SeriesBoundaryResult.Applied }
    }

    private data class Fixture(val group: UUID, val current: UUID, val rule: WeeklySeriesRule, val repository: RecordingRepository, val useCase: ApplySeriesBoundary) {
        fun only(action: SeriesBoundaryAction) = OnlyThisBoundaryCommand(group, UUID.randomUUID(), 1, DATE.minusDays(1), action)
        fun future(rule: WeeklySeriesRule, action: SeriesBoundaryAction = SeriesBoundaryAction.EDIT) = useCase.thisAndFuture(group, current, 1, rule, 2, DATE, action)
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 1, 7)
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
    }
}
