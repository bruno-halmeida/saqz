package br.com.saqz.groups.presentation.list

import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.presentation.details.overCapacityLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_weekday_short_friday
import br.com.saqz.groups.resources.group_weekday_short_monday
import br.com.saqz.groups.resources.group_weekday_short_saturday
import br.com.saqz.groups.resources.group_weekday_short_sunday
import br.com.saqz.groups.resources.group_weekday_short_thursday
import br.com.saqz.groups.resources.group_weekday_short_tuesday
import br.com.saqz.groups.resources.group_weekday_short_wednesday
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * O jogo da linha do grupo (GER-003): o **publicado** mais perto que ainda não começou. Só quando não há nenhum, o jogo
 * em andamento (até `startsAt + durationMinutes`) fica, com "AGORA". Rascunho nunca aparece, nem para dono e admin.
 */
internal fun List<Game>.shownGame(now: Instant): Pair<Game, Instant>? {
    val published = mapNotNull { game ->
        val start = runCatching { Instant.parse(game.startsAt) }.getOrNull()
        if (game.status == GameStatus.Published && start != null) game to start else null
    }
    return published.filter { it.second > now }.minByOrNull { it.second }
        ?: published.filter { (game, start) -> start <= now && start + game.durationMinutes.minutes > now }
            .maxByOrNull { it.second }
}

internal suspend fun List<Game>.nextGroupCardGame(now: Instant): GroupCardGameUi? {
    val (game, start) = shownGame(now) ?: return null
    val local = start.toLocalDateTime(TimeZone.of(game.zoneId))
    val live = start <= now
    return GroupCardGameUi(
        gameId = game.id,
        weekday = getString(local.date.dayOfWeek.shortResource()).uppercase(),
        day = local.day.twoDigits(),
        time = "${local.hour.twoDigits()}h${local.minute.twoDigits()}",
        live = live,
        confirmedCount = game.confirmedCount,
        capacity = game.capacity,
        // Confirmados acima da lotação: "13 confirmados para 12 vagas", nunca "13 de 12".
        overCapacityLabel = if (game.confirmedCount > game.capacity) {
            overCapacityLabel(game.confirmedCount, game.capacity)
        } else {
            null
        },
        attendance = game.cardAttendance(live),
        startsAtEpochSeconds = start.epochSeconds,
    )
}

/** "CERET — Quadra 2"; sem quadra, só o nome do local. */
internal fun Game.placeLabel(): String? {
    val name = venue.name.trim().takeIf(String::isNotEmpty) ?: return null
    val court = venue.court?.trim()?.takeIf(String::isNotEmpty) ?: return name
    return "$name — $court"
}

/**
 * A sua resposta. Sem resposta, o jogo por começar pede "Confirmar" (inclusive depois do prazo: o prazo encerra a
 * prioridade de mensalista, não a lista, PRES-005); em andamento, mostra "Não vai" só na tela — nada é gravado.
 */
private fun Game.cardAttendance(live: Boolean): GroupCardAttendance = when (ownAttendance) {
    AttendanceStatus.Confirmed -> GroupCardAttendance.Going
    AttendanceStatus.Waitlisted -> GroupCardAttendance.Waitlisted
    AttendanceStatus.Declined -> GroupCardAttendance.Out
    null -> if (live) GroupCardAttendance.Out else GroupCardAttendance.Pending
}

/** Ordem da lista: 1) pede Confirmar; 2) o jogo mais perto (em andamento já começou, então vem antes); 3) sem jogo. */
internal fun List<GroupCardUi>.sortedForList(): List<GroupCardUi> = sortedWith(
    compareBy<GroupCardUi>(
        { it.nextGame?.needsConfirmation != true },
        { it.nextGame == null },
        { it.nextGame?.startsAtEpochSeconds ?: Long.MAX_VALUE },
        { it.name.lowercase() },
    ),
)

private fun Int.twoDigits() = toString().padStart(2, '0')

private fun DayOfWeek.shortResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.group_weekday_short_monday
    DayOfWeek.TUESDAY -> Res.string.group_weekday_short_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.group_weekday_short_wednesday
    DayOfWeek.THURSDAY -> Res.string.group_weekday_short_thursday
    DayOfWeek.FRIDAY -> Res.string.group_weekday_short_friday
    DayOfWeek.SATURDAY -> Res.string.group_weekday_short_saturday
    DayOfWeek.SUNDAY -> Res.string.group_weekday_short_sunday
}
