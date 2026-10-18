package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.ResolvedWeeklyOccurrence
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/** Quinta-feira, 01/01/2026 12:00Z: a primeira quarta é 07/01, 19:30 em São Paulo (22:30Z). */
val SERIES_NOW: Instant = Instant.parse("2026-01-01T12:00:00Z")
val SERIES_CLOCK: Clock = Clock.fixed(SERIES_NOW, ZoneOffset.UTC)
val SERIES_TODAY: LocalDate = LocalDate.of(2026, 1, 1)

fun seriesVenue() = GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2")

fun wednesdaySlot(
    key: UUID = UUID.randomUUID(),
    capacity: Int = 24,
    time: LocalTime = LocalTime.of(19, 30),
    weekday: DayOfWeek = DayOfWeek.WEDNESDAY,
) = WeeklySlotRule(key, weekday, time, 90, seriesVenue(), capacity, 180, 2500, "Treino semanal")

fun seriesRule(group: UUID, slots: List<WeeklySlotRule> = listOf(wednesdaySlot()), start: LocalDate = SERIES_TODAY, through: LocalDate? = null) =
    WeeklySeriesRule(group, UUID.randomUUID(), UUID.randomUUID(), "America/Sao_Paulo", start, activeThroughDate = through, slots = slots)

class MemorySeriesRepository : WeeklySeriesRepository {
    val roles = mutableMapOf<Pair<UUID, UUID>, GroupRole>()
    val stored = mutableMapOf<Pair<UUID, UUID>, WeeklySeriesView>()
    var open = emptyList<Pair<UUID, UUID>>()
    var createReturns = true
    var occupied = emptySet<Instant>()
    var ranges = emptyList<OccupiedRange>()
    var adoptable = emptySet<Instant>()
    val created = mutableListOf<Triple<WeeklySeriesRule, List<MaterializedGameOccurrence>, List<ResolvedWeeklyOccurrence>>>()
    val finds = mutableListOf<Pair<UUID, UUID>>()

    override fun role(actor: UUID, groupId: UUID) = roles[actor to groupId]
    override fun create(rule: WeeklySeriesRule, occurrences: List<MaterializedGameOccurrence>, adopted: List<ResolvedWeeklyOccurrence>): Boolean {
        created += Triple(rule, occurrences, adopted)
        stored[rule.groupId to rule.seriesId] = WeeklySeriesView(rule, 1, 1, emptyList())
        return createReturns
    }
    override fun find(groupId: UUID, lineageId: UUID): WeeklySeriesView? {
        finds += groupId to lineageId
        return stored[groupId to lineageId]
    }
    override fun openSeries() = open
    override fun occupiedStartsAt(groupId: UUID, lineageId: UUID, after: Instant) = occupied
    override fun occupiedRanges(groupId: UUID, lineageId: UUID, after: Instant) = ranges
    override fun adoptableStartsAt(groupId: UUID, lineageId: UUID, after: Instant) = adoptable
}

class RecordingBoundary : SeriesBoundaryRepository {
    val future = mutableListOf<FutureBoundaryCommand>()
    val only = mutableListOf<OnlyThisBoundaryCommand>()
    override fun applyOnlyThis(command: OnlyThisBoundaryCommand): SeriesBoundaryResult {
        only += command
        return SeriesBoundaryResult.Applied
    }
    override fun applyThisAndFuture(command: FutureBoundaryCommand): SeriesBoundaryResult {
        future += command
        return SeriesBoundaryResult.Applied
    }
}

val DirectTransaction = object : br.com.saqz.groups.application.create.TransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = block()
}

fun seriesReplacement(startsAt: java.time.Instant) = br.com.saqz.groups.domain.game.GameSnapshot(
    "Vôlei de quarta",
    br.com.saqz.groups.domain.game.GameVenueSnapshot(UUID.randomUUID(), "Arena", "Rua Central 100", null),
    java.time.LocalDate.of(2026, 1, 20),
    java.time.LocalTime.of(19, 30),
    br.com.saqz.groups.domain.IanaTimeZone.from("America/Sao_Paulo"),
    startsAt,
    120,
    12,
    startsAt.minusSeconds(3600),
    null,
    null,
)
