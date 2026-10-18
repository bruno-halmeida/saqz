package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.domain.game.GameCapacity
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Horários regulares do grupo viram série semanal: criar, editar daqui para frente, cancelar ou deixar quieto. */
class SyncScheduleSeriesTest {
    private val group = UUID.randomUUID()
    private val series = MemorySeriesRepository()
    private val boundaryRepository = RecordingBoundary()
    private val released = mutableListOf<UUID>()
    private var horizon = GameCreationHorizon.Unlimited
    private var defaults: ScheduleSeriesDefaults? = defaultsFor()

    private val sync get() = SyncScheduleSeries(
        object : ScheduleSeriesDefaultsRepository { override fun seriesDefaults(groupId: UUID) = defaults },
        series,
        ApplySeriesBoundary(boundaryRepository, UUID::randomUUID, SERIES_CLOCK),
        GameIdFactory { UUID.randomUUID() },
        SERIES_CLOCK,
        horizon,
        object : SeriesOccurrenceRelease {
            override fun release(groupId: UUID) { released += groupId }
            override fun releaseAll() = Unit
        },
    )

    private fun defaultsFor(
        slots: List<ScheduleSeriesSlot> = listOf(ScheduleSeriesSlot(DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90)),
        paused: Boolean = false,
        venue: br.com.saqz.groups.domain.game.GameVenueSnapshot? = seriesVenue(),
        capacity: Int = 24,
    ) = ScheduleSeriesDefaults("Vôlei", "America/Sao_Paulo", paused, venue, capacity, 180, 2500, slots)

    @Test fun `grupo sem padrão de agenda, pausado ou sem quadra padrão não ganha série`() {
        defaults = null
        sync.sync(group)
        defaults = defaultsFor(paused = true)
        sync.sync(group)
        defaults = defaultsFor(venue = null)
        sync.sync(group)
        assertTrue(series.created.isEmpty())
        assertTrue(boundaryRepository.future.isEmpty())
    }

    @Test fun `depois de sincronizar libera o próximo jogo do grupo, mesmo quando nada mudou`() {
        defaults = null
        sync.sync(group)
        assertEquals(listOf(group), released)
    }

    @Test fun `sem horário regular e sem série nada nasce`() {
        defaults = defaultsFor(slots = emptyList())
        sync.sync(group)
        assertTrue(series.created.isEmpty())
    }

    @Test fun `horário regular novo cria a série de hoje com 12 semanas de rascunhos`() {
        sync.sync(group)
        val (rule, occurrences, adopted) = series.created.single()
        assertEquals(group, rule.groupId)
        assertEquals(SERIES_TODAY, rule.localStartDate)
        assertEquals("America/Sao_Paulo", rule.zoneId)
        val slot = rule.slots.single()
        assertEquals(DayOfWeek.WEDNESDAY, slot.weekday)
        assertEquals(LocalTime.of(19, 30), slot.localTime)
        assertEquals(90, slot.durationMinutes)
        assertEquals(24, slot.capacity)
        assertEquals(180, slot.confirmationLeadMinutes)
        assertEquals(2500L, slot.gameFeeCents)
        assertEquals("Vôlei", slot.title)
        assertEquals(12, occurrences.size)
        assertEquals(setOf(GameStatus.DRAFT), occurrences.map { it.status }.toSet())
        assertEquals(Instant.parse("2026-01-07T22:30:00Z"), occurrences.first().occurrence.startsAt)
        assertTrue(adopted.isEmpty())
    }

    @Test fun `padrão antigo de 2 vagas sobe para o mínimo de hoje no horário regular`() {
        defaults = defaultsFor(capacity = 2)
        sync.sync(group)
        assertEquals(GameCapacity.MIN, series.created.single().first.slots.single().capacity)
        defaults = defaultsFor(capacity = 30)
        series.stored.clear(); series.created.clear()
        sync.sync(group)
        assertEquals(30, series.created.single().first.slots.single().capacity)
    }

    @Test fun `a série e a chave do horário são sempre as mesmas para o mesmo grupo, dia e hora`() {
        sync.sync(group)
        val first = series.created.single().first
        series.stored.clear(); series.created.clear()
        sync.sync(group)
        val again = series.created.single().first
        assertEquals(first.seriesId, again.seriesId)
        assertEquals(first.revisionId, again.revisionId)
        assertEquals(first.slots.single().slotKey, again.slots.single().slotKey)
    }

    @Test fun `jogo avulso no horário de uma ocorrência faz a série pular essa data`() {
        series.occupied = setOf(Instant.parse("2026-01-14T22:30:00Z"))
        sync.sync(group)
        val occurrences = series.created.single().second
        assertEquals(11, occurrences.size)
        assertTrue(occurrences.none { it.occurrence.startsAt == Instant.parse("2026-01-14T22:30:00Z") })
    }

    @Test fun `jogo de fora que se sobrepõe pelo intervalo também faz pular, e o que só encosta não`() {
        // o jogo avulso de 21:30Z a 22:45Z pega a quarta 07/01 das 22:30Z; o de 21:00Z a 22:30Z só encosta nela
        series.ranges = listOf(
            OccupiedRange(Instant.parse("2026-01-07T21:30:00Z"), Instant.parse("2026-01-07T22:45:00Z")),
            OccupiedRange(Instant.parse("2026-01-14T21:00:00Z"), Instant.parse("2026-01-14T22:30:00Z")),
        )
        sync.sync(group)
        val starts = series.created.single().second.map { it.occurrence.startsAt }
        assertEquals(11, starts.size)
        assertTrue(Instant.parse("2026-01-07T22:30:00Z") !in starts)
        assertTrue(Instant.parse("2026-01-14T22:30:00Z") in starts)
    }

