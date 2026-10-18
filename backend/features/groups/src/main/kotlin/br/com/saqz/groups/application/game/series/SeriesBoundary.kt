package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.recurrence.RecurrenceValidationError
import br.com.saqz.groups.domain.game.recurrence.ResolvedWeeklyOccurrence
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResolver
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResult
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import java.time.Clock
import java.time.Instant
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
    /** Preenchido pelo [ApplySeriesBoundary]: jogo que já começou não é mais alterado. */
    val now: Instant = Instant.EPOCH,
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
    /** Jogo com `starts_at <= now` já começou e não é reescrito nem cancelado. */
    val now: Instant = Instant.EPOCH,
    /** Ocorrência existente é atualizada em qualquer data; nova só nasce antes deste instante. */
    val insertUntil: Instant = Instant.MAX,
    /** Ocorrências cujo horário já tem jogo avulso publicado: ele é adotado pela série em vez de gerar outro. */
    val adopted: List<ResolvedWeeklyOccurrence> = emptyList(),
)

sealed interface SeriesBoundaryResult {
    data object Applied : SeriesBoundaryResult
    data object Replay : SeriesBoundaryResult
    data object NotFound : SeriesBoundaryResult
    data object VersionConflict : SeriesBoundaryResult
    data object InvalidBoundary : SeriesBoundaryResult
    /** A substituição começaria em [startsBefore] ou depois, além do fim do plano (DEB-075). */
    data class AfterPlanEnd(val startsBefore: Instant) : SeriesBoundaryResult
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
    private val horizon: GameCreationHorizon = GameCreationHorizon.Unlimited,
) {
    fun onlyThis(command: OnlyThisBoundaryCommand): SeriesBoundaryResult {
        if (command.action == SeriesBoundaryAction.EDIT && command.replacement == null) {
            return SeriesBoundaryResult.InvalidBoundary
        }
        command.replacement?.let { replacement ->
            val limit = horizon.startsBefore(command.groupId)
            if (limit != null && replacement.startsAt >= limit) return SeriesBoundaryResult.AfterPlanEnd(limit)
        }
        return repository.applyOnlyThis(command.copy(now = clock.instant()))
    }

    @Suppress("LongParameterList")
    fun thisAndFuture(
        groupId: UUID,
        currentRevisionId: UUID,
        expectedVersion: Long,
        successorRule: WeeklySeriesRule,
        revisionNumber: Int,
        boundary: LocalDate,
        action: SeriesBoundaryAction,
        /** Horários já ocupados por jogo de fora da série: a ocorrência não nasce, e o jogo fica. */
        skipStartsAt: Set<Instant> = emptySet(),
        /** Jogos de fora da série por intervalo: ocorrência que se sobrepõe a eles também não nasce (DEB-048). */
        skipRanges: List<OccupiedRange> = emptyList(),
        /** Parte de [skipStartsAt] com jogo avulso publicado: esse vira o jogo da série (só em EDIT). */
        adoptStartsAt: Set<Instant> = emptySet(),
    ): SeriesBoundaryResult {
        if (successorRule.groupId != groupId || successorRule.localStartDate != boundary) {
            return SeriesBoundaryResult.InvalidBoundary
        }
        val resolved = when (val result = WeeklyRecurrenceResolver.resolve(successorRule, boundary)) {
            is WeeklyRecurrenceResult.Invalid -> return SeriesBoundaryResult.Invalid(result.errors)
            is WeeklyRecurrenceResult.Valid -> result.occurrences
        }
        val now = clock.instant()
        val materialized = resolved.filter { occurrence ->
            occurrence.startsAt > now && occurrence.startsAt !in skipStartsAt &&
                skipRanges.none { it.overlaps(occurrence.startsAt, occurrence.slot.durationMinutes) }
        }
            .map { MaterializedGameOccurrence(ids(), it, GameStatus.DRAFT, now) }
        val command = FutureBoundaryCommand(
            groupId, currentRevisionId, expectedVersion, successorRule, revisionNumber,
            boundary, action, materialized, now, horizon.until(groupId) ?: now,
            adopted = if (action == SeriesBoundaryAction.CANCEL) emptyList()
            else resolved.filter { it.startsAt > now && it.startsAt in adoptStartsAt },
        )
        return repository.applyThisAndFuture(command)
    }
}
