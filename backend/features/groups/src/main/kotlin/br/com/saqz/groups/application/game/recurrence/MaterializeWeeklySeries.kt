package br.com.saqz.groups.application.game.recurrence

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.attendance.AutoConfirmationMaterializationPort
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.recurrence.RecurrenceValidationError
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResolver
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResult
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.groups.domain.game.recurrence.ResolvedWeeklyOccurrence

data class MaterializedGameOccurrence(
    val id: UUID,
    val occurrence: br.com.saqz.groups.domain.game.recurrence.ResolvedWeeklyOccurrence,
    val status: GameStatus,
    val createdAt: Instant,
)

/** Jogo só nasce no futuro e dentro do horizonte mensal do plano ([GameCreationHorizon]). */
fun List<ResolvedWeeklyOccurrence>.creatableBetween(now: Instant, until: Instant): List<ResolvedWeeklyOccurrence> =
    filter { it.startsAt > now && it.startsAt < until }

fun interface ScheduleMaterializationPolicy { fun isPaused(groupId: UUID): Boolean }

fun interface GameIdFactory { fun create(): UUID }

fun interface OccurrenceMaterializationRepository {
    fun insertIfAbsent(occurrences: List<MaterializedGameOccurrence>): Int
}

sealed interface MaterializeWeeklySeriesResult {
    data class Success(val generated: Int, val inserted: Int) : MaterializeWeeklySeriesResult
    data class Invalid(val errors: List<RecurrenceValidationError>) : MaterializeWeeklySeriesResult
}

class MaterializeWeeklySeries(
    private val transactionRunner: TransactionRunner,
    private val repository: OccurrenceMaterializationRepository,
    private val ids: GameIdFactory,
    private val clock: Clock,
    private val autoConfirmation: AutoConfirmationMaterializationPort = AutoConfirmationMaterializationPort { },
    private val writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
    private val schedulePolicy: ScheduleMaterializationPolicy = ScheduleMaterializationPolicy { false },
    private val horizon: GameCreationHorizon = GameCreationHorizon.Unlimited,
) {
    fun execute(rule: WeeklySeriesRule, from: LocalDate): MaterializeWeeklySeriesResult {
        val resolved = when (val result = WeeklyRecurrenceResolver.resolve(rule, from)) {
            is WeeklyRecurrenceResult.Invalid -> return MaterializeWeeklySeriesResult.Invalid(result.errors)
            is WeeklyRecurrenceResult.Valid -> result.occurrences
        }
        val createdAt = clock.instant()
        val until = horizon.until(rule.groupId) ?: return MaterializeWeeklySeriesResult.Success(0, 0)
        val materialized = resolved.creatableBetween(createdAt, until).map { MaterializedGameOccurrence(ids.create(), it, GameStatus.DRAFT, createdAt) }
        return transactionRunner.inTransaction {
            if (!writeAccess.canWrite(rule.groupId) || schedulePolicy.isPaused(rule.groupId)) return@inTransaction MaterializeWeeklySeriesResult.Success(0, 0)
            val inserted = repository.insertIfAbsent(materialized)
            autoConfirmation.apply(materialized)
            MaterializeWeeklySeriesResult.Success(materialized.size, inserted)
        }
    }
}
