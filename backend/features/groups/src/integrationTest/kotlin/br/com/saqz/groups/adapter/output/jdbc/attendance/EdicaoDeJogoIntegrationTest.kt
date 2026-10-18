package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGameScheduleChangeNotice
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.AdjustGameCapacity
import br.com.saqz.groups.application.game.EditGame
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameVenueInput
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DEB-046 (aviso de mudança de dia, hora ou local) e DEB-047 (vagas do editor pelo caminho do "Ajustar vagas"),
 * com o EditGame de verdade sobre o Postgres.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdicaoDeJogoIntegrationTest {
    private lateinit var h: FilaHarness
    private val games get() = JdbcGameOccurrenceRepository(h.dataSource)
    private val zone = ZoneId.of("America/Sao_Paulo")

    @BeforeEach fun reset() {
        h = FilaHarness(this); h.cenario(capacity = 4)
        // O cenário base deixa dia e hora locais soltos do início; o editor compara o que o jogo diz de si mesmo.
        h.execute(
            "UPDATE games SET starts_at = date_trunc('minute', starts_at), " +
                "local_date = (date_trunc('minute', starts_at) AT TIME ZONE 'America/Sao_Paulo')::date, " +
                "local_time = (date_trunc('minute', starts_at) AT TIME ZONE 'America/Sao_Paulo')::time " +
                "WHERE id='${h.game}'",
        )
    }

    private fun editor(clock: Clock = Clock.systemUTC()): EditGame {
        val tx = JdbcTransactionRunner(h.dataSource)
        val capacity = AdjustGameCapacity(tx, h.repo(), h.cobrancas(), { Instant.now() })
        return EditGame(tx, games, { _, _, _ -> }, clock, capacity, JdbcGameScheduleChangeNotice(h.dataSource))
    }

    private fun atual() = checkNotNull(games.find(h.group, h.game))

    private fun input(
        from: GameSnapshot = atual().snapshot,
        shift: Duration = Duration.ZERO,
        venue: String = from.venue.name,
        capacity: Int = from.capacity,
        fee: Long? = from.gameFeeCents,
        notes: String? = from.notes,
    ): GameDraftInput {
        val startsAt = from.startsAt.plus(shift)
        val local = startsAt.atZone(zone)
        return GameDraftInput(
            from.title, GameVenueInput(from.venue.venueId, venue, from.venue.address, from.venue.court),
            local.toLocalDate(), local.toLocalTime(), zone.id, startsAt, from.durationMinutes, capacity,
            from.confirmationDeadline, fee, notes,
        )
    }

    private fun salvar(input: GameDraftInput, clock: Clock = Clock.systemUTC()) =
        editor(clock).execute(h.dono, h.group, h.game, atual().version, input)

    private fun avisosDeMudanca(user: UUID) = h.int(
        "SELECT count(*) FROM group_notifications n JOIN group_messages m ON m.id=n.message_id " +
            "WHERE n.recipient_id='$user' AND m.channel='AUTO_CONFIRMATION' AND m.body LIKE '%mudou:%'",
    )

    private fun elenco(): Triple<UUID, UUID, UUID> {
        val vou = h.atleta(); val espera = h.atleta(); val semResposta = h.atleta()
        h.presenca(vou, "CONFIRMED"); h.presenca(espera, "WAITLISTED", 1)
        return Triple(vou, espera, semResposta)
    }

    @Test fun `mudar dia ou hora avisa quem disse Vou, quem espera e quem nao respondeu, e nao quem disse Nao vou nem quem editou`() {
        val (vou, espera, semResposta) = elenco()
        val naoVou = h.atleta(); h.presenca(naoVou, "DECLINED")
        val result = salvar(input(shift = Duration.ofHours(26)))
        assertIs<GameCommandResult.Success>(result)
        listOf(vou, espera, semResposta).forEach { assertEquals(1, avisosDeMudanca(it)) }
        assertEquals(0, avisosDeMudanca(naoVou)); assertEquals(0, avisosDeMudanca(h.dono))
        val corpo = h.string("SELECT body FROM group_messages WHERE channel='AUTO_CONFIRMATION' AND body LIKE '%mudou:%'")
        assertTrue(corpo.startsWith("O jogo de ") && "mudou:" in corpo && "Arena" in corpo && " às " in corpo, corpo)
    }

    @Test fun `mudar so a hora ou so o local tambem avisa`() {
        val (vou) = elenco()
        assertIs<GameCommandResult.Success>(salvar(input(shift = Duration.ofMinutes(30))))
        assertEquals(1, avisosDeMudanca(vou))
        assertIs<GameCommandResult.Success>(salvar(input(venue = "Ginásio Novo")))
        assertEquals(2, avisosDeMudanca(vou))
        assertTrue(h.string("SELECT body FROM group_messages WHERE body LIKE '%Ginásio Novo%'").isNotBlank())
        assertEquals("GAME_SCHEDULE_CHANGED", h.string("SELECT event_kind FROM group_messages WHERE body LIKE '%Ginásio Novo%'"))
    }

    @Test fun `mudar so vagas, valor ou recado nao avisa, e salvar de novo igual nao duplica`() {
        val (vou) = elenco()
        assertIs<GameCommandResult.Success>(salvar(input(fee = 3000, notes = "Novo recado", capacity = 5)))
        assertEquals(0, avisosDeMudanca(vou))
        assertIs<GameCommandResult.Success>(salvar(input(shift = Duration.ofHours(2))))
        assertEquals(1, avisosDeMudanca(vou))
        assertIs<GameCommandResult.Success>(salvar(input()))
        assertIs<GameCommandResult.Success>(salvar(input()))
        assertEquals(1, avisosDeMudanca(vou), "salvar igual não repete o aviso")
    }

    @Test fun `o aviso aparece na central mesmo com os avisos do membro desligados`() {
        val (vou) = elenco()
        h.execute("INSERT INTO group_notification_preferences (user_id, notices, push_notices) VALUES ('$vou', false, false)")
        assertIs<GameCommandResult.Success>(salvar(input(shift = Duration.ofHours(3))))
        assertEquals(1, h.int("SELECT count(*) FROM group_notifications n JOIN group_messages m ON m.id=n.message_id WHERE n.recipient_id='$vou' AND n.in_app AND m.body LIKE '%mudou:%'"))
    }

    @Test fun `aumentar as vagas pelo editor promove a fila em ordem de chegada, com diaria e aviso, e o jogo salva junto`() {
        repeat(4) { h.presenca(h.atleta(), "CONFIRMED") }
        val primeiro = h.atleta(); val segundo = h.atleta(); val terceiro = h.atleta()
        h.presenca(primeiro, "WAITLISTED", 1); h.presenca(segundo, "WAITLISTED", 2); h.presenca(terceiro, "WAITLISTED", 3)

        val result = salvar(input(capacity = 6, notes = "Mais duas vagas"))
        assertEquals(6, assertIs<GameCommandResult.Success>(result).game.snapshot.capacity)
        assertEquals(6, atual().snapshot.capacity); assertNull(atual().snapshot.notes) // DEB-051: o recado enviado é ignorado
        assertEquals("CONFIRMED", h.status(primeiro)); assertEquals("CONFIRMED", h.status(segundo))
        assertEquals("WAITLISTED", h.status(terceiro))
        assertEquals(1, h.diarias(primeiro)); assertEquals(1, h.diarias(segundo)); assertEquals(0, h.diarias(terceiro))
        assertEquals(1, h.avisos(primeiro)); assertEquals(1, h.avisos(segundo))
        assertEquals(0, avisosDeMudanca(primeiro), "mudar só as vagas não gera aviso de mudança")
    }

    @Test fun `reduzir as vagas pelo editor nunca derruba ninguem`() {
        h.execute("UPDATE games SET capacity = 8 WHERE id='${h.game}'")
        val confirmados = List(7) { h.atleta().also { id -> h.presenca(id, "CONFIRMED") } }
        val espera = h.atleta(); h.presenca(espera, "WAITLISTED", 1)

        assertEquals(4, assertIs<GameCommandResult.Success>(salvar(input(capacity = 4))).game.snapshot.capacity)
        confirmados.forEach { assertEquals("CONFIRMED", h.status(it)) }
        assertEquals("WAITLISTED", h.status(espera))
    }

    @Test fun `abaixo de 4 vagas recusa e nao grava nada do resto`() {
        val titulo = atual().snapshot.title
        val result = salvar(input(capacity = 3, venue = "Ginásio Novo"))
        assertEquals(listOf("capacity"), assertIs<GameCommandResult.Invalid>(result).errors.map { it.field })
        assertEquals(titulo, atual().snapshot.title); assertEquals("Arena", atual().snapshot.venue.name)
        assertEquals(4, atual().snapshot.capacity); assertEquals(1, atual().version)
    }

    @Test fun `depois do inicio o editor recusa e nem vagas nem aviso mudam`() {
        val (vou) = elenco()
        val depois = Clock.fixed(atual().snapshot.startsAt.plusSeconds(60), java.time.ZoneOffset.UTC)
        assertIs<GameCommandResult.InvalidTransition>(salvar(input(capacity = 8, venue = "Ginásio Novo"), depois))
        assertEquals(4, atual().snapshot.capacity); assertEquals(0, avisosDeMudanca(vou))
    }
}
