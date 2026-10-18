package br.com.saqz.groups.application.game.series

import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Editar ou cancelar "este e os próximos": que ocorrências nascem, quais são adotadas e até onde vale o plano. */
class SeriesBoundaryRulesTest {
    private val group = UUID.randomUUID()
    private val repository = RecordingBoundary()
    private val rule = seriesRule(group, start = SERIES_TODAY.plusDays(6))
    private val wednesday = Instant.parse("2026-01-07T22:30:00Z")
    private val nextWednesday = Instant.parse("2026-01-14T22:30:00Z")

    private fun apply(
        action: SeriesBoundaryAction = SeriesBoundaryAction.EDIT,
        clock: Clock = SERIES_CLOCK,
        horizon: GameCreationHorizon = GameCreationHorizon.Unlimited,
        skipStartsAt: Set<Instant> = emptySet(),
        skipRanges: List<OccupiedRange> = emptyList(),
        adopt: Set<Instant> = emptySet(),
    ): FutureBoundaryCommand {
        ApplySeriesBoundary(repository, UUID::randomUUID, clock, horizon)
            .thisAndFuture(group, UUID.randomUUID(), 3, rule, 2, rule.localStartDate, action, skipStartsAt, skipRanges, adopt)
        return repository.future.last()
    }

    @Test fun `ocorrência que já começou, ou começa agora, não renasce`() {
        val atTheStart = Clock.fixed(wednesday, ZoneOffset.UTC)
        val starts = apply(clock = atTheStart).occurrences.map { it.occurrence.startsAt }
        assertEquals(11, starts.size)
        assertFalse(wednesday in starts)
        assertTrue(nextWednesday in starts)
    }

    @Test fun `o comando leva o agora do relógio e o horizonte do plano como limite de criação`() {
        val until = Instant.parse("2026-02-01T00:00:00Z")
        val command = apply(horizon = { until })
        assertEquals(SERIES_NOW, command.now)
        assertEquals(until, command.insertUntil)
        assertEquals(3L, command.expectedVersion)
        assertEquals(2, command.revisionNumber)
    }

    @Test fun `sem plano o limite de criação é o próprio agora`() {
        assertEquals(SERIES_NOW, apply(horizon = { null }).insertUntil)
    }

    @Test fun `ocorrência cujo intervalo se sobrepõe a jogo de fora não nasce, mas encostar não conta`() {
        val overlapping = OccupiedRange(wednesday.minusSeconds(60 * 30), wednesday.plusSeconds(60))
        val touching = OccupiedRange(nextWednesday.minusSeconds(60 * 60), nextWednesday)
        val starts = apply(skipRanges = listOf(overlapping, touching)).occurrences.map { it.occurrence.startsAt }
        assertEquals(11, starts.size)
        assertFalse(wednesday in starts)
        assertTrue(nextWednesday in starts)
    }

    @Test fun `jogo de fora publicado no horário é adotado só ao editar, nunca ao cancelar`() {
        val edit = apply(skipStartsAt = setOf(nextWednesday), adopt = setOf(nextWednesday))
        assertEquals(listOf(nextWednesday), edit.adopted.map { it.startsAt })
        assertFalse(edit.occurrences.any { it.occurrence.startsAt == nextWednesday })

        val cancel = apply(SeriesBoundaryAction.CANCEL, skipStartsAt = setOf(nextWednesday), adopt = setOf(nextWednesday))
        assertTrue(cancel.adopted.isEmpty())
    }

    @Test fun `só se adota o horário que está na lista de adoção`() {
        val command = apply(skipStartsAt = setOf(wednesday, nextWednesday), adopt = setOf(nextWednesday))
        assertEquals(listOf(nextWednesday), command.adopted.map { it.startsAt })
    }

    @Test fun `intervalo de jogo de fora cobre do começo até o fim da ocorrência, exclusivo nas pontas`() {
        val range = OccupiedRange(Instant.parse("2026-01-07T20:00:00Z"), Instant.parse("2026-01-07T21:00:00Z"))
        // ocorrência de 90 min
        assertTrue(range.overlaps(Instant.parse("2026-01-07T20:30:00Z"), 90))
        assertTrue(range.overlaps(Instant.parse("2026-01-07T19:00:00Z"), 90))
        assertFalse(range.overlaps(Instant.parse("2026-01-07T21:00:00Z"), 90))
        assertFalse(range.overlaps(Instant.parse("2026-01-07T18:30:00Z"), 90))
        assertTrue(range.overlaps(Instant.parse("2026-01-07T18:30:01Z"), 90))
        assertTrue(range.overlaps(Instant.parse("2026-01-07T20:59:59Z"), 90))
    }
}