    @Test fun `jogo avulso já publicado no horário é adotado pela série`() {
        val adoptable = Instant.parse("2026-01-14T22:30:00Z")
        series.occupied = setOf(adoptable)
        series.adoptable = setOf(adoptable)
        sync.sync(group)
        val (_, occurrences, adopted) = series.created.single()
        assertEquals(listOf(adoptable), adopted.map { it.startsAt })
        assertTrue(occurrences.none { it.occurrence.startsAt == adoptable })
    }

    @Test fun `sem plano ativo nada nasce, mesmo com horário regular`() {
        horizon = GameCreationHorizon { null }
        sync.sync(group)
        assertTrue(series.created.isEmpty())
        assertTrue(boundaryRepository.future.isEmpty())
    }

    @Test fun `horizonte do plano limita os jogos que nascem`() {
        horizon = GameCreationHorizon { Instant.parse("2026-01-15T12:00:00Z") }
        sync.sync(group)
        assertEquals(2, series.created.single().second.size)
    }

    @Test fun `sincronizar de novo sem mudança de horário não mexe na série`() {
        sync.sync(group)
        sync.sync(group)
        assertEquals(1, series.created.size)
        assertTrue(boundaryRepository.future.isEmpty())
    }

    @Test fun `mudar o horário edita daqui para frente com a revisão seguinte e a fronteira em hoje`() {
        sync.sync(group)
        val before = series.created.single().first
        defaults = defaultsFor(slots = listOf(ScheduleSeriesSlot(DayOfWeek.THURSDAY, LocalTime.of(20, 0), 60)))
        sync.sync(group)
        val edit = boundaryRepository.future.single()
        assertEquals(SeriesBoundaryAction.EDIT, edit.action)
        assertEquals(before.revisionId, edit.currentRevisionId)
        assertEquals(1L, edit.expectedVersion)
        assertEquals(2, edit.revisionNumber)
        assertEquals(SERIES_TODAY, edit.boundary)
        assertEquals(SERIES_TODAY, edit.successorRule.localStartDate)
        assertEquals(DayOfWeek.THURSDAY, edit.successorRule.slots.single().weekday)
        assertTrue(edit.successorRule.revisionId != before.revisionId)
        assertEquals(12, edit.occurrences.size)
        assertEquals(SERIES_NOW, edit.now)
    }

    @Test fun `série que começa no futuro mantém a data de início ao ser editada`() {
        val future = LocalDate.of(2026, 2, 4)
        sync.sync(group)
        val view = series.stored.values.single()
        series.stored[group to view.rule.seriesId] = view.copy(rule = view.rule.copy(localStartDate = future))
        defaults = defaultsFor(slots = listOf(ScheduleSeriesSlot(DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), 90)))
        sync.sync(group)
        // início da série depois de hoje: a fronteira acompanha a série, não hoje; como a regra
        // sucessora e a fronteira coincidem, a edição é aceita
        val edit = boundaryRepository.future.single()
        assertEquals(future, edit.boundary)
        assertEquals(future, edit.successorRule.localStartDate)
    }

    @Test fun `tirar todos os horários cancela daqui para frente e a revisão guarda os horários antigos`() {
        sync.sync(group)
        val before = series.created.single().first
        defaults = defaultsFor(slots = emptyList())
        sync.sync(group)
        val cancel = boundaryRepository.future.single()
        assertEquals(SeriesBoundaryAction.CANCEL, cancel.action)
        assertEquals(before.slots, cancel.successorRule.slots)
        assertTrue(cancel.adopted.isEmpty())
    }

    @Test fun `trial vencido sem assinatura cancela o futuro da série existente`() {
        sync.sync(group)
        horizon = GameCreationHorizon { null }
        sync.sync(group)
        assertEquals(SeriesBoundaryAction.CANCEL, boundaryRepository.future.single().action)
        assertEquals(1, series.created.size)
    }

    @Test fun `série encerrada abre a geração seguinte, que começa depois do fim da anterior`() {
        sync.sync(group)
        val first = series.created.single().first
        val closedThrough = LocalDate.of(2026, 3, 10)
        series.stored[group to first.seriesId] = series.stored.getValue(group to first.seriesId)
            .copy(rule = first.copy(activeThroughDate = closedThrough))
        sync.sync(group)
        val second = series.created.last().first
        assertEquals(2, series.created.size)
        assertTrue(second.seriesId != first.seriesId)
        assertEquals(closedThrough.plusDays(1), second.localStartDate)
    }

    @Test fun `série encerrada no passado não recua o início da geração nova para antes de hoje`() {
        sync.sync(group)
        val first = series.created.single().first
        series.stored[group to first.seriesId] = series.stored.getValue(group to first.seriesId)
            .copy(rule = first.copy(activeThroughDate = LocalDate.of(2025, 12, 1)))
        sync.sync(group)
        assertEquals(SERIES_TODAY, series.created.last().first.localStartDate)
    }
}
