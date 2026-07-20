package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.recurrence.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class SeriesOccurrenceView(val id: UUID, val localDate: LocalDate, val localTime: LocalTime, val startsAt: Instant, val status: GameStatus, val version: Long)
data class WeeklySeriesView(val rule: WeeklySeriesRule, val revisionNumber: Int, val version: Long, val occurrences: List<SeriesOccurrenceView>)

sealed interface WeeklySeriesResult {
    data class Success(val series: WeeklySeriesView, val replay: Boolean = false) : WeeklySeriesResult
    data class Invalid(val errors: List<RecurrenceValidationError>) : WeeklySeriesResult
    data object NotFound : WeeklySeriesResult
    data object Forbidden : WeeklySeriesResult
    data object Conflict : WeeklySeriesResult
}

interface WeeklySeriesRepository {
    fun role(actor: UUID, groupId: UUID): GroupRole?
    fun create(rule: WeeklySeriesRule, occurrences: List<MaterializedGameOccurrence>): Boolean
    fun find(groupId: UUID, lineageId: UUID): WeeklySeriesView?
}

class WeeklySeriesService(private val repository: WeeklySeriesRepository, private val ids: GameIdFactory, private val clock: Clock) {
    fun authorizeOrganizer(actor: UUID, groupId: UUID): WeeklySeriesResult? = when (repository.role(actor, groupId)) {
        null -> WeeklySeriesResult.NotFound
        GroupRole.ATHLETE -> WeeklySeriesResult.Forbidden
        GroupRole.OWNER, GroupRole.ADMIN -> null
    }
    fun create(actor: UUID, rule: WeeklySeriesRule): WeeklySeriesResult {
        val role = repository.role(actor, rule.groupId) ?: return WeeklySeriesResult.NotFound
        if (role !in setOf(GroupRole.OWNER, GroupRole.ADMIN)) return WeeklySeriesResult.Forbidden
        val resolved = when (val result = WeeklyRecurrenceResolver.resolve(rule, rule.localStartDate)) {
            is WeeklyRecurrenceResult.Invalid -> return WeeklySeriesResult.Invalid(result.errors)
            is WeeklyRecurrenceResult.Valid -> result.occurrences
        }
        val now = clock.instant()
        val inserted = repository.create(rule, resolved.map { MaterializedGameOccurrence(ids.create(), it, GameStatus.DRAFT, now) })
        val stored = repository.find(rule.groupId, rule.seriesId) ?: error("created series not readable")
        return WeeklySeriesResult.Success(stored, replay = !inserted)
    }

    fun read(actor: UUID, groupId: UUID, lineageId: UUID): WeeklySeriesResult {
        repository.role(actor, groupId) ?: return WeeklySeriesResult.NotFound
        return repository.find(groupId, lineageId)?.let(WeeklySeriesResult::Success) ?: WeeklySeriesResult.NotFound
    }
}
