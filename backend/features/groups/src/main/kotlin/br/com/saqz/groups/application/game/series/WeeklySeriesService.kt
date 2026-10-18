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
import br.com.saqz.groups.application.game.recurrence.creatableBetween
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess

data class SeriesOccurrenceView(val id: UUID, val localDate: LocalDate, val localTime: LocalTime, val startsAt: Instant, val status: GameStatus, val version: Long)
data class WeeklySeriesView(val rule: WeeklySeriesRule, val revisionNumber: Int, val version: Long, val occurrences: List<SeriesOccurrenceView>)

sealed interface WeeklySeriesResult {
    data class Success(val series: WeeklySeriesView, val replay: Boolean = false) : WeeklySeriesResult
    data class Invalid(val errors: List<RecurrenceValidationError>) : WeeklySeriesResult
    data object NotFound : WeeklySeriesResult
    data object Forbidden : WeeklySeriesResult
    data object Conflict : WeeklySeriesResult
}

/** Intervalo de um jogo ativo de fora da série. Jogo encostado (termina quando o outro começa) não se sobrepõe. */
data class OccupiedRange(val start: Instant, val end: Instant) {
    fun overlaps(startsAt: Instant, durationMinutes: Int): Boolean =
        startsAt < end && start < startsAt.plusSeconds(durationMinutes * 60L)
}

interface WeeklySeriesRepository {
    fun role(actor: UUID, groupId: UUID): GroupRole?
    /** [adopted]: ocorrências cujo horário já tem jogo avulso publicado; ele vira o jogo da série, sem criar outro. */
    fun create(
        rule: WeeklySeriesRule,
        occurrences: List<MaterializedGameOccurrence>,
        adopted: List<ResolvedWeeklyOccurrence> = emptyList(),
    ): Boolean
    fun find(groupId: UUID, lineageId: UUID): WeeklySeriesView?
    /** (grupo, linhagem) de toda série cuja última revisão segue aberta em grupo ativo. */
    fun openSeries(): List<Pair<UUID, UUID>> = emptyList()
    /**
     * `starts_at` dos jogos DRAFT/PUBLISHED do grupo que não pertencem a esta linhagem, depois de
     * [after]. Jogo avulso marcado num horário regular vale pela ocorrência: a série nasce sem ela.
     */
    fun occupiedStartsAt(groupId: UUID, lineageId: UUID, after: Instant): Set<Instant> = emptySet()
    /**
     * Intervalos dos mesmos jogos de [occupiedStartsAt] (DEB-048): ocorrência que se sobrepõe a eles, mesmo
     * começando em outra hora, não nasce, porque o banco recusaria o grupo inteiro.
     */
    fun occupiedRanges(groupId: UUID, lineageId: UUID, after: Instant): List<OccupiedRange> = emptyList()
    /**
     * Subconjunto de [occupiedStartsAt] que a série adota: jogo avulso (sem série, não desvinculado) já
     * PUBLICADO. Rascunho avulso é do gestor e segue só pulado; jogo de outra série também.
     */
    fun adoptableStartsAt(groupId: UUID, lineageId: UUID, after: Instant): Set<Instant> = emptySet()
}

class WeeklySeriesService(
    private val repository: WeeklySeriesRepository,
    private val ids: GameIdFactory,
    private val clock: Clock,
    private val writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
    private val horizon: GameCreationHorizon = GameCreationHorizon.Unlimited,
) {
    fun authorizeOrganizer(actor: UUID, groupId: UUID): WeeklySeriesResult? = when (repository.role(actor, groupId)) {
        null -> WeeklySeriesResult.NotFound
        GroupRole.ATHLETE -> WeeklySeriesResult.Forbidden
        GroupRole.OWNER, GroupRole.ADMIN -> null
    }
    fun create(actor: UUID, rule: WeeklySeriesRule): WeeklySeriesResult {
        val role = repository.role(actor, rule.groupId) ?: return WeeklySeriesResult.NotFound
        if (role !in setOf(GroupRole.OWNER, GroupRole.ADMIN)) return WeeklySeriesResult.Forbidden
        writeAccess.requireWrite(rule.groupId)
        val resolved = when (val result = WeeklyRecurrenceResolver.resolve(rule, rule.localStartDate)) {
            is WeeklyRecurrenceResult.Invalid -> return WeeklySeriesResult.Invalid(result.errors)
            is WeeklyRecurrenceResult.Valid -> result.occurrences
        }
        val now = clock.instant()
        // O resto da série nasce mês a mês, pela rotina ExtendGameSeries.
        val materialized = resolved.creatableBetween(now, horizon.until(rule.groupId) ?: now)
            .map { MaterializedGameOccurrence(ids.create(), it, GameStatus.DRAFT, now) }
        val inserted = repository.create(rule, materialized)
        val stored = repository.find(rule.groupId, rule.seriesId) ?: error("created series not readable")
        return WeeklySeriesResult.Success(stored, replay = !inserted)
    }

    fun read(actor: UUID, groupId: UUID, lineageId: UUID): WeeklySeriesResult {
        repository.role(actor, groupId) ?: return WeeklySeriesResult.NotFound
        return repository.find(groupId, lineageId)?.let(WeeklySeriesResult::Success) ?: WeeklySeriesResult.NotFound
    }
}
