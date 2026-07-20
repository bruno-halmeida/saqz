package br.com.saqz.groups.domain.game.recurrence

import br.com.saqz.groups.domain.game.GameVenueSnapshot
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WeeklyRecurrenceTest {
    @Test fun `ordinary local time uses sole valid offset`() {
        assertEquals(
            Instant.parse("2026-02-04T22:30:00Z"),
            WeeklyRecurrenceResolver.resolveInstant(
                LocalDate.of(2026, 2, 4), LocalTime.of(19, 30), ZoneId.of("America/Sao_Paulo"),
            ),
        )
    }

    @Test fun `daylight saving gap advances by transition duration`() {
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            WeeklyRecurrenceResolver.resolveInstant(
                LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), ZoneId.of("America/New_York"),
            ),
        )
    }

    @Test fun `daylight saving overlap chooses earlier offset`() {
        assertEquals(
            Instant.parse("2026-11-01T05:30:00Z"),
            WeeklyRecurrenceResolver.resolveInstant(
                LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), ZoneId.of("America/New_York"),
            ),
        )
    }

    @Test fun `multiple weekdays preserve wall clock across offset change`() {
        val result = valid(
            rule(
                zone = "Europe/Lisbon",
                start = LocalDate.of(2026, 3, 23),
                slots = listOf(slot(DayOfWeek.MONDAY), slot(DayOfWeek.THURSDAY, time = LocalTime.of(20, 0))),
            ),
            LocalDate.of(2026, 3, 23),
        )

        assertEquals(setOf(LocalTime.of(19, 30), LocalTime.of(20, 0)), result.map { it.localTime }.toSet())
        val mondays = result.filter { it.localDate.dayOfWeek == DayOfWeek.MONDAY }
        assertEquals(Instant.parse("2026-03-23T19:30:00Z"), mondays[0].startsAt)
        assertEquals(Instant.parse("2026-03-30T18:30:00Z"), mondays[1].startsAt)
    }

    @Test fun `rolling horizon is exactly twelve exclusive weeks`() {
        val from = LocalDate.of(2026, 1, 1)
        val result = valid(rule(start = from, slots = listOf(slot(DayOfWeek.THURSDAY))), from)

        assertEquals(12, result.size)
        assertEquals(from, result.first().localDate)
        assertEquals(from.plusWeeks(11), result.last().localDate)
        assertTrue(result.none { !it.localDate.isBefore(from.plusWeeks(12)) })
    }

    @Test fun `series start clamps rolling horizon`() {
        val from = LocalDate.of(2026, 1, 1)
        val result = valid(
            rule(start = from.plusWeeks(2), slots = listOf(slot(DayOfWeek.THURSDAY))),
            from,
        )
        assertEquals(10, result.size)
        assertEquals(from.plusWeeks(2), result.first().localDate)
    }

    @Test fun `series end is inclusive and bounds writes`() {
        val start = LocalDate.of(2026, 1, 1)
        val result = valid(
            rule(start = start, end = start.plusWeeks(2), slots = listOf(slot(DayOfWeek.THURSDAY))),
            start,
        )
        assertEquals(listOf(start, start.plusWeeks(1), start.plusWeeks(2)), result.map { it.localDate })
    }

    @Test fun `active through boundary closes prior revision`() {
        val start = LocalDate.of(2026, 1, 1)
        val result = valid(
            rule(start = start, activeThrough = start.plusWeeks(1), slots = listOf(slot(DayOfWeek.THURSDAY))),
            start,
        )
        assertEquals(listOf(start, start.plusWeeks(1)), result.map { it.localDate })
    }

    @Test fun `window after ended series produces no occurrences`() {
        val start = LocalDate.of(2026, 1, 1)
        assertTrue(valid(rule(start = start, end = start.plusWeeks(1)), start.plusWeeks(2)).isEmpty())
    }

    @Test fun `empty slots fail before resolution`() {
        assertInvalid(rule(slots = emptyList()), "slots")
    }

    @Test fun `invalid timezone fails before resolution`() {
        assertInvalid(rule(zone = "Mars/Olympus"), "zoneId")
    }

    @Test fun `end before start fails before resolution`() {
        assertInvalid(rule(start = DATE, end = DATE.minusDays(1)), "localEndDate")
    }

    @Test fun `active boundary before start fails before resolution`() {
        assertInvalid(rule(start = DATE, activeThrough = DATE.minusDays(1)), "activeThroughDate")
    }

    @Test fun `duplicate stable slot keys fail before resolution`() {
        val slot = slot(DayOfWeek.WEDNESDAY)
        assertInvalid(rule(slots = listOf(slot, slot.copy(weekday = DayOfWeek.FRIDAY))), "slots")
    }

    @Test fun `all invalid slot limits are reported together`() {
        val invalid = slot(DayOfWeek.WEDNESDAY).copy(
            durationMinutes = 14,
            capacity = 101,
            confirmationLeadMinutes = 10081,
            gameFeeCents = 0,
        )
        val errors = assertIs<WeeklyRecurrenceResult.Invalid>(
            WeeklyRecurrenceResolver.resolve(rule(slots = listOf(invalid)), DATE),
        ).errors
        assertEquals(
            setOf(
                "slots[0].durationMinutes",
                "slots[0].capacity",
                "slots[0].confirmationLeadMinutes",
                "slots[0].gameFeeCents",
            ),
            errors.map { it.field }.toSet(),
        )
    }

    @Test fun `invalid slot snapshot text fails before resolution`() {
        val invalid = slot(DayOfWeek.WEDNESDAY).copy(
            title = "X",
            venue = GameVenueSnapshot(UUID.randomUUID(), " A ", "Rua", "\n"),
        )
        val errors = assertIs<WeeklyRecurrenceResult.Invalid>(
            WeeklyRecurrenceResolver.resolve(rule(slots = listOf(invalid)), DATE),
        ).errors
        assertEquals(
            setOf("slots[0].title", "slots[0].venue.name", "slots[0].venue.address", "slots[0].venue.court"),
            errors.map { it.field }.toSet(),
        )
    }

    private fun valid(rule: WeeklySeriesRule, from: LocalDate): List<ResolvedWeeklyOccurrence> =
        assertIs<WeeklyRecurrenceResult.Valid>(WeeklyRecurrenceResolver.resolve(rule, from)).occurrences

    private fun assertInvalid(rule: WeeklySeriesRule, field: String) {
        assertTrue(
            assertIs<WeeklyRecurrenceResult.Invalid>(WeeklyRecurrenceResolver.resolve(rule, DATE)).errors.any { it.field == field },
        )
    }

    private fun rule(
        zone: String = "America/Sao_Paulo",
        start: LocalDate = DATE,
        end: LocalDate? = null,
        activeThrough: LocalDate? = null,
        slots: List<WeeklySlotRule> = listOf(slot(DayOfWeek.WEDNESDAY)),
    ) = WeeklySeriesRule(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), zone, start, end, activeThrough, slots)

    private fun slot(
        weekday: DayOfWeek,
        key: UUID = UUID.randomUUID(),
        time: LocalTime = LocalTime.of(19, 30),
    ) = WeeklySlotRule(
        key,
        weekday,
        time,
        90,
        GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2"),
        24,
        180,
        2500,
        "Treino semanal",
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 1, 7)
    }
}
