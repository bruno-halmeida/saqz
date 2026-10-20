package br.com.saqz.groups.presentation.game

import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_hero_priority
import br.com.saqz.groups.resources.home_priority_date
import br.com.saqz.groups.resources.home_priority_today
import br.com.saqz.groups.resources.home_priority_tomorrow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.getString

/**
 * "Prioridade dos mensalistas até hoje às 12h00." — o prazo não fecha a lista, só encerra a
 * prioridade do mensalista (o "Vou" depois dele entra pela espera).
 */
internal suspend fun LocalDateTime.gamePrioritySentence(today: LocalDate): String = when (date) {
    today -> getString(Res.string.home_priority_today, gameTimeLabel())
    today.plus(DatePeriod(days = 1)) -> getString(Res.string.home_priority_tomorrow, gameTimeLabel())
    else -> getString(Res.string.home_priority_date, date.gameDateLabel(), gameTimeLabel())
}

/** "Prioridade até 04/08 · 12h00" */
internal suspend fun LocalDateTime.gamePriorityShort(): String =
    getString(Res.string.home_admin_hero_priority, date.gameDateLabel(), gameTimeLabel())
