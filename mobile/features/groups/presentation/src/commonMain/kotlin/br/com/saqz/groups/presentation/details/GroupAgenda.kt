package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.game.gameDateLabel
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.game.gameWeekdayLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_agenda_meta
import br.com.saqz.groups.resources.group_details_agenda_meta_draft
import br.com.saqz.groups.resources.group_details_agenda_meta_full
import br.com.saqz.groups.resources.group_details_agenda_meta_venue
import br.com.saqz.groups.resources.group_details_agenda_status_draft
import br.com.saqz.groups.resources.home_admin_score_pending
import br.com.saqz.groups.resources.home_upcoming_cd_row
import br.com.saqz.groups.resources.home_upcoming_row_title
import br.com.saqz.groups.resources.home_upcoming_status_going
import br.com.saqz.groups.resources.home_upcoming_status_out
import br.com.saqz.groups.resources.home_upcoming_status_waitlisted
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

// ponytail: teto de 12 linhas — a agenda é um `Column` não-lazy dentro da tela mais aberta do
// app e a listagem de jogos não pagina. Grupo que passar disso ganha uma tela de agenda
// própria (lista lazy + paginação no servidor); até lá o "ver mais" da tela abre só estas.
private const val GROUP_AGENDA_LIMIT = 12

/**
 * "Próximos jogos" do detalhe do grupo: publicados e rascunhos que ainda vão começar, SEM o
 * jogo que já está no hero, em ordem de início. Rascunho só chega aqui para gestor — quem
 * filtra por papel é o backend, não este mapper. Jogo com `startsAt` ilegível fica de fora,
 * como em `nextPublishedGame`.
 */
internal suspend fun groupAgenda(
    games: List<Game>,
    heroGameId: String?,
    now: Instant,
    defaultVenueName: String?,
): List<GroupAgendaRowUi> = games
    .filter { it.id != heroGameId && (it.status == GameStatus.Published || it.status == GameStatus.Draft) }
    .mapNotNull { game -> runCatching { Instant.parse(game.startsAt) }.getOrNull()?.let { it to game } }
    .filter { it.first >= now }
    .sortedBy { it.first }
    .take(GROUP_AGENDA_LIMIT)
    .map { (startsAt, game) -> game.toAgendaRow(startsAt, defaultVenueName) }

/**
 * "Terça e Quinta · 19h30": os dias distintos na ordem da semana e, só quando TODOS os
 * horários fixos começam na mesma hora, a hora. Os rótulos de dia entram por parâmetro porque
 * ainda são os hardcoded do `GroupDetailsViewModel.kt` (dívida conhecida).
 */
internal fun groupScheduleSummary(slots: List<GroupRegularSlot>, weekdayLabel: (GroupWeekday) -> String): String? {
    if (slots.isEmpty()) return null
    val days = slots.map { it.weekday }.distinct().sorted().map(weekdayLabel)
    val daysLabel = if (days.size == 1) days.single() else "${days.dropLast(1).joinToString(", ")} e ${days.last()}"
    // "19:30" → "19h30". O `take` corta segundos que um payload antigo possa trazer.
    val time = slots.map { it.startTime }.distinct().singleOrNull()?.take(HOUR_MINUTE_LENGTH)?.replace(':', 'h')
    return listOfNotNull(daysLabel, time).joinToString(" · ")
}

private const val HOUR_MINUTE_LENGTH = 5

private suspend fun Game.toAgendaRow(startsAt: Instant, defaultVenueName: String?): GroupAgendaRowUi {
    val local = startsAt.toLocalDateTime(gameTimeZone(zoneId))
    val weekday = local.date.dayOfWeek.gameWeekdayLabel()
    val time = local.gameTimeLabel()
    val status = agendaStatus()
    val statusLabel = getString(status.labelResource())
    return GroupAgendaRowUi(
        gameId = id,
        day = local.day.toString(),
        month = gameShortMonthLabel(local.date.month.ordinal + 1),
        title = getString(Res.string.home_upcoming_row_title, weekday, time),
        meta = agendaMeta(defaultVenueName),
        status = status,
        statusLabel = statusLabel,
        contentDescription = getString(Res.string.home_upcoming_cd_row, weekday, local.date.gameDateLabel(), time, statusLabel),
    )
}

// A ordem é a regra: rascunho não tem lotação para contar; lotado importa mais que o local.
// Sem quadra padrão no grupo (`null`), todo jogo mostra o próprio local.
private suspend fun Game.agendaMeta(defaultVenueName: String?): String = when {
    status == GameStatus.Draft -> getString(Res.string.group_details_agenda_meta_draft)
    availableSpots <= 0 -> getString(Res.string.group_details_agenda_meta_full, confirmedCount, capacity)
    venue.name != defaultVenueName ->
        getString(Res.string.group_details_agenda_meta_venue, confirmedCount, capacity, venue.name)
    else -> getString(Res.string.group_details_agenda_meta, confirmedCount, capacity)
}

private fun Game.agendaStatus(): GroupAgendaStatus = when {
    status == GameStatus.Draft -> GroupAgendaStatus.Draft
    ownAttendance == AttendanceStatus.Confirmed -> GroupAgendaStatus.Going
    ownAttendance == AttendanceStatus.Declined -> GroupAgendaStatus.Out
    ownAttendance == AttendanceStatus.Waitlisted -> GroupAgendaStatus.Waitlisted
    else -> GroupAgendaStatus.Pending
}

private fun GroupAgendaStatus.labelResource(): StringResource = when (this) {
    GroupAgendaStatus.Pending -> Res.string.home_admin_score_pending
    GroupAgendaStatus.Going -> Res.string.home_upcoming_status_going
    GroupAgendaStatus.Out -> Res.string.home_upcoming_status_out
    GroupAgendaStatus.Waitlisted -> Res.string.home_upcoming_status_waitlisted
    GroupAgendaStatus.Draft -> Res.string.group_details_agenda_status_draft
}
