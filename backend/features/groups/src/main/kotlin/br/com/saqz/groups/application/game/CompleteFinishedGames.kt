package br.com.saqz.groups.application.game

import java.time.Clock
import br.com.saqz.sharedkernel.job.JobLog
import java.time.Instant
import java.util.UUID

data class FinishedGame(val groupId: UUID, val gameId: UUID)

/** Encerra todo jogo publicado que já terminou; devolve quais mudaram. */
fun interface FinishedGamesRepository {
    fun completeFinished(now: Instant): List<FinishedGame>
}

/**
 * Jogo publicado vira encerrado quando passa do início + duração. É o encerramento que trava a
 * presença, abre o acerto e conta nas estatísticas; antes só o endpoint de encerrar mudava o
 * status, e nenhuma tela o chamava. Tudo por estado: rodar de novo ou em duas instâncias não
 * muda nada.
 */
class CompleteFinishedGames(
    private val games: FinishedGamesRepository,
    private val clock: Clock,
) {
    fun run(): Int {
        val finished = games.completeFinished(clock.instant())
        finished.forEach { JobLog.item("complete_finished_games", "game_completed", "groupId" to it.groupId, "gameId" to it.gameId) }
        return finished.size
    }
}
