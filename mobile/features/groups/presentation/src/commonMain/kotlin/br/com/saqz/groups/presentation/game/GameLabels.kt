package br.com.saqz.groups.presentation.game

import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_hero_deadline
import br.com.saqz.groups.resources.home_date
import br.com.saqz.groups.resources.home_deadline_date
import br.com.saqz.groups.resources.home_deadline_today
import br.com.saqz.groups.resources.home_deadline_tomorrow
import br.com.saqz.groups.resources.home_game_display
import br.com.saqz.groups.resources.home_game_meta
import br.com.saqz.groups.resources.home_month_april
import br.com.saqz.groups.resources.home_month_april_long
import br.com.saqz.groups.resources.home_month_august
import br.com.saqz.groups.resources.home_month_august_long
import br.com.saqz.groups.resources.home_month_december
import br.com.saqz.groups.resources.home_month_december_long
import br.com.saqz.groups.resources.home_month_february
import br.com.saqz.groups.resources.home_month_february_long
import br.com.saqz.groups.resources.home_month_january
import br.com.saqz.groups.resources.home_month_january_long
import br.com.saqz.groups.resources.home_month_july
import br.com.saqz.groups.resources.home_month_july_long
import br.com.saqz.groups.resources.home_month_june
import br.com.saqz.groups.resources.home_month_june_long
import br.com.saqz.groups.resources.home_month_march
import br.com.saqz.groups.resources.home_month_march_long
import br.com.saqz.groups.resources.home_month_may
import br.com.saqz.groups.resources.home_month_may_long
import br.com.saqz.groups.resources.home_month_november
import br.com.saqz.groups.resources.home_month_november_long
import br.com.saqz.groups.resources.home_month_october
import br.com.saqz.groups.resources.home_month_october_long
import br.com.saqz.groups.resources.home_month_september
import br.com.saqz.groups.resources.home_month_september_long
import br.com.saqz.groups.resources.home_time
import br.com.saqz.groups.resources.home_waitlist_reserva_bell
import br.com.saqz.groups.resources.home_weekday_friday
import br.com.saqz.groups.resources.home_weekday_monday
import br.com.saqz.groups.resources.home_weekday_saturday
import br.com.saqz.groups.resources.home_weekday_sunday
import br.com.saqz.groups.resources.home_weekday_thursday
import br.com.saqz.groups.resources.home_weekday_tuesday
import br.com.saqz.groups.resources.home_weekday_wednesday
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Os rótulos de data e prazo de um jogo, no vocabulário da Início (VUL-218): "Terça, 19h30",
 * "4 de agosto · CERET", "As confirmações encerram hoje às 12h00.". Nasceram privados no
 * `HomeViewModel`; o detalhe do grupo é o segundo uso, então moram aqui para as duas telas
 * dizerem a mesma coisa com as mesmas chaves `home_*`.
 *
 * Tudo recebe data/hora JÁ convertida para o fuso do jogo: quem chama resolve o fuso com
 * [gameTimeZone] e converte o instante uma vez.
 */
internal fun gameTimeZone(zoneId: String): TimeZone =
    runCatching { TimeZone.of(zoneId) }.getOrDefault(TimeZone.UTC)

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/** "19h30" */
internal suspend fun LocalDateTime.gameTimeLabel(): String =
    getString(Res.string.home_time, hour.twoDigits(), minute.twoDigits())

/** "04/08" */
internal suspend fun LocalDate.gameDateLabel(): String =
    getString(Res.string.home_date, day.twoDigits(), (month.ordinal + 1).twoDigits())

/** "Terça" — a chave é minúscula ("terça"); quem capitaliza é este rótulo. */
internal suspend fun DayOfWeek.gameWeekdayLabel(): String =
    getString(longWeekdayResource()).replaceFirstChar { it.titlecase() }

/** "AGO" — [month] de 1 a 12. Fora disso cai em dezembro, como as tabelas da Início. */
internal suspend fun gameShortMonthLabel(month: Int): String = getString(month.shortMonthResource())

/** "agosto" — minúsculo, como a chave; [month] de 1 a 12. */
private suspend fun gameLongMonthLabel(month: Int): String = getString(month.longMonthResource())

/** "Terça, 19h30" */
internal suspend fun LocalDateTime.gameHeroDisplay(): String =
    getString(Res.string.home_game_display, date.dayOfWeek.gameWeekdayLabel(), gameTimeLabel())

/** "4 de agosto · CERET — Quadra 2" */
internal suspend fun LocalDateTime.gameHeroMeta(place: String): String =
    getString(Res.string.home_game_meta, day.toString(), gameLongMonthLabel(month.ordinal + 1), place)

/** A frase do prazo ABERTO, relativa a [today] (hoje no fuso do jogo). */
internal suspend fun LocalDateTime.gameDeadlineSentence(today: LocalDate): String = when (date) {
    today -> getString(Res.string.home_deadline_today, gameTimeLabel())
    today.plus(DatePeriod(days = 1)) -> getString(Res.string.home_deadline_tomorrow, gameTimeLabel())
    else -> getString(Res.string.home_deadline_date, date.gameDateLabel(), gameTimeLabel())
}

/** "Encerra 04/08 · 12h00" */
internal suspend fun LocalDateTime.gameDeadlineShort(): String =
    getString(Res.string.home_admin_hero_deadline, date.gameDateLabel(), gameTimeLabel())

/** "Avisamos você se abrir vaga até 12h00 de 04/08." */
internal suspend fun LocalDateTime.gameBellLabel(): String =
    getString(Res.string.home_waitlist_reserva_bell, gameTimeLabel(), date.gameDateLabel())

private fun DayOfWeek.longWeekdayResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.home_weekday_monday
    DayOfWeek.TUESDAY -> Res.string.home_weekday_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.home_weekday_wednesday
    DayOfWeek.THURSDAY -> Res.string.home_weekday_thursday
    DayOfWeek.FRIDAY -> Res.string.home_weekday_friday
    DayOfWeek.SATURDAY -> Res.string.home_weekday_saturday
    DayOfWeek.SUNDAY -> Res.string.home_weekday_sunday
}

private fun Int.shortMonthResource(): StringResource = when (this) {
    1 -> Res.string.home_month_january
    2 -> Res.string.home_month_february
    3 -> Res.string.home_month_march
    4 -> Res.string.home_month_april
    5 -> Res.string.home_month_may
    6 -> Res.string.home_month_june
    7 -> Res.string.home_month_july
    8 -> Res.string.home_month_august
    9 -> Res.string.home_month_september
    10 -> Res.string.home_month_october
    11 -> Res.string.home_month_november
    else -> Res.string.home_month_december
}

private fun Int.longMonthResource(): StringResource = when (this) {
    1 -> Res.string.home_month_january_long
    2 -> Res.string.home_month_february_long
    3 -> Res.string.home_month_march_long
    4 -> Res.string.home_month_april_long
    5 -> Res.string.home_month_may_long
    6 -> Res.string.home_month_june_long
    7 -> Res.string.home_month_july_long
    8 -> Res.string.home_month_august_long
    9 -> Res.string.home_month_september_long
    10 -> Res.string.home_month_october_long
    11 -> Res.string.home_month_november_long
    else -> Res.string.home_month_december_long
}
