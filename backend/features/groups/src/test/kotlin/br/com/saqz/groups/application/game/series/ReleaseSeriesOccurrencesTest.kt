package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.GameCommandContext
import br.com.saqz.groups.application.game.GameCommandRepository
import br.com.saqz.groups.application.game.GameCreationContext
import br.com.saqz.groups.application.game.GameSideEffect
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.application.game.GameWriteResult
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReleaseSeriesOccurrencesTest {
    private val now = Instant.parse("2026-09-17T15:00:00Z")
    private val lead = Duration.ofDays(5)
    private val owner = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val games = FakeGames(owner)
    private val repository = FakeReleasable(owner)
    private val effects = mutableListOf<Triple<UUID, UUID, Set<GameSideEffect>>>()
    private val failures = mutableListOf<String>()
    private var horizonUntil: Instant? = Instant.MAX

    @Test fun `publishes every occurrence the repository offers inside the lead window as the group owner`() {
        val thursday = games.draft(groupId)
        val saturday = games.draft(groupId)
        repository.offer(thursday, saturday)

        release().releaseAll()

        assertEquals(listOf(Triple<Instant, Instant, UUID?>(now, now.plus(lead), null)), repository.calls)
        assertEquals(GameStatus.PUBLISHED, games.status(thursday))
        assertEquals(GameStatus.PUBLISHED, games.status(saturday))
        // Mesmo caminho do "Publicar" manual: presença aberta, aviso e confirmações em nome do dono.
        assertEquals(
            listOf(
                Triple(thursday.id, owner, setOf(GameSideEffect.SCHEDULE_CHANGED, GameSideEffect.ATTENDANCE_OPENED)),
                Triple(saturday.id, owner, setOf(GameSideEffect.SCHEDULE_CHANGED, GameSideEffect.ATTENDANCE_OPENED)),
            ),
            effects,
        )
        assertEquals(emptyList(), failures)
    }

    @Test fun `release after a group sync is limited to that group`() {
        release().release(groupId)
        assertEquals(listOf(Triple(now, now.plus(lead), groupId)), repository.calls)
    }

    @Test fun `groups without a creation horizon are left alone`() {
        val occurrence = games.draft(groupId)
        repository.offer(occurrence)
        horizonUntil = null

        release().releaseAll()

        assertEquals(GameStatus.DRAFT, games.status(occurrence))
        assertEquals(emptyList(), effects)
    }

    @Test fun `a failing occurrence is reported and does not stop the others`() {
        val broken = games.draft(groupId)
        val healthy = games.draft(groupId)
        games.failing = broken.id
        repository.offer(broken, healthy)

        release().releaseAll()

        assertEquals(listOf("game ${broken.id}"), failures)
        assertEquals(GameStatus.DRAFT, games.status(broken))
        assertEquals(GameStatus.PUBLISHED, games.status(healthy))
    }

    @Test fun `an occurrence changed since the read is skipped silently and converges next run`() {
        val changed = games.draft(groupId)
        repository.offer(changed, version = changed.version + 1)

        release().releaseAll()

        assertEquals(GameStatus.DRAFT, games.status(changed))
        assertEquals(emptyList(), failures)
        assertEquals(emptyList(), effects)
    }

    @Test fun `negative lead is rejected`() {
        assertFailsWith<IllegalArgumentException> { release(lead = Duration.ofDays(-1)) }
    }

    private fun release(lead: Duration = this.lead) = ReleaseSeriesOccurrences(
        repository,
        ChangeGameLifecycle(
            object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
            games,
            GameSideEffectPort { game, actorId, applied -> effects += Triple(game.id, actorId, applied) },
        ),
        GameCreationHorizon { horizonUntil },
        lead,
        Clock.fixed(now, ZoneOffset.UTC),
    ) { what, _ -> failures += what }

    private class FakeReleasable(private val owner: UUID) : ReleasableOccurrenceRepository {
        val calls = mutableListOf<Triple<Instant, Instant, UUID?>>()
        private val offered = mutableListOf<ReleasableOccurrence>()
        fun offer(vararg games: Game, version: Long? = null) {
            games.forEach { offered += ReleasableOccurrence(it.groupId, it.id, version ?: it.version, owner) }
        }
        override fun nextOccurrences(after: Instant, until: Instant, groupId: UUID?): List<ReleasableOccurrence> {
            calls += Triple(after, until, groupId)
            return offered.toList()
        }
    }

    private class FakeGames(private val owner: UUID) : GameCommandRepository {
        private val stored = mutableMapOf<UUID, Game>()
        var failing: UUID? = null
        fun draft(groupId: UUID): Game = Game(
            UUID.randomUUID(),
            groupId,
            GameSnapshot(
                "Vôlei de quinta",
                GameVenueSnapshot(UUID.randomUUID(), "Arena", "Rua Central 100", null),
                LocalDate.of(2026, 9, 17),
                LocalTime.of(20, 0),
                IanaTimeZone.from("America/Sao_Paulo"),
                Instant.parse("2026-09-17T23:00:00Z"),
                120,
                12,
                Instant.parse("2026-09-17T17:00:00Z"),
                null,
                null,
            ),
            GameStatus.DRAFT,
            seriesId = UUID.randomUUID(),
        ).also { stored[it.id] = it }
        fun status(game: Game) = stored.getValue(game.id).status
        override fun creationContext(actor: UUID, groupId: UUID): GameCreationContext? = error("unused")
        override fun recurringConflict(groupId: UUID, startsAt: Instant, excludingGameId: UUID?): UUID? = error("unused")
        override fun find(actor: UUID, groupId: UUID, gameId: UUID): GameCommandContext? {
            check(gameId != failing) { "database down" }
            val game = stored[gameId]?.takeIf { it.groupId == groupId } ?: return null
            return GameCommandContext(if (actor == owner) GroupRole.OWNER else GroupRole.ATHLETE, game)
        }
        override fun create(game: Game): GameWriteResult = error("unused")
        override fun update(game: Game, expectedVersion: Long): GameWriteResult {
            val current = stored[game.id] ?: return GameWriteResult.NotFound
            if (current.version != expectedVersion) return GameWriteResult.VersionConflict
            return GameWriteResult.Saved(game.copy(version = expectedVersion + 1).also { stored[it.id] = it })
        }
    }
}
