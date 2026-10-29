package br.com.saqz.groups.presentation.ui.finance

import br.com.saqz.core.common.formatting.formatBrl

/** "R$ 880" sem centavos quando o valor é redondo (placar, resumo, etiquetas); senão "R$ 880,50". */
internal fun formatBrlShort(cents: Long): String =
    if (cents % 100 == 0L) formatBrl(cents).removeSuffix(",00") else formatBrl(cents)

/** Saída com o sinal do desenho: "−R$ 160" (menos tipográfico). */
internal fun formatBrlOut(cents: Long): String = "−" + formatBrlShort(if (cents < 0) -cents else cents)

/** Entrada com o sinal: "+R$ 20". */
internal fun formatBrlIn(cents: Long): String = "+" + formatBrlShort(if (cents < 0) -cents else cents)

internal val MONTH_NAMES = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

internal val WEEKDAY_NAMES = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")

/** "2026-10-27" → "Terça, 27/10"; data inválida volta como veio. */
internal fun weekdayDayMonth(isoDate: String): String = runCatching {
    val date = kotlinx.datetime.LocalDate.parse(isoDate.take(10))
    "${WEEKDAY_NAMES[date.dayOfWeek.ordinal]}, ${dayMonth(date)}"
}.getOrDefault(isoDate)

/** "2026-10-27" → "27/10". */
internal fun dayMonth(isoDate: String): String = runCatching {
    dayMonth(kotlinx.datetime.LocalDate.parse(isoDate.take(10)))
}.getOrDefault(isoDate)

private fun dayMonth(date: kotlinx.datetime.LocalDate) =
    "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}"

/** "2026-10" → "outubro". */
internal fun monthNameOf(monthKey: String): String {
    val month = monthKey.substringAfter('-', "01").take(2).toIntOrNull()?.coerceIn(1, 12) ?: 1
    return MONTH_NAMES[month - 1]
}
