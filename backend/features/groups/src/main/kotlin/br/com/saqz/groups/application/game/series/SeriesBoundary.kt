package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.recurrence.RecurrenceValidationError
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResolver
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResult
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

enum class SeriesBoundaryScope { ONLY_THIS, THIS_AND_FUTURE }
enum class SeriesBoundaryAction { EDIT, CANCEL }

data class OnlyThisBoundaryCommand(
    val groupId: UUID,
    val gameId: UUID,
    val expectedVersion: Long,
    val today: LocalDate,
    val action: SeriesBoundaryAction,
    val replacement: GameSnapshot? = null,
)

data class FutureBoundaryCommand(
    val groupId: UUID,
    val currentRevisionId: UUID,
    val expectedVersion: Long,
    val successorRule: WeeklySeriesRule,
    val revisionNumber: Int,
    val boundary: LocalDate,
    val action: SeriesBoundaryAction,
    val occurrences: List<MaterializedGameOccurrence>,
)

sealed interface SeriesBoundaryResult {
    data object Applied : SeriesBoundaryResult
    data object Replay : SeriesBoundaryResult
    data object NotFound : SeriesBoundaryResult
    data object VersionConflict : SeriesBoundaryResult
    data object InvalidBoundary : SeriesBoundaryResult
    data class Invalid(val errors: List<RecurrenceValidationError>) : SeriesBoundaryResult
}

interface SeriesBoundaryRepository {
    fun applyOnlyThis(command: OnlyThisBoundaryCommand): SeriesBoundaryResult
    fun applyThisAndFuture(command: FutureBoundaryCommand): SeriesBoundaryResult
}

class ApplySeriesBoundary(
    private val repository: SeriesBoundaryRepository,
    private val ids: () -> UUID,
    private val clock: Clock,
) {
    fun onlyThis(command: OnlyThisBoundaryCommand): SeriesBoundaryResult {
        if (command.action == SeriesBoundaryAction.EDIT && command.replacement == null) {
            return SeriesBoundaryResult.InvalidBoundary
        }
        return repository.applyOnlyThis(command)
    }

    fun thisAndFuture(
        groupId: UUID,
        currentRevisionId: UUID,
        expectedVersion: Long,
        successorRule: WeeklySeriesRule,
        revisionNumber: Int,
        boundary: LocalDate,
        action: SeriesBoundaryAction,
    ): SeriesBoundaryResult {
        if (successorRule.groupId != groupId || successorRule.localStartDate != boundary) {
            return SeriesBoundaryResult.InvalidBoundary
        }
        val resolved = when (val result = WeeklyRecurrenceResolver.resolve(successorRule, boundary)) {
            is WeeklyRecurrenceResult.Invalid -> return SeriesBoundaryResult.Invalid(result.errors)
            is WeeklyRecurrenceResult.Valid -> result.occurrences
        }
        val now = clock.instant()
        val materialized = resolved.map { MaterializedGameOccurrence(ids(), it, GameStatus.DRAFT, now) }
        return repository.applyThisAndFuture(
            FutureBoundaryCommand(
                groupId, currentRevisionId, expectedVersion, successorRule, revisionNumber,
                boundary, action, materialized,
            ),
        )
    }
}
