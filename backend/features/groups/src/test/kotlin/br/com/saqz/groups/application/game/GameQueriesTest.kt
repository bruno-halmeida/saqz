package br.com.saqz.groups.application.game

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameQueriesTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

    @Test fun `organizer list includes drafts and history`() {
        val repository = FakeQueries(GroupRole.OWNER, listOf(game(GameStatus.DRAFT), game(GameStatus.PUBLISHED)))
        val result = assertIs<GameListResult.Success>(ListGames(repository, FakeCounts()).execute(actor, groupId))
        assertEquals(listOf(GameStatus.DRAFT, GameStatus.PUBLISHED), result.games.map { it.game.status })
    }

    @Test fun `athlete list hides drafts and retains published cancelled completed history`() {
        val games = GameStatus.entries.map(::game)
        val result = assertIs<GameListResult.Success>(
            ListGames(FakeQueries(GroupRole.ATHLETE, games), FakeCounts()).execute(actor, groupId),
        )
        assertEquals(listOf(GameStatus.PUBLISHED, GameStatus.CANCELLED, GameStatus.COMPLETED), result.games.map { it.game.status })
    }

    @Test fun `nonmember list is privacy preserving and never reads games or counts`() {
        val repository = FakeQueries(null, listOf(game(GameStatus.PUBLISHED)))
        val counts = FakeCounts()
        assertSame(GameListResult.GroupNotFound, ListGames(repository, counts).execute(actor, groupId))
        assertEquals(0, repository.listCalls)
        assertEquals(0, counts.calls)
    }

    @Test fun `unknown and nonmember game reads share not found`() {
        val unknown = GetGame(FakeQueries(GroupRole.OWNER, emptyList()), FakeCounts()).execute(actor, groupId, UUID.randomUUID())
        val nonmember = GetGame(FakeQueries(null, listOf(game(GameStatus.PUBLISHED))), FakeCounts()).execute(actor, groupId, UUID.randomUUID())
        assertSame(GameReadResult.GameNotFound, unknown)
        assertSame(unknown, nonmember)
    }

    @Test fun `athlete draft read is hidden and does not request counts`() {
        val draft = game(GameStatus.DRAFT)
        val counts = FakeCounts()
        assertSame(
            GameReadResult.GameNotFound,
            GetGame(FakeQueries(GroupRole.ATHLETE, listOf(draft)), counts).execute(actor, groupId, draft.id),
        )
        assertEquals(0, counts.calls)
    }

    @Test fun `reads derive confirmed available and waitlist counts from server source`() {
        val game = game(GameStatus.PUBLISHED, capacity = 24)
        val result = assertIs<GameReadResult.Success>(
            GetGame(FakeQueries(GroupRole.ATHLETE, listOf(game)), FakeCounts(mapOf(game.id to GameAttendanceCounts(20, 3))))
                .execute(actor, groupId, game.id),
        ).game
        assertEquals(20, result.confirmedCount)
        assertEquals(4, result.availableSpots)
        assertEquals(3, result.waitlistCount)
    }

    @Test fun `available spots never becomes negative after capacity reduction`() {
        val game = game(GameStatus.PUBLISHED, capacity = 10)
        val result = assertIs<GameReadResult.Success>(
            GetGame(FakeQueries(GroupRole.OWNER, listOf(game)), FakeCounts(mapOf(game.id to GameAttendanceCounts(14, 2))))
                .execute(actor, groupId, game.id),
        ).game
        assertEquals(14, result.confirmedCount)
        assertEquals(0, result.availableSpots)
    }

    @Test fun `missing count projection deterministically derives zero counts`() {
        val game = game(GameStatus.PUBLISHED)
        val result = assertIs<GameReadResult.Success>(
            GetGame(FakeQueries(GroupRole.OWNER, listOf(game)), FakeCounts()).execute(actor, groupId, game.id),
        ).game
        assertEquals(0, result.confirmedCount)
        assertEquals(game.snapshot.capacity, result.availableSpots)
        assertEquals(0, result.waitlistCount)
    }

    private class FakeQueries(private val access: GroupRole?, private val games: List<Game>) : GameQueryRepository {
        var listCalls = 0
        override fun role(actor: UUID, groupId: UUID) = access
        override fun list(groupId: UUID): List<Game> = games.also { listCalls++ }
        override fun find(groupId: UUID, gameId: UUID): Game? = games.firstOrNull { it.id == gameId }
    }

    private class FakeCounts(private val values: Map<UUID, GameAttendanceCounts> = emptyMap()) : GameAttendanceCountSource {
        var calls = 0
        override fun counts(gameIds: Set<UUID>) = values.filterKeys(gameIds::contains).also { calls++ }
    }

    private fun game(status: GameStatus, capacity: Int = 24) = Game(
        UUID.randomUUID(),
        groupId,
        GameSnapshot(
            "Treino",
            GameVenueSnapshot(UUID.randomUUID(), "Arena", "Rua Central 100", null),
            LocalDate.of(2026, 8, 12),
            LocalTime.of(19, 30),
            IanaTimeZone.from("America/Sao_Paulo"),
            Instant.parse("2026-08-12T22:30:00Z"),
            90,
            capacity,
            Instant.parse("2026-08-12T20:30:00Z"),
            2500,
            null,
        ),
        status,
    )
}
