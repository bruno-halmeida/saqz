package br.com.saqz.groups.presentation.games

import br.com.saqz.core.common.formatting.SaqzDateTimeFormatter
import br.com.saqz.groups.domain.game.Game
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Início e fim do jogo no timezone do dispositivo, derivados do instante real (`startsAt`)
 * e não do wall-clock do grupo. Ex.: "12/08/2026 às 19:30 – 21:00"; a data do fim aparece
 * quando o jogo cruza a meia-noite local do dispositivo.
 */
fun Game.scheduleText(formatter: SaqzDateTimeFormatter): String {
    val start = Instant.parse(startsAt)
    val end = start + durationMinutes.minutes
    val startDate = formatter.formatDate(start)
    val endDate = formatter.formatDate(end)
    val endText = if (endDate == startDate) formatter.formatTime(end) else "$endDate às ${formatter.formatTime(end)}"
    return "$startDate às ${formatter.formatTime(start)} – $endText"
}

/** Data local do dispositivo em ISO, usada para separar jogos futuros de passados. */
fun Game.deviceLocalDate(formatter: SaqzDateTimeFormatter): String =
    formatter.formatDate(Instant.parse(startsAt)).split('/').let { (day, month, year) -> "$year-$month-$day" }
