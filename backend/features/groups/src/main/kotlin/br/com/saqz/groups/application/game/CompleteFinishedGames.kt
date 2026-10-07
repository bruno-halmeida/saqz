package br.com.saqz.groups.application.game

import java.time.Clock
import java.time.Instant

/** Encerra todo jogo publicado que já terminou; devolve quantos mudaram. */
fun interface FinishedGamesRepository {
    fun completeFinished(now: Instant): Int
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
    fun run(): Int = games.completeFinished(clock.instant())
}
