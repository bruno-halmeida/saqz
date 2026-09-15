package br.com.saqz.groups.presentation.list

import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_next_game_summary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

internal suspend fun List<Game>.nextGroupCardGame(role: GroupRole, now: Instant): GroupCardGameUi? {
    val next = mapNotNull { game ->
        val visible = game.status == GameStatus.Published ||
            (game.status == GameStatus.Draft && role != GroupRole.ATHLETE)
        val start = runCatching { Instant.parse(game.startsAt) }.getOrNull()
        if (visible && start != null && start >= now) game to start else null
    }.minByOrNull { it.second } ?: return null
    val game = next.first
    val local = next.second.toLocalDateTime(TimeZone.of(game.zoneId))
    val date = "${local.day.twoDigits()}/${(local.month.ordinal + 1).twoDigits()}"
    val time = "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
    return GroupCardGameUi(
        label = getString(Res.string.groups_next_game_summary, date, time, game.confirmedCount, game.capacity),
        // The games endpoint has counts, but does not return the user's attendance.
        attendance = null,
    )
}
private fun Int.twoDigits() = toString().padStart(2, '0')
