package br.com.saqz.groups.application.game

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameLifecyclePolicy
import java.util.UUID

data class GameAttendanceCounts(val confirmed: Int, val waitlisted: Int) {
    init { require(confirmed >= 0 && waitlisted >= 0) }
}

fun interface GameAttendanceCountSource {
    fun counts(gameIds: Set<UUID>): Map<UUID, GameAttendanceCounts>
}

interface GameQueryRepository {
    fun role(actor: UUID, groupId: UUID): GroupRole?
    fun list(groupId: UUID): List<Game>
    fun find(groupId: UUID, gameId: UUID): Game?
}

data class GameView(
    val game: Game,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
)

sealed interface GameListResult {
    data class Success(val games: List<GameView>) : GameListResult
    data object GroupNotFound : GameListResult
}

sealed interface GameReadResult {
    data class Success(val game: GameView) : GameReadResult
    data object GameNotFound : GameReadResult
}

class ListGames(
    private val repository: GameQueryRepository,
    private val attendance: GameAttendanceCountSource,
) {
    fun execute(actor: UUID, groupId: UUID): GameListResult {
        val role = repository.role(actor, groupId) ?: return GameListResult.GroupNotFound
        val games = repository.list(groupId).filter { GameLifecyclePolicy.visibleTo(it.status, role) }
        val counts = attendance.counts(games.map(Game::id).toSet())
        return GameListResult.Success(games.map { it.view(counts[it.id]) })
    }
}

class GetGame(
    private val repository: GameQueryRepository,
    private val attendance: GameAttendanceCountSource,
) {
    fun execute(actor: UUID, groupId: UUID, gameId: UUID): GameReadResult {
        val role = repository.role(actor, groupId) ?: return GameReadResult.GameNotFound
        val game = repository.find(groupId, gameId) ?: return GameReadResult.GameNotFound
        if (!GameLifecyclePolicy.visibleTo(game.status, role)) return GameReadResult.GameNotFound
        return GameReadResult.Success(game.view(attendance.counts(setOf(gameId))[gameId]))
    }
}

private fun Game.view(counts: GameAttendanceCounts?): GameView {
    val confirmed = counts?.confirmed ?: 0
    return GameView(
        this,
        confirmedCount = confirmed,
        availableSpots = (snapshot.capacity - confirmed).coerceAtLeast(0),
        waitlistCount = counts?.waitlisted ?: 0,
    )
}
