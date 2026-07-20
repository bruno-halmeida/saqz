package br.com.saqz.groups.application.game

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.CreateGameInput
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.game.GameDraftValidation
import br.com.saqz.groups.domain.game.GameDraftValidator
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueInput
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.GroupGameDefaults
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameCommandsTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val gameId = UUID.randomUUID()

    @Test fun `owner creates draft from copied defaults`() {
        val fixture = fixture(GroupRole.OWNER)
        val result = fixture.create.execute(actor, groupId, gameId, createInput())

        val saved = assertIs<GameCommandResult.Success>(result).game
        assertEquals(GameStatus.DRAFT, saved.status)
        assertEquals("Arena Central", saved.snapshot.venue.name)
        assertEquals(24, saved.snapshot.capacity)
        assertEquals(START.minusSeconds(180 * 60), saved.snapshot.confirmationDeadline)
        assertEquals(2500, saved.snapshot.gameFeeCents)
        assertEquals(saved, fixture.repository.creates.single())
    }

    @Test fun `create overrides do not mutate stored group defaults`() {
        val fixture = fixture(GroupRole.OWNER)
        fixture.create.execute(actor, groupId, gameId, createInput(title = "Final", capacity = 30))

        assertEquals("Final", fixture.repository.creates.single().snapshot.title)
        assertEquals(30, fixture.repository.creates.single().snapshot.capacity)
        assertEquals("Treino padrão", fixture.repository.creation?.defaults?.title)
        assertEquals(24, fixture.repository.creation?.defaults?.capacity)
    }

    @Test fun `admin creates game`() {
        assertTrue(fixture(GroupRole.ADMIN).create.execute(actor, groupId, gameId, createInput()) is GameCommandResult.Success)
    }

    @Test fun `athlete cannot create and causes no write`() {
        val fixture = fixture(GroupRole.ATHLETE)
        assertSame(GameCommandResult.AccessForbidden, fixture.create.execute(actor, groupId, gameId, createInput()))
        assertTrue(fixture.repository.creates.isEmpty())
    }

    @Test fun `nonmember create is privacy preserving not found`() {
        val fixture = fixture(role = null)
        assertSame(GameCommandResult.GroupNotFound, fixture.create.execute(actor, groupId, gameId, createInput()))
        assertTrue(fixture.repository.creates.isEmpty())
    }

    @Test fun `missing group creates no write`() {
        val fixture = fixture(GroupRole.OWNER)
        fixture.repository.creation = null
        assertSame(GameCommandResult.GroupNotFound, fixture.create.execute(actor, groupId, gameId, createInput()))
        assertTrue(fixture.repository.creates.isEmpty())
    }

    @Test fun `create reports all invalid fields before persistence`() {
        val fixture = fixture(GroupRole.OWNER, defaults = GroupGameDefaults())
        val result = assertIs<GameCommandResult.Invalid>(
            fixture.create.execute(actor, groupId, gameId, CreateGameInput(null, null, null, null, null, null)),
        )

        assertEquals(
            setOf("title", "venue", "localDate", "localTime", "zoneId", "startsAt", "durationMinutes", "capacity", "confirmationDeadline"),
            result.errors.map { it.field }.toSet(),
        )
        assertTrue(fixture.repository.creates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `draft edit stores immutable replacement and schedules only after success`() {
        val fixture = fixture(GroupRole.OWNER)
        val result = fixture.edit.execute(actor, groupId, gameId, 1, validDraft(title = "Novo título"))

        assertEquals("Novo título", assertIs<GameCommandResult.Success>(result).game.snapshot.title)
        assertEquals(1, fixture.repository.updates.size)
        assertEquals(setOf(GameSideEffect.SCHEDULE_CHANGED), fixture.effects.calls.single().second)
    }

    @Test fun `published game remains editable`() {
        val fixture = fixture(GroupRole.ADMIN, status = GameStatus.PUBLISHED)
        assertTrue(fixture.edit.execute(actor, groupId, gameId, 1, validDraft()) is GameCommandResult.Success)
    }

    @Test fun `invalid edit reports all fields with no write or side effect`() {
        val fixture = fixture(GroupRole.OWNER)
        val result = fixture.edit.execute(actor, groupId, gameId, 1, emptyDraft())

        assertTrue(result is GameCommandResult.Invalid)
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `cancelled and completed games are read only`() {
        listOf(GameStatus.CANCELLED, GameStatus.COMPLETED).forEach { status ->
            val fixture = fixture(GroupRole.OWNER, status = status)
            assertEquals(
                GameCommandResult.InvalidTransition(status, GameMutation.EDIT),
                fixture.edit.execute(actor, groupId, gameId, 1, validDraft()),
            )
            assertTrue(fixture.repository.updates.isEmpty())
            assertTrue(fixture.effects.calls.isEmpty())
        }
    }

    @Test fun `stale edit returns conflict before validation and effects`() {
        val fixture = fixture(GroupRole.OWNER, version = 2)
        assertSame(GameCommandResult.VersionConflict, fixture.edit.execute(actor, groupId, gameId, 1, emptyDraft()))
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `compare and set loss returns conflict without effects`() {
        val fixture = fixture(GroupRole.OWNER)
        fixture.repository.writeResult = GameWriteResult.VersionConflict
        assertSame(GameCommandResult.VersionConflict, fixture.edit.execute(actor, groupId, gameId, 1, validDraft()))
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `publish changes draft and opens attendance after write`() {
        val fixture = fixture(GroupRole.OWNER)
        val result = fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.PUBLISH)

        assertEquals(GameStatus.PUBLISHED, assertIs<GameCommandResult.Success>(result).game.status)
        assertEquals(setOf(GameSideEffect.SCHEDULE_CHANGED, GameSideEffect.ATTENDANCE_OPENED), fixture.effects.calls.single().second)
    }

    @Test fun `cancel freezes attendance and records finance work after write`() {
        val fixture = fixture(GroupRole.ADMIN, status = GameStatus.PUBLISHED)
        fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.CANCEL)

        assertEquals(
            setOf(
                GameSideEffect.SCHEDULE_CHANGED,
                GameSideEffect.ATTENDANCE_FROZEN,
                GameSideEffect.PENDING_CHARGES_CANCELLED,
                GameSideEffect.FINANCE_REVIEW_MARKED,
            ),
            fixture.effects.calls.single().second,
        )
    }

    @Test fun `complete freezes attendance after successful published transition`() {
        val fixture = fixture(GroupRole.OWNER, status = GameStatus.PUBLISHED)
        val result = fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.COMPLETE)

        assertEquals(GameStatus.COMPLETED, assertIs<GameCommandResult.Success>(result).game.status)
        assertEquals(setOf(GameSideEffect.ATTENDANCE_FROZEN), fixture.effects.calls.single().second)
    }

    @Test fun `draft cannot complete and emits no side effects`() {
        val fixture = fixture(GroupRole.OWNER)
        assertEquals(
            GameCommandResult.InvalidTransition(GameStatus.DRAFT, GameMutation.COMPLETE),
            fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.COMPLETE),
        )
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `unpublished draft cannot be cancelled into member history`() {
        val fixture = fixture(GroupRole.OWNER)
        assertEquals(
            GameCommandResult.InvalidTransition(GameStatus.DRAFT, GameMutation.CANCEL),
            fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.CANCEL),
        )
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `published game cannot publish twice`() {
        val fixture = fixture(GroupRole.OWNER, status = GameStatus.PUBLISHED)
        assertTrue(
            fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.PUBLISH) is GameCommandResult.InvalidTransition,
        )
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `athlete lifecycle command is forbidden without write or effects`() {
        val fixture = fixture(GroupRole.ATHLETE, status = GameStatus.PUBLISHED)
        assertSame(
            GameCommandResult.AccessForbidden,
            fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.CANCEL),
        )
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `stale lifecycle command causes no finance attendance or schedule effect`() {
        val fixture = fixture(GroupRole.OWNER, status = GameStatus.PUBLISHED, version = 2)
        assertSame(
            GameCommandResult.VersionConflict,
            fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.CANCEL),
        )
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    private fun fixture(
        role: GroupRole?,
        status: GameStatus = GameStatus.DRAFT,
        version: Long = 1,
        defaults: GroupGameDefaults = defaults(),
    ): Fixture {
        val transaction = ImmediateTransaction()
        val repository = FakeRepository(
            GameCreationContext(role, defaults),
            GameCommandContext(role, Game(gameId, groupId, validSnapshot(), status, version)),
        )
        val effects = FakeEffects()
        return Fixture(
            CreateGame(transaction, repository),
            EditGame(transaction, repository, effects),
            ChangeGameLifecycle(transaction, repository, effects),
            repository,
            effects,
        )
    }

    private class ImmediateTransaction : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class FakeRepository(
        var creation: GameCreationContext?,
        var current: GameCommandContext?,
    ) : GameCommandRepository {
        val creates = mutableListOf<Game>()
        val updates = mutableListOf<Pair<Game, Long>>()
        var writeResult: GameWriteResult? = null

        override fun creationContext(actor: UUID, groupId: UUID) = creation
        override fun find(actor: UUID, groupId: UUID, gameId: UUID) = current
        override fun create(game: Game): GameWriteResult {
            creates += game
            return writeResult ?: GameWriteResult.Saved(game)
        }
        override fun update(game: Game, expectedVersion: Long): GameWriteResult {
            updates += game to expectedVersion
            return writeResult ?: GameWriteResult.Saved(game.copy(version = expectedVersion + 1))
        }
    }

    private class FakeEffects : GameSideEffectPort {
        val calls = mutableListOf<Pair<UUID, Set<GameSideEffect>>>()
        override fun apply(gameId: UUID, effects: Set<GameSideEffect>) { calls += gameId to effects }
    }

    private data class Fixture(
        val create: CreateGame,
        val edit: EditGame,
        val lifecycle: ChangeGameLifecycle,
        val repository: FakeRepository,
        val effects: FakeEffects,
    )

    private fun validSnapshot() = assertIs<GameDraftValidation.Valid>(GameDraftValidator.validate(validDraft())).snapshot

    private fun validDraft(title: String = "Treino de terça") = GameDraftInput(
        title,
        GameVenueInput(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2"),
        LocalDate.of(2026, 8, 12),
        LocalTime.of(19, 30),
        "America/Sao_Paulo",
        START,
        90,
        24,
        START.minusSeconds(3600),
        2500,
        "Levar bola",
    )

    private fun emptyDraft() = GameDraftInput(null, null, null, null, null, null, null, null, null)

    private fun createInput(title: String? = null, capacity: Int? = null) = CreateGameInput(
        title = title,
        localDate = LocalDate.of(2026, 8, 12),
        localTime = LocalTime.of(19, 30),
        zoneId = "America/Sao_Paulo",
        startsAt = START,
        capacity = capacity,
    )

    private fun defaults() = GroupGameDefaults(
        title = "Treino padrão",
        venue = GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2"),
        durationMinutes = 90,
        capacity = 24,
        confirmationLeadMinutes = 180,
        gameFeeCents = 2500,
    )

    private companion object {
        val START: Instant = Instant.parse("2026-08-12T22:30:00Z")
    }
}
