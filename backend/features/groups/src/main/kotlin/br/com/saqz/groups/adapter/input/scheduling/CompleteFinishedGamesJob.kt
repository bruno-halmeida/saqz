package br.com.saqz.groups.adapter.input.scheduling

import br.com.saqz.groups.application.game.CompleteFinishedGames
import org.springframework.scheduling.annotation.Scheduled

/** A cada 5 minutos: jogo que terminou vira encerrado sem ninguém precisar tocar em nada. */
class CompleteFinishedGamesJob(private val complete: CompleteFinishedGames) {
    @Scheduled(fixedDelayString = "\${saqz.games.completion-delay-ms:300000}", initialDelayString = "\${saqz.games.completion-initial-delay-ms:30000}")
    fun run() { complete.run() }
}
