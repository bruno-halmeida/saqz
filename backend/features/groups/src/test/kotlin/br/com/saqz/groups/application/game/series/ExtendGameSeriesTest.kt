package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.application.game.recurrence.OccurrenceMaterializationRepository
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rotina diária da série: jogos nascem mês a mês, trial vencido cancela o futuro, e uma falha não para as outras. */
class ExtendGameSeriesTest {
    private val group = UUID.randomUUID()
    private val series = MemorySeriesRepository()
    private val inserted = mutableListOf<List<MaterializedGameOccurrence>>()
    private val boundaryRepository = RecordingBoundary()
    private val synced = mutableListOf<UUID>()
    private var released = 0
    private val failures = mutableListOf<Pair<String, Throwable>>()
    private var scheduled = emptyList<UUID>()
    private var horizon = GameCreationHorizon.Unlimited
    private var failSync: UUID? = null
    private var failRelease = false
    private val nextId = UUID.randomUUID()

    private fun routine() = ExtendGameSeries(
        series,
        object : ScheduleSeriesDefaultsRepository {
            override fun seriesDefaults(groupId: UUID) = null
            override fun groupsWithRegularSlots() = scheduled
        },
        MaterializeWeeklySeries(
            DirectTransaction,
            OccurrenceMaterializationRepository { inserted += it; it.size },
            GameIdFactory { UUID.randomUUID() },
            SERIES_CLOCK,
            horizon = horizon,
        ),
        ApplySeriesBoundary(boundaryRepository, { nextId }, SERIES_CLOCK),
        ScheduleSeriesSync { if (it == failSync) error("conflito") else synced += it },
        horizon,
        { nextId },
        SERIES_CLOCK,
        object : SeriesOccurrenceRelease {
            override fun release(groupId: UUID) = Unit
            override fun releaseAll() { if (failRelease) error("fora do ar"); released++ }
        },
        { what, error -> failures += what to error },
    )

    private fun open(rule: br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule): UUID {
        series.stored[rule.groupId to rule.seriesId] = WeeklySeriesView(rule, 4, 9, emptyList())
        series.open = series.open + (rule.groupId to rule.seriesId)
        return rule.seriesId
    }

    @Test fun `com plano ativo a série ganha os próximos jogos em rascunho`() {
        open(seriesRule(group))
        val outcome = routine().run()
        assertEquals(ExtendGameSeries.Outcome(series = 1, groups = 0, failed = 0), outcome)
        assertEquals(12, inserted.single().size)
        assertTrue(boundaryRepository.future.isEmpty())
    }

    @Test fun `série que sumiu entre a lista e a leitura é pulada sem erro`() {
        series.open = listOf(group to UUID.randomUUID())
        val outcome = routine().run()
        assertEquals(1, outcome.series)
        assertEquals(0, outcome.failed)
        assertTrue(inserted.isEmpty())
        assertTrue(boundaryRepository.future.isEmpty())
    }

    @Test fun `sem plano ativo a série é cancelada daqui para frente, na revisão seguinte`() {
        horizon = GameCreationHorizon { null }
        val rule = seriesRule(group)
        open(rule)
        routine().run()
        val cancel = boundaryRepository.future.single()
        assertEquals(SeriesBoundaryAction.CANCEL, cancel.action)
        assertEquals(rule.revisionId, cancel.currentRevisionId)
        assertEquals(9L, cancel.expectedVersion)
        assertEquals(5, cancel.revisionNumber)
        assertEquals(SERIES_TODAY, cancel.boundary)
        assertEquals(nextId, cancel.successorRule.revisionId)
        assertEquals(SERIES_TODAY, cancel.successorRule.localStartDate)
        assertTrue(inserted.isEmpty())
    }

    @Test fun `série que só começa no futuro é cancelada a partir do início dela`() {
        horizon = GameCreationHorizon { null }
        val start = LocalDate.of(2026, 3, 4)
        open(seriesRule(group, start = start))
        routine().run()
        assertEquals(start, boundaryRepository.future.single().boundary)
    }

    @Test fun `a data de hoje vale no fuso da série, não no do servidor`() {
        horizon = GameCreationHorizon { null }
        // 01/01 12:00Z já é 02/01 em Auckland
        val rule = seriesRule(group).copy(zoneId = "Pacific/Auckland")
        open(rule)
        routine().run()
        assertEquals(LocalDate.of(2026, 1, 2), boundaryRepository.future.single().boundary)
    }

    @Test fun `cada grupo com horário regular é sincronizado, e a liberação roda uma vez`() {
        val other = UUID.randomUUID()
        scheduled = listOf(group, other)
        val outcome = routine().run()
        assertEquals(listOf(group, other), synced)
        assertEquals(1, released)
        assertEquals(ExtendGameSeries.Outcome(series = 0, groups = 2, failed = 0), outcome)
    }

    @Test fun `um grupo com conflito não para os outros, e a falha é contada e avisada`() {
        val other = UUID.randomUUID()
        scheduled = listOf(group, other)
        failSync = group
        val outcome = routine().run()
        assertEquals(listOf(other), synced)
        assertEquals(1, outcome.failed)
        assertEquals(1, released)
        assertEquals(listOf("group $group"), failures.map { it.first })
        assertEquals("conflito", failures.single().second.message)
    }

    @Test fun `falha na liberação também conta e não derruba a rodada`() {
        failRelease = true
        val outcome = routine().run()
        assertEquals(1, outcome.failed)
        assertEquals("release", failures.single().first)
    }

    @Test fun `falha numa série segue para as demais`() {
        val broken = seriesRule(group)
        val fine = seriesRule(UUID.randomUUID())
        open(broken); open(fine)
        // série quebrada: regra sem horários não resolve e a materialização devolve erro de validação (sem exceção);
        // aqui a falha vem do repositório de leitura
        val failing = object : WeeklySeriesRepository by series {
            override fun find(groupId: UUID, lineageId: UUID) =
                if (groupId == broken.groupId) error("banco fora") else series.find(groupId, lineageId)
        }
        val routine = ExtendGameSeries(
            failing,
            object : ScheduleSeriesDefaultsRepository { override fun seriesDefaults(groupId: UUID) = null },
            MaterializeWeeklySeries(DirectTransaction, OccurrenceMaterializationRepository { inserted += it; it.size }, GameIdFactory { UUID.randomUUID() }, SERIES_CLOCK),
            ApplySeriesBoundary(boundaryRepository, { nextId }, SERIES_CLOCK),
            ScheduleSeriesSync { },
            GameCreationHorizon.Unlimited,
            { nextId },
            SERIES_CLOCK,
            onFailure = { what, error -> failures += what to error },
        )
        val outcome = routine.run()
        assertEquals(ExtendGameSeries.Outcome(series = 2, groups = 0, failed = 1), outcome)
        assertEquals(listOf("series ${broken.seriesId}"), failures.map { it.first })
        assertEquals(1, inserted.size)
    }
}
