package br.com.saqz.groups.application.game

import br.com.saqz.groups.application.attendance.CapacityCommandResult
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.game.GameDraftValidation
import br.com.saqz.groups.domain.game.GameDraftValidator
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueInput
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** DEB-046 (aviso de mudança de dia, hora ou local) e DEB-047 (vagas do editor pelo caminho do "Ajustar vagas"). */
class EditGameScheduleAndCapacityTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val start = Instant.parse("2026-08-12T22:30:00Z")
    private val clock = java.time.Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), java.time.ZoneOffset.UTC)

    private val base = draft()

    private fun draft(
        date: LocalDate = LocalDate.of(2026, 8, 12),
        time: LocalTime = LocalTime.of(19, 30),
        startsAt: Instant = start,
        venue: String = "Arena Central",
        court: String? = "Quadra 2",
        capacity: Int = 24,
        fee: Long = 2500,
        notes: String = "Levar bola",
    ) = GameDraftInput(
        "Treino de terça", GameVenueInput(UUID.randomUUID(), venue, "Rua das Flores 100", court), date, time,
        "America/Sao_Paulo", startsAt, 90, capacity, start.minusSeconds(3600), fee, notes,
    )

    private class Repo(var game: Game, val role: GroupRole = GroupRole.OWNER) : GameCommandRepository {
        val updates = mutableListOf<Game>()
        override fun creationContext(actor: UUID, groupId: UUID): GameCreationContext? = null
        override fun recurringConflict(groupId: UUID, startsAt: Instant, excludingGameId: UUID?): UUID? = null
        override fun find(actor: UUID, groupId: UUID, gameId: UUID) = GameCommandContext(role, game)
        override fun create(game: Game): GameWriteResult = GameWriteResult.Saved(game)
        override fun update(game: Game, expectedVersion: Long): GameWriteResult {
            updates += game
            this.game = game.copy(version = expectedVersion + 1)
            return GameWriteResult.Saved(this.game)
        }
    }

    private class Adjuster(val result: (Int) -> CapacityCommandResult) : GameCapacityAdjuster {
        val calls = mutableListOf<Triple<Long, Int, Unit>>()
        override fun adjust(actor: UUID, groupId: UUID, gameId: UUID, expectedVersion: Long, capacity: Int): CapacityCommandResult {
            calls += Triple(expectedVersion, capacity, Unit)
            return result(capacity)
        }
    }

    private class Notices : GameScheduleChangeNotice {
        val calls = mutableListOf<Pair<GameSnapshot, Game>>()
        override fun changed(actor: UUID, before: GameSnapshot, after: Game) { calls += before to after }
    }

    private class Tx : TransactionRunner {
        var rolledBack = false
        override fun <T> inTransaction(block: () -> T): T = try { block() } catch (failure: RuntimeException) { rolledBack = true; throw failure }
    }

    private fun snapshot(input: GameDraftInput) = assertIs<GameDraftValidation.Valid>(GameDraftValidator.validate(input)).snapshot
    private fun published(input: GameDraftInput = base, status: GameStatus = GameStatus.PUBLISHED) =
        Game(gameId, groupId, snapshot(input), status, 1)

    private class Rig(val repo: Repo, val adjuster: Adjuster, val notices: Notices, val tx: Tx, val edit: EditGame)

    private fun rig(
        status: GameStatus = GameStatus.PUBLISHED,
        result: (Int) -> CapacityCommandResult = { CapacityCommandResult.Success(it, 3, emptyList()) },
    ): Rig {
        val repo = Repo(published(status = status))
        val adjuster = Adjuster(result); val notices = Notices(); val tx = Tx()
        val edit = EditGame(tx, repo, { _, _, _ -> }, clock, adjuster, notices)
        return Rig(repo, adjuster, notices, tx, edit)
    }

    @Test fun `mudar o dia avisa uma vez com o jogo antigo e o novo`() {
        val r = rig()
        val result = r.edit.execute(actor, groupId, gameId, 1, draft(date = LocalDate.of(2026, 8, 13), startsAt = start.plusSeconds(86400)))
        assertIs<GameCommandResult.Success>(result)
        assertEquals(1, r.notices.calls.size)
        assertEquals(LocalDate.of(2026, 8, 12), r.notices.calls.single().first.localDate)
        assertEquals(LocalDate.of(2026, 8, 13), r.notices.calls.single().second.snapshot.localDate)
    }

    @Test fun `mudar a hora ou o local avisa`() {
        val hora = rig()
        hora.edit.execute(actor, groupId, gameId, 1, draft(time = LocalTime.of(20, 0), startsAt = start.plusSeconds(1800)))
        assertEquals(1, hora.notices.calls.size)
        val local = rig()
        local.edit.execute(actor, groupId, gameId, 1, draft(venue = "Ginásio Novo"))
        assertEquals(1, local.notices.calls.size)
        val quadra = rig()
        quadra.edit.execute(actor, groupId, gameId, 1, draft(court = "Quadra 3"))
        assertEquals(1, quadra.notices.calls.size)
    }

    @Test fun `mudar so vagas valor ou recado nao avisa`() {
        val r = rig()
        assertIs<GameCommandResult.Success>(r.edit.execute(actor, groupId, gameId, 1, draft(capacity = 30, fee = 3000, notes = "Sem bola")))
        assertTrue(r.notices.calls.isEmpty())
    }

    @Test fun `salvar de novo igual nao avisa`() {
        val r = rig()
        assertIs<GameCommandResult.Success>(r.edit.execute(actor, groupId, gameId, 1, draft()))
        assertTrue(r.notices.calls.isEmpty())
        r.edit.execute(actor, groupId, gameId, 2, draft(venue = "Ginásio Novo"))
        r.edit.execute(actor, groupId, gameId, 3, draft(venue = "Ginásio Novo"))
        assertEquals(1, r.notices.calls.size)
    }

    @Test fun `rascunho editado nao avisa ninguem e grava a capacidade direto`() {
        val r = rig(status = GameStatus.DRAFT)
        r.edit.execute(actor, groupId, gameId, 1, draft(venue = "Ginásio Novo", capacity = 30))
        assertTrue(r.notices.calls.isEmpty())
        assertTrue(r.adjuster.calls.isEmpty())
        assertEquals(30, r.repo.game.snapshot.capacity)
    }

    @Test fun `vagas diferentes em jogo publicado passam pelo ajuste de vagas depois de gravar o resto com a capacidade antiga`() {
        val r = rig()
        val result = r.edit.execute(actor, groupId, gameId, 1, draft(capacity = 30, notes = "Novo recado"))
        assertIs<GameCommandResult.Success>(result)
        assertEquals(24, r.repo.updates.single().snapshot.capacity, "o resto é gravado sem mexer nas vagas")
        assertEquals("Novo recado", r.repo.updates.single().snapshot.notes)
        assertEquals(listOf(Triple(2L, 30, Unit)), r.adjuster.calls, "ajuste com a versão nova e a capacidade pedida")
    }

    @Test fun `vagas iguais nao chamam o ajuste`() {
        val r = rig()
        r.edit.execute(actor, groupId, gameId, 1, draft(notes = "Outro recado"))
        assertTrue(r.adjuster.calls.isEmpty())
    }

    @Test fun `ajuste recusado desfaz o salvamento inteiro e nao avisa`() {
        val cases = mapOf(
            CapacityCommandResult.Conflict to GameCommandResult.VersionConflict,
            CapacityCommandResult.Forbidden to GameCommandResult.AccessForbidden,
            CapacityCommandResult.Hidden to GameCommandResult.GameNotFound,
        )
        for ((refusal, expected) in cases) {
            val r = rig(result = { refusal })
            val result = r.edit.execute(actor, groupId, gameId, 1, draft(venue = "Ginásio Novo", capacity = 30))
            assertEquals(expected, result)
            assertTrue(r.tx.rolledBack, "a transação precisa voltar")
            assertTrue(r.notices.calls.isEmpty())
        }
        val frozen = rig(result = { CapacityCommandResult.Frozen })
        assertIs<GameCommandResult.InvalidTransition>(frozen.edit.execute(actor, groupId, gameId, 1, draft(capacity = 30)))
        val invalid = rig(result = { CapacityCommandResult.InvalidCapacity })
        assertEquals(listOf("capacity"), assertIs<GameCommandResult.Invalid>(invalid.edit.execute(actor, groupId, gameId, 1, draft(capacity = 30))).errors.map { it.field })
    }

    @Test fun `abaixo do minimo de 4 o editor recusa sem gravar nada`() {
        val r = rig()
        val result = r.edit.execute(actor, groupId, gameId, 1, draft(capacity = 3))
        assertEquals(listOf("capacity"), assertIs<GameCommandResult.Invalid>(result).errors.map { it.field })
        assertTrue(r.repo.updates.isEmpty() && r.adjuster.calls.isEmpty() && r.notices.calls.isEmpty())
    }

    @Test fun `depois do inicio o editor recusa e nao avisa nem ajusta`() {
        val repo = Repo(published())
        val adjuster = Adjuster { CapacityCommandResult.Success(it, 2, emptyList()) }; val notices = Notices()
        val late = java.time.Clock.fixed(start.plusSeconds(60), java.time.ZoneOffset.UTC)
        val result = EditGame(Tx(), repo, { _, _, _ -> }, late, adjuster, notices).execute(actor, groupId, gameId, 1, draft(capacity = 30))
        assertIs<GameCommandResult.InvalidTransition>(result)
        assertTrue(repo.updates.isEmpty() && adjuster.calls.isEmpty() && notices.calls.isEmpty())
    }
}
