package br.com.saqz.groups.adapter.input.scheduling

import br.com.saqz.groups.application.game.series.ExtendGameSeries
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.scheduling.annotation.Scheduled

/** De hora em hora: renovação confirmada vira jogos novos sem esperar o dia seguinte. */
class ExtendGameSeriesJob(private val extend: ExtendGameSeries) {
    @Scheduled(fixedDelayString = "\${saqz.games.series-extension-delay-ms:3600000}", initialDelayString = "\${saqz.games.series-extension-initial-delay-ms:60000}")
    fun run() {
        JobLog.frequent("extend_game_series", { extend.run() }) { mapOf("series" to it.series, "groups" to it.groups, "failed" to it.failed) }
    }
}
