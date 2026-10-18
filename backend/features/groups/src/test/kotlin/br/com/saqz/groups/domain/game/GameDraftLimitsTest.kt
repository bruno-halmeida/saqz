package br.com.saqz.groups.domain.game

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** JOGO-002/JOGO-007/JOGO-008: tamanhos exatos de cada texto do jogo e o valor da diária, nas duas pontas. */
class GameDraftLimitsTest {
    @Test
    fun `cada texto aceita o tamanho minimo e o maximo e recusa um a menos e um a mais`() {
        // campo, tamanho mínimo, tamanho máximo, montagem do rascunho com o texto do tamanho pedido
        val cases: List<Triple<String, IntRange, (String) -> GameDraftInput>> = listOf(
            Triple("title", 2..120) { draft(title = it) },
            Triple("venue.name", 2..120) { draft(venue = venue(name = it)) },
            Triple("venue.address", 5..300) { draft(venue = venue(address = it)) },
            Triple("venue.court", 1..80) { draft(venue = venue(court = it)) },
            Triple("notes", 2..500) { draft(notes = it) },
        )
        cases.forEach { (field, size, build) ->
            assertEquals(emptyList(), errorsOf(build("x".repeat(size.first))), "$field no mínimo")
            assertEquals(emptyList(), errorsOf(build("x".repeat(size.last))), "$field no máximo")
            assertEquals(listOf(field), errorsOf(build("x".repeat(size.last + 1))), "$field acima do máximo")
            if (size.first > 1) assertEquals(listOf(field), errorsOf(build("x".repeat(size.first - 1))), "$field abaixo do mínimo")
        }
    }

    @Test
    fun `emoji conta como um caractere so`() {
        assertEquals(emptyList(), errorsOf(draft(title = "🏐".repeat(120))))
        assertEquals(listOf("title"), errorsOf(draft(title = "🏐".repeat(121))))
    }

    @Test
    fun `valor da diaria vai de 1 a 99999999 centavos`() {
        assertEquals(1L, valid(draft(fee = 1)).gameFeeCents)
        assertEquals(99_999_999L, valid(draft(fee = 99_999_999)).gameFeeCents)
        assertNull(valid(draft(fee = null)).gameFeeCents)
        listOf(0L, -1L, 100_000_000L).forEach { assertEquals(listOf("gameFeeCents"), errorsOf(draft(fee = it)), "valor $it") }
    }

    @Test
    fun `o snapshot guarda o titulo e o local ja aparados, sem espaco nas pontas e sem texto vazio`() {
        val snapshot = valid(draft(title = "  Treino de terça  ", venue = venue(name = "  Arena Central ", address = " Rua das Flores 100 ", court = " Quadra 2 ")))

        assertEquals("Treino de terça", snapshot.title)
        assertEquals("Arena Central", snapshot.venue.name)
        assertEquals("Rua das Flores 100", snapshot.venue.address)
        assertEquals("Quadra 2", snapshot.venue.court)
    }

    @Test
    fun `texto so de espacos conta como ausente, titulo falta e endereco e quadra ficam vazios`() {
        assertEquals(listOf("title"), errorsOf(draft(title = "   ")))
        val snapshot = valid(draft(venue = venue(address = "   ", court = "  "), notes = "  "))
        assertEquals("", snapshot.venue.address)
        assertNull(snapshot.venue.court)
        assertNull(snapshot.notes)
    }

    @Test
    fun `caractere de controle no meio do texto e recusado mesmo com o tamanho certo`() {
        assertEquals(listOf("title"), errorsOf(draft(title = "Jogo\u0007final")))
        assertEquals(listOf("notes"), errorsOf(draft(notes = "Levar\u0000bola")))
    }

    private fun errorsOf(input: GameDraftInput): List<String> = when (val result = GameDraftValidator.validate(input)) {
        is GameDraftValidation.Valid -> emptyList()
        is GameDraftValidation.Invalid -> result.errors.map { it.field }.distinct()
    }

    private fun valid(input: GameDraftInput) = assertIs<GameDraftValidation.Valid>(GameDraftValidator.validate(input)).snapshot

    private fun venue(name: String = "Arena Central", address: String = "Rua das Flores 100", court: String? = "Quadra 2") =
        GameVenueInput(UUID.randomUUID(), name, address, court)

    private fun draft(
        title: String? = "Treino de terça",
        venue: GameVenueInput? = venue(),
        fee: Long? = 2500,
        notes: String? = "Levar bola",
    ) = GameDraftInput(
        title, venue, LocalDate.of(2026, 8, 12), LocalTime.of(19, 30), "America/Sao_Paulo",
        START, 90, 24, START.minusSeconds(3600), fee, notes,
    )

    private companion object {
        val START: Instant = Instant.parse("2026-08-12T22:30:00Z")
    }
}
