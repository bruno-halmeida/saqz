package br.com.saqz.groups.domain.game.recurrence

import br.com.saqz.groups.domain.game.GameVenueSnapshot
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** JOGO-010: cada limite do horário fixo vale nas duas pontas, e o jogo gerado herda prazo e fuso do horário. */
class WeeklyRecurrenceLimitsTest {
    @Test
    fun `fim e ultimo dia ativo no mesmo dia do inicio sao validos e geram esse unico dia`() {
        val sameDay = LocalDate.of(2026, 1, 7)

        assertEquals(listOf(sameDay), occurrences(rule(end = sameDay)).map { it.localDate })
        assertEquals(listOf(sameDay), occurrences(rule(activeThrough = sameDay)).map { it.localDate })
        assertEquals(listOf("localEndDate"), errors(rule(end = sameDay.minusDays(1))))
        assertEquals(listOf("activeThroughDate"), errors(rule(activeThrough = sameDay.minusDays(1))))
    }

    @Test
    fun `duracao vai de 15 a 480 minutos`() {
        listOf(15, 480).forEach { assertEquals(emptyList(), errors(rule(slot = slot().copy(durationMinutes = it))), "duração $it") }
        listOf(14, 481).forEach { assertEquals(listOf("slots[0].durationMinutes"), errors(rule(slot = slot().copy(durationMinutes = it))), "duração $it") }
    }

    @Test
    fun `antecedencia do prazo vai de 0 a 10080 minutos`() {
        listOf(0, 10080).forEach { assertEquals(emptyList(), errors(rule(slot = slot().copy(confirmationLeadMinutes = it))), "prazo $it") }
        listOf(-1, 10081).forEach { assertEquals(listOf("slots[0].confirmationLeadMinutes"), errors(rule(slot = slot().copy(confirmationLeadMinutes = it))), "prazo $it") }
    }

    @Test
    fun `valor da diaria vai de 1 a 99999999 centavos e pode faltar`() {
        listOf(1L, 99_999_999L, null).forEach { assertEquals(emptyList(), errors(rule(slot = slot().copy(gameFeeCents = it))), "valor $it") }
        listOf(0L, 100_000_000L).forEach { assertEquals(listOf("slots[0].gameFeeCents"), errors(rule(slot = slot().copy(gameFeeCents = it))), "valor $it") }
    }

    @Test
    fun `textos do horario aceitam os tamanhos minimo e maximo e recusam um a menos e um a mais`() {
        val cases: List<Triple<String, IntRange, (String) -> WeeklySlotRule>> = listOf(
            Triple("slots[0].title", 2..120) { slot().copy(title = it) },
            Triple("slots[0].venue.name", 2..120) { slot(venue = venue(name = it)) },
            Triple("slots[0].venue.address", 5..300) { slot(venue = venue(address = it)) },
            Triple("slots[0].venue.court", 1..80) { slot(venue = venue(court = it)) },
        )
        cases.forEach { (field, size, build) ->
            assertEquals(emptyList(), errors(rule(slot = build("x".repeat(size.first)))), "$field no mínimo")
            assertEquals(emptyList(), errors(rule(slot = build("x".repeat(size.last)))), "$field no máximo")
            assertEquals(listOf(field), errors(rule(slot = build("x".repeat(size.last + 1)))), "$field acima do máximo")
            if (size.first > 1) assertEquals(listOf(field), errors(rule(slot = build("x".repeat(size.first - 1)))), "$field abaixo do mínimo")
        }
    }

    @Test
    fun `endereco vazio e aceito, quadra ausente tambem`() {
        assertEquals(emptyList(), errors(rule(slot = slot(venue = venue(address = "", court = null)))))
    }

    @Test
    fun `texto com controle no meio ou espaco nas pontas e recusado`() {
        assertEquals(listOf("slots[0].title"), errors(rule(slot = slot().copy(title = "Treino\u0007semanal"))))
        assertEquals(listOf("slots[0].title"), errors(rule(slot = slot().copy(title = " Treino semanal"))))
        assertEquals(listOf("slots[0].title"), errors(rule(slot = slot().copy(title = "Treino semanal "))))
    }

    @Test
    fun `a ocorrencia traz o fuso e o prazo e o inicio menos a antecedencia do horario`() {
        val occurrence = occurrences(rule(slot = slot().copy(confirmationLeadMinutes = 180))).first()

        assertEquals("America/Sao_Paulo", occurrence.zoneId.value)
        assertEquals(occurrence.startsAt.minusSeconds(180 * 60), occurrence.confirmationDeadline)
        assertEquals(java.time.Instant.parse("2026-01-07T22:30:00Z"), occurrence.startsAt)
        assertEquals(java.time.Instant.parse("2026-01-07T19:30:00Z"), occurrence.confirmationDeadline)
    }

    private fun errors(rule: WeeklySeriesRule): List<String> =
        (WeeklyRecurrenceResolver.resolve(rule, DATE) as? WeeklyRecurrenceResult.Invalid)?.errors?.map { it.field }.orEmpty()

    private fun occurrences(rule: WeeklySeriesRule) =
        assertIs<WeeklyRecurrenceResult.Valid>(WeeklyRecurrenceResolver.resolve(rule, DATE)).occurrences

    private fun venue(name: String = "Arena Central", address: String = "Rua das Flores 100", court: String? = "Quadra 2") =
        GameVenueSnapshot(UUID.randomUUID(), name, address, court)

    private fun slot(venue: GameVenueSnapshot = venue()) =
        WeeklySlotRule(UUID.randomUUID(), DayOfWeek.WEDNESDAY, LocalTime.of(19, 30), 90, venue, 24, 180, 2500, "Treino semanal")

    private fun rule(slot: WeeklySlotRule = slot(), end: LocalDate? = null, activeThrough: LocalDate? = null) =
        WeeklySeriesRule(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "America/Sao_Paulo", DATE, end, activeThrough, listOf(slot))

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 1, 7)
    }
}
