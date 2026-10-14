package br.com.saqz.groups.presentation.list

import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.presentation.details.overCapacityLabel
import br.com.saqz.groups.resources.groups_next_game_in_progress
import br.com.saqz.groups.resources.groups_next_game_in_progress_over_capacity
import br.com.saqz.groups.resources.groups_next_game_over_capacity
import br.com.saqz.groups.resources.groups_next_game_summary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal suspend fun List<Game>.nextGroupCardGame(role: GroupRole, now: Instant): GroupCardGameUi? {
    val next = mapNotNull { game ->
        val visible = game.status == GameStatus.Published ||
            (game.status == GameStatus.Draft && role != GroupRole.ATHLETE)
        val start = runCatching { Instant.parse(game.startsAt) }.getOrNull()
        if (visible && start != null && game.isUpcomingOrRunning(start, now)) game to start else null
    }.minByOrNull { it.second } ?: return null
    val game = next.first
    val local = next.second.toLocalDateTime(TimeZone.of(game.zoneId))
    val date = "${local.day.twoDigits()}/${(local.month.ordinal + 1).twoDigits()}"
    val time = "${local.hour.twoDigits()}h${local.minute.twoDigits()}"
    val inProgress = next.second < now
    // Confirmados acima da lotação: "3 confirmados para 2 vagas", nunca "3 de 2".
    val label = if (game.confirmedCount > game.capacity) {
        getString(
            if (inProgress) Res.string.groups_next_game_in_progress_over_capacity else Res.string.groups_next_game_over_capacity,
            date, time, overCapacityLabel(game.confirmedCount, game.capacity),
        )
    } else {
        getString(
            if (inProgress) Res.string.groups_next_game_in_progress else Res.string.groups_next_game_summary,
            date, time, game.confirmedCount, game.capacity,
        )
    }
    return GroupCardGameUi(
        label = label,
        // The games endpoint has counts, but does not return the user's attendance.
        attendance = null,
    )
}
private fun Int.twoDigits() = toString().padStart(2, '0')

/** Jogo em andamento continua sendo o "próximo", como na Início: vale até o fim da duração. */
private fun Game.isUpcomingOrRunning(start: Instant, now: Instant): Boolean =
    start >= now || (status == GameStatus.Published && start + durationMinutes.minutes > now)
