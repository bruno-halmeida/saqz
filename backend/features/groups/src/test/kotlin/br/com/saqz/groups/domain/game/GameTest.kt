package br.com.saqz.groups.domain.game

import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {
    @Test fun `validator reports every missing required field`() {
        val result = assertIs<GameDraftValidation.Invalid>(GameDraftValidator.validate(emptyInput()))

        assertEquals(
            setOf("title", "venue", "localDate", "localTime", "zoneId", "startsAt", "durationMinutes", "capacity", "confirmationDeadline"),
            result.errors.map { it.field }.toSet(),
        )
    }

    @Test fun `valid snapshot trims text and keeps resolved local values`() {
        val snapshot = valid(validInput(title = "  Treino oficial  ", notes = "  Levar bola  "))

        assertEquals("Treino oficial", snapshot.title)
        assertEquals("Levar bola", snapshot.notes)
        assertEquals(LocalDate.of(2026, 8, 12), snapshot.localDate)
        assertEquals(LocalTime.of(19, 30), snapshot.localTime)
        assertEquals("America/Sao_Paulo", snapshot.zoneId.value)
    }

    @Test fun `title enforces exact code point limits`() {
        assertInvalid(validInput(title = "X"), "title")
        assertInvalid(validInput(title = "x".repeat(121)), "title")
        assertEquals("🏐🏐", valid(validInput(title = "🏐🏐")).title)
    }

    @Test fun `venue reports name address and court limits together`() {
        val result = assertIs<GameDraftValidation.Invalid>(
            GameDraftValidator.validate(
                validInput(venue = GameVenueInput(name = "X", address = "Rua", court = "x".repeat(81))),
            ),
        )
        assertEquals(setOf("venue.name", "venue.address", "venue.court"), result.errors.map { it.field }.toSet())
    }

    @Test fun `duration and capacity report both invalid ranges`() {
        val result = assertIs<GameDraftValidation.Invalid>(
            GameDraftValidator.validate(validInput(durationMinutes = 14, capacity = 101)),
        )
        assertEquals(setOf("durationMinutes", "capacity"), result.errors.map { it.field }.toSet())
    }

    @Test fun `deadline may equal start but not follow it`() {
        val start = Instant.parse("2026-08-12T22:30:00Z")
        assertEquals(start, valid(validInput(startsAt = start, confirmationDeadline = start)).confirmationDeadline)
        assertInvalid(validInput(startsAt = start, confirmationDeadline = start.plusSeconds(1)), "confirmationDeadline")
    }

    @Test fun `optional fee enforces positive BRL cents limit`() {
        assertInvalid(validInput(gameFeeCents = 0), "gameFeeCents")
        assertInvalid(validInput(gameFeeCents = 100_000_000), "gameFeeCents")
        assertEquals(99_999_999, valid(validInput(gameFeeCents = 99_999_999)).gameFeeCents)
    }

    @Test fun `blank notes normalize to null and nonblank notes enforce limits`() {
        assertNull(valid(validInput(notes = "   ")).notes)
        assertInvalid(validInput(notes = "X"), "notes")
        assertInvalid(validInput(notes = "x".repeat(501)), "notes")
    }

    @Test fun `text control characters are rejected`() {
        assertInvalid(validInput(title = "Jogo\nfinal"), "title")
        assertInvalid(validInput(venue = validVenue().copy(address = "Rua\tCentral 100")), "venue.address")
    }

    @Test fun `invalid timezone reports field without losing other errors`() {
        val result = assertIs<GameDraftValidation.Invalid>(
            GameDraftValidator.validate(validInput(zoneId = "Mars/Olympus", capacity = 1)),
        )
        assertEquals(setOf("zoneId", "capacity"), result.errors.map { it.field }.toSet())
    }

    @Test fun `default snapshot copies values and later defaults cannot rewrite game`() {
        val defaults = defaults()
        val copied = valid(GameDefaultSnapshotFactory.copy(defaults, createInput()))
        val changed = defaults.copy(
            venue = defaults.venue?.copy(name = "Outra arena"),
            capacity = 50,
            gameFeeCents = 9000,
        )

        assertEquals("Arena Central", copied.venue.name)
        assertEquals(24, copied.capacity)
        assertEquals(2500, copied.gameFeeCents)
        assertEquals(50, changed.capacity)
    }

    @Test fun `create overrides replace copied defaults independently`() {
        val snapshot = valid(
            GameDefaultSnapshotFactory.copy(
                defaults(),
                createInput(
                    title = "Jogo especial",
                    venue = validVenue().copy(name = "Arena Nova"),
                    durationMinutes = 120,
                    capacity = 30,
                    confirmationDeadline = START.minusSeconds(300),
                    gameFee = NullableGameFeeOverride.Value(3000),
                ),
            ),
        )

        assertEquals("Jogo especial", snapshot.title)
        assertEquals("Arena Nova", snapshot.venue.name)
        assertEquals(120, snapshot.durationMinutes)
        assertEquals(30, snapshot.capacity)
        assertEquals(3000, snapshot.gameFeeCents)
    }

    @Test fun `explicit null fee clears copied default`() {
        val snapshot = valid(
            GameDefaultSnapshotFactory.copy(
                defaults(),
                createInput(gameFee = NullableGameFeeOverride.Value(null)),
            ),
        )
        assertNull(snapshot.gameFeeCents)
    }

    @Test fun `default confirmation lead derives immutable deadline`() {
        val snapshot = valid(GameDefaultSnapshotFactory.copy(defaults(), createInput()))
        assertEquals(START.minusSeconds(180 * 60), snapshot.confirmationDeadline)
    }

    @Test fun `lifecycle permits only explicit status transitions`() {
        assertTrue(GameLifecyclePolicy.canMutate(GameStatus.DRAFT, GameMutation.EDIT))
        assertTrue(GameLifecyclePolicy.canMutate(GameStatus.DRAFT, GameMutation.PUBLISH))
        assertTrue(GameLifecyclePolicy.canMutate(GameStatus.PUBLISHED, GameMutation.EDIT))
        assertTrue(GameLifecyclePolicy.canMutate(GameStatus.PUBLISHED, GameMutation.CANCEL))
        assertTrue(GameLifecyclePolicy.canMutate(GameStatus.PUBLISHED, GameMutation.COMPLETE))
        GameMutation.entries.forEach { assertFalse(GameLifecyclePolicy.canMutate(GameStatus.CANCELLED, it)) }
        GameMutation.entries.forEach { assertFalse(GameLifecyclePolicy.canMutate(GameStatus.COMPLETED, it)) }
        assertFalse(GameLifecyclePolicy.canMutate(GameStatus.DRAFT, GameMutation.COMPLETE))
        assertFalse(GameLifecyclePolicy.canMutate(GameStatus.DRAFT, GameMutation.CANCEL))
        assertFalse(GameLifecyclePolicy.canMutate(GameStatus.PUBLISHED, GameMutation.PUBLISH))
    }

    @Test fun `draft is organizer only while historical statuses are member visible`() {
        assertTrue(GameLifecyclePolicy.visibleTo(GameStatus.DRAFT, GroupRole.OWNER))
        assertTrue(GameLifecyclePolicy.visibleTo(GameStatus.DRAFT, GroupRole.ADMIN))
        assertFalse(GameLifecyclePolicy.visibleTo(GameStatus.DRAFT, GroupRole.ATHLETE))
        assertTrue(GameLifecyclePolicy.visibleTo(GameStatus.PUBLISHED, GroupRole.ATHLETE))
        assertTrue(GameLifecyclePolicy.visibleTo(GameStatus.CANCELLED, GroupRole.ATHLETE))
        assertTrue(GameLifecyclePolicy.visibleTo(GameStatus.COMPLETED, GroupRole.ATHLETE))
        assertFalse(GameLifecyclePolicy.visibleTo(GameStatus.PUBLISHED, null))
    }

    private fun valid(input: GameDraftInput): GameSnapshot =
        assertIs<GameDraftValidation.Valid>(GameDraftValidator.validate(input)).snapshot

    private fun assertInvalid(input: GameDraftInput, field: String) {
        assertTrue(assertIs<GameDraftValidation.Invalid>(GameDraftValidator.validate(input)).errors.any { it.field == field })
    }

    private fun emptyInput() = GameDraftInput(null, null, null, null, null, null, null, null, null)

    private fun validInput(
        title: String? = "Treino de terça",
        venue: GameVenueInput? = validVenue(),
        startsAt: Instant? = START,
        durationMinutes: Int? = 90,
        capacity: Int? = 24,
        confirmationDeadline: Instant? = START.minusSeconds(3600),
        gameFeeCents: Long? = 2500,
        notes: String? = "Levar bola",
        zoneId: String? = "America/Sao_Paulo",
    ) = GameDraftInput(
        title,
        venue,
        LocalDate.of(2026, 8, 12),
        LocalTime.of(19, 30),
        zoneId,
        startsAt,
        durationMinutes,
        capacity,
        confirmationDeadline,
        gameFeeCents,
        notes,
    )

    private fun validVenue() = GameVenueInput(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2")

    private fun defaults() = GroupGameDefaults(
        title = "Treino padrão",
        venue = GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2"),
        durationMinutes = 90,
        capacity = 24,
        confirmationLeadMinutes = 180,
        gameFeeCents = 2500,
    )

    private fun createInput(
        title: String? = null,
        venue: GameVenueInput? = null,
        durationMinutes: Int? = null,
        capacity: Int? = null,
        confirmationDeadline: Instant? = null,
        gameFee: NullableGameFeeOverride = NullableGameFeeOverride.UseDefault,
    ) = CreateGameInput(
        title,
        venue,
        LocalDate.of(2026, 8, 12),
        LocalTime.of(19, 30),
        "America/Sao_Paulo",
        START,
        durationMinutes,
        capacity,
        confirmationDeadline,
        gameFee,
    )

    private companion object {
        val START: Instant = Instant.parse("2026-08-12T22:30:00Z")
    }
}
