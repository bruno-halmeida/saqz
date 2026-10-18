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
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test fun `create refuses 3 spots and accepts 4 and 100 without persisting the refused one`() {
        val fixture = fixture(GroupRole.OWNER)
        val refused = assertIs<GameCommandResult.Invalid>(fixture.create.execute(actor, groupId, gameId, createInput(capacity = 3)))
        assertEquals(listOf("capacity"), refused.errors.map { it.field })
        assertTrue(fixture.repository.creates.isEmpty())

        listOf(4, 100).forEach { capacity ->
            val saved = assertIs<GameCommandResult.Success>(fixture.create.execute(actor, groupId, UUID.randomUUID(), createInput(capacity = capacity)))
            assertEquals(capacity, saved.game.snapshot.capacity)
        }
    }

    @Test fun `create from an old group default with 3 spots uses the minimum of 4`() {
        val fixture = fixture(GroupRole.OWNER)
        fixture.repository.creation = fixture.repository.creation!!.copy(defaults = defaults().copy(capacity = 3))

        val saved = assertIs<GameCommandResult.Success>(fixture.create.execute(actor, groupId, gameId, createInput()))

        assertEquals(4, saved.game.snapshot.capacity)
    }

    @Test fun `manual create reports the recurring occurrence at the same instant`() {
        val fixture = fixture(GroupRole.OWNER)
        val existing = UUID.randomUUID()
        fixture.repository.recurringConflict = existing

        val result = fixture.create.execute(actor, groupId, gameId, createInput())

        assertEquals(GameCommandResult.ScheduleConflict(existing), result)
        assertTrue(fixture.repository.creates.isEmpty())
    }

    @Test fun `database schedule conflict from create is exposed as a command conflict`() {
        val fixture = fixture(GroupRole.OWNER)
        val existing = UUID.randomUUID()
        fixture.repository.writeResult = GameWriteResult.ScheduleConflict(existing)

        assertEquals(
            GameCommandResult.ScheduleConflict(existing),
            fixture.create.execute(actor, groupId, gameId, createInput()),
        )
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

    @Test fun `game past its start time is no longer editable`() {
        val fixture = fixture(GroupRole.OWNER, clock = java.time.Clock.fixed(START, java.time.ZoneOffset.UTC))
        val result = fixture.edit.execute(actor, groupId, gameId, 1, validDraft(title = "Novo título"))

        assertIs<GameCommandResult.InvalidTransition>(result)
        assertTrue(fixture.repository.updates.isEmpty())
    }

    @Test fun `draft edit stores immutable replacement and schedules only after success`() {
        val fixture = fixture(GroupRole.OWNER)
        val result = fixture.edit.execute(actor, groupId, gameId, 1, validDraft(title = "Novo título"))

        assertEquals("Novo título", assertIs<GameCommandResult.Success>(result).game.snapshot.title)
        assertEquals(1, fixture.repository.updates.size)
        assertEquals(setOf(GameSideEffect.SCHEDULE_CHANGED), fixture.effects.calls.single().second)
    }

    @Test fun `edit reports the recurring occurrence at the same instant`() {
        val fixture = fixture(GroupRole.OWNER)
        val existing = UUID.randomUUID()
        fixture.repository.recurringConflict = existing

        val result = fixture.edit.execute(actor, groupId, gameId, 1, validDraft())

        assertEquals(GameCommandResult.ScheduleConflict(existing), result)
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `edit does not report the game itself as a recurring conflict`() {
        val fixture = fixture(GroupRole.OWNER)
        fixture.repository.recurringConflict = gameId

        val result = fixture.edit.execute(actor, groupId, gameId, 1, validDraft())

        assertTrue(result is GameCommandResult.Success)
        assertEquals(1, fixture.repository.updates.size)
    }

    @Test fun `published game remains editable`() {
        val fixture = fixture(GroupRole.ADMIN, status = GameStatus.PUBLISHED)
        assertTrue(fixture.edit.execute(actor, groupId, gameId, 1, validDraft()) is GameCommandResult.Success)
    }

    @Test fun `saving an edit refuses 3 spots even on a legacy game and accepts 4`() {
        val fixture = fixture(GroupRole.OWNER)
        val refused = assertIs<GameCommandResult.Invalid>(
            fixture.edit.execute(actor, groupId, gameId, 1, validDraft().copy(capacity = 3)),
        )
        assertEquals(listOf("capacity"), refused.errors.map { it.field })
        assertTrue(fixture.repository.updates.isEmpty())
        assertTrue(fixture.effects.calls.isEmpty())

        val saved = assertIs<GameCommandResult.Success>(fixture.edit.execute(actor, groupId, gameId, 1, validDraft().copy(capacity = 4)))
        assertEquals(4, saved.game.snapshot.capacity)
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
                GameSideEffect.PARTICIPANTS_NOTIFIED_OF_CANCELLATION,
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

    @Test fun `draft can be cancelled without notifying anyone`() {
        val fixture = fixture(GroupRole.OWNER)
        val result = fixture.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.CANCEL)

        assertEquals(GameStatus.CANCELLED, assertIs<GameCommandResult.Success>(result).game.status)
        assertEquals(1, fixture.repository.updates.size)
        assertTrue(fixture.effects.calls.isEmpty())
    }

    @Test fun `publish refuses an elapsed confirmation deadline but the series release does not`() {
        val late = java.time.Clock.fixed(START.minusSeconds(1800), java.time.ZoneOffset.UTC)
        val manual = fixture(GroupRole.OWNER, clock = late)
        val refused = manual.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.PUBLISH)
        assertEquals(listOf("confirmationDeadline"), assertIs<GameCommandResult.Invalid>(refused).errors.map { it.field })
        assertTrue(manual.repository.updates.isEmpty())

        val release = fixture(GroupRole.OWNER, clock = late)
        val released = release.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.PUBLISH, enforceTiming = false)
        assertEquals(GameStatus.PUBLISHED, assertIs<GameCommandResult.Success>(released).game.status)
    }

    // DEB-075: nenhum jogo começa no limite do plano (0h do dia do fim) ou depois.

    private fun limitAt(limit: Instant?) = object : GameCreationHorizon {
        override fun until(groupId: UUID): Instant? = limit
    }

    @Test fun `create refuses a game starting at the plan limit or later and accepts one a minute before`() {
        val refused = fixture(GroupRole.OWNER, horizon = limitAt(START))
        assertEquals(GameCommandResult.AfterPlanEnd(START), refused.create.execute(actor, groupId, gameId, createInput()))
        assertTrue(refused.repository.creates.isEmpty())

        val later = fixture(GroupRole.OWNER, horizon = limitAt(START.minusSeconds(3600)))
        assertEquals(GameCommandResult.AfterPlanEnd(START.minusSeconds(3600)), later.create.execute(actor, groupId, gameId, createInput()))

        val accepted = fixture(GroupRole.OWNER, horizon = limitAt(START.plusSeconds(60)))
        assertIs<GameCommandResult.Success>(accepted.create.execute(actor, groupId, gameId, createInput()))
    }

    @Test fun `create without a plan is a subscription requirement and writes nothing`() {
        val fixture = fixture(GroupRole.OWNER, horizon = limitAt(null))
        assertFailsWith<SubscriptionRequiredException> { fixture.create.execute(actor, groupId, gameId, createInput()) }
        assertTrue(fixture.repository.creates.isEmpty())
    }

    @Test fun `validation and the clock come before the plan limit and the schedule conflict after it`() {
        val invalid = fixture(GroupRole.OWNER, horizon = limitAt(START.minusSeconds(60)))
        assertIs<GameCommandResult.Invalid>(invalid.create.execute(actor, groupId, gameId, createInput(capacity = 3)))
        val past = fixture(GroupRole.OWNER, clock = java.time.Clock.fixed(START.plusSeconds(60), java.time.ZoneOffset.UTC), horizon = limitAt(START.minusSeconds(60)))
        assertIs<GameCommandResult.Invalid>(past.create.execute(actor, groupId, gameId, createInput(deadline = START.minusSeconds(7200))))

        val conflicting = fixture(GroupRole.OWNER, horizon = limitAt(START))
        conflicting.repository.recurringConflict = UUID.randomUUID()
        assertEquals(GameCommandResult.AfterPlanEnd(START), conflicting.create.execute(actor, groupId, gameId, createInput()))
    }

    @Test fun `edit that moves the start to the limit or later is refused with no write notice or effect`() {
        val notices = mutableListOf<Instant>()
        val transaction = ImmediateTransaction()
        val repository = FakeRepository(null, GameCommandContext(GroupRole.OWNER, Game(gameId, groupId, validSnapshot(), GameStatus.PUBLISHED, 1)))
        val effects = FakeEffects()
        val clock = java.time.Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), java.time.ZoneOffset.UTC)
        val edit = EditGame(transaction, repository, effects, clock, scheduleNotice = { _, _, saved -> notices += saved.snapshot.startsAt }, horizon = limitAt(START.plusSeconds(3600)))

        val moved = edit.execute(actor, groupId, gameId, 1, validDraft().copy(startsAt = START.plusSeconds(3600), localTime = LocalTime.of(20, 30), confirmationDeadline = START))

        assertEquals(GameCommandResult.AfterPlanEnd(START.plusSeconds(3600)), moved)
        assertTrue(repository.updates.isEmpty())
        assertTrue(effects.calls.isEmpty())
        assertTrue(notices.isEmpty())
        // Um minuto antes do limite passa.
        assertIs<GameCommandResult.Success>(edit.execute(actor, groupId, gameId, 1, validDraft().copy(startsAt = START.plusSeconds(3540), localTime = LocalTime.of(20, 29), confirmationDeadline = START)))
    }

    @Test fun `edit of a legacy game beyond the limit still saves when the start does not change`() {
        val fixture = fixture(GroupRole.OWNER, horizon = limitAt(START.minusSeconds(86_400)))
        assertIs<GameCommandResult.Success>(fixture.edit.execute(actor, groupId, gameId, 1, validDraft("Novo título")))
        assertEquals(1, fixture.repository.updates.size)
    }

    @Test fun `publish is refused beyond the limit with or without the timing check and cancel ignores the limit`() {
        val beyond = limitAt(START)
        val manual = fixture(GroupRole.OWNER, horizon = beyond)
        assertEquals(GameCommandResult.AfterPlanEnd(START), manual.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.PUBLISH))
        val release = fixture(GroupRole.OWNER, horizon = beyond)
        assertEquals(GameCommandResult.AfterPlanEnd(START), release.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.PUBLISH, enforceTiming = false))
        assertTrue(manual.repository.updates.isEmpty() && release.repository.updates.isEmpty())
        assertTrue(manual.effects.calls.isEmpty())

        val inside = fixture(GroupRole.OWNER, horizon = limitAt(START.plusSeconds(60)))
        assertIs<GameCommandResult.Success>(inside.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.PUBLISH))

        val cancel = fixture(GroupRole.OWNER, status = GameStatus.PUBLISHED, horizon = limitAt(null))
        assertIs<GameCommandResult.Success>(cancel.lifecycle.execute(actor, groupId, gameId, 1, GameMutation.CANCEL))
    }

    @Test fun `edit checks the clock only for the fields it changes`() {
        val clock = java.time.Clock.fixed(START.minusSeconds(1800), java.time.ZoneOffset.UTC)
        val fixture = fixture(GroupRole.OWNER, clock = clock)
        val untouched = fixture.edit.execute(actor, groupId, gameId, 1, validDraft("Novo título"))
        assertIs<GameCommandResult.Success>(untouched)

        val moved = fixture.edit.execute(actor, groupId, gameId, 1, validDraft().copy(confirmationDeadline = START.minusSeconds(7200)))
        assertEquals(listOf("confirmationDeadline"), assertIs<GameCommandResult.Invalid>(moved).errors.map { it.field })
    }

    @Test fun `create refuses a game that starts in the past`() {
        val fixture = fixture(GroupRole.OWNER, clock = java.time.Clock.fixed(START.plusSeconds(60), java.time.ZoneOffset.UTC))
        val result = fixture.create.execute(actor, groupId, gameId, createInput(deadline = START.minusSeconds(7200)))

        val errors = assertIs<GameCommandResult.Invalid>(result).errors
        assertTrue(errors.any { it.field == "startsAt" })
        assertTrue(errors.any { it.field == "confirmationDeadline" })
        assertTrue(fixture.repository.creates.isEmpty())
    }

    @Test fun `create refuses an explicit deadline in the past`() {
        val fixture = fixture(GroupRole.OWNER, clock = java.time.Clock.fixed(START.minusSeconds(3600), java.time.ZoneOffset.UTC))
        val result = fixture.create.execute(actor, groupId, gameId, createInput(deadline = START.minusSeconds(7200)))

        assertEquals(listOf("confirmationDeadline"), assertIs<GameCommandResult.Invalid>(result).errors.map { it.field })
    }

    @Test fun `derived default deadline that already passed becomes the game start without priority window`() {
        val now = START.minusSeconds(3600)
        val fixture = fixture(GroupRole.OWNER, clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC))
        val result = fixture.create.execute(actor, groupId, gameId, createInput())

        assertEquals(START, assertIs<GameCommandResult.Success>(result).game.snapshot.confirmationDeadline)
    }

    @Test fun `create refuses startsAt that disagrees with local date time and zone`() {
        val fixture = fixture(GroupRole.OWNER)
        val result = fixture.create.execute(actor, groupId, gameId, createInput(startsAt = START.plusSeconds(3600)))

        assertEquals(listOf("startsAt"), assertIs<GameCommandResult.Invalid>(result).errors.map { it.field })
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
        clock: java.time.Clock = java.time.Clock.fixed(java.time.Instant.parse("2026-08-01T12:00:00Z"), java.time.ZoneOffset.UTC),
        horizon: GameCreationHorizon = GameCreationHorizon.Unlimited,
    ): Fixture {
        val transaction = ImmediateTransaction()
        val repository = FakeRepository(
            GameCreationContext(role, defaults),
            GameCommandContext(role, Game(gameId, groupId, validSnapshot(), status, version)),
        )
        val effects = FakeEffects()
        return Fixture(
            CreateGame(transaction, repository, clock, horizon),
            EditGame(transaction, repository, effects, clock, horizon = horizon),
            ChangeGameLifecycle(transaction, repository, effects, clock, horizon),
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
        var recurringConflict: UUID? = null

        override fun creationContext(actor: UUID, groupId: UUID) = creation
        override fun recurringConflict(groupId: UUID, startsAt: Instant, durationMinutes: Int, excludingGameId: UUID?) =
            recurringConflict?.takeUnless { it == excludingGameId }
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
        override fun apply(game: Game, actorId: UUID, effects: Set<GameSideEffect>) { calls += game.id to effects }
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

    private fun createInput(
        title: String? = null,
        capacity: Int? = null,
        startsAt: Instant = START,
        deadline: Instant? = null,
    ) = CreateGameInput(
        title = title,
        localDate = LocalDate.of(2026, 8, 12),
        localTime = LocalTime.of(19, 30),
        zoneId = "America/Sao_Paulo",
        startsAt = startsAt,
        capacity = capacity,
        confirmationDeadline = deadline,
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
