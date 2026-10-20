package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzChoiceChipDefaults
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_picker_day
import br.com.saqz.groups.resources.game_picker_hour
import br.com.saqz.groups.resources.game_picker_hour_spoken
import br.com.saqz.groups.resources.game_picker_hour_spoken_one
import br.com.saqz.groups.resources.game_picker_minute
import br.com.saqz.groups.resources.group_weekday_short_friday
import br.com.saqz.groups.resources.group_weekday_short_monday
import br.com.saqz.groups.resources.group_weekday_short_saturday
import br.com.saqz.groups.resources.group_weekday_short_sunday
import br.com.saqz.groups.resources.group_weekday_short_thursday
import br.com.saqz.groups.resources.group_weekday_short_tuesday
import br.com.saqz.groups.resources.group_weekday_short_wednesday
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

/**
 * 4b — seletor de dia e hora por toque: grade de calendário (7 colunas), grade das 24 horas e os minutos em passo de 15.
 * Nada se escolhe deslizando; a folha só rola para caber. Vive no módulo da jornada porque não está no fluxo 10 do
 * export (AGENTS.md §5); os controles são todos do design system.
 */
internal data class GameDateTimePickerState(
    val days: List<PickerDay>,
    val hours: List<Int>,
    val minutes: List<Int>,
    val selectedDayIndex: Int,
    val selectedHour: Int,
    val selectedMinute: Int,
)

/** [label] é o texto falado e do resumo ("Segunda, 20 de outubro"). */
internal data class PickerDay(val isoDate: String, val label: String)

/** Um dia da grade; [index] é a posição em [GameDateTimePickerState.days]. */
internal data class PickerDayCell(val index: Int, val dayOfMonth: Int, val spokenLabel: String)

/** Semanas de segunda a domingo; `null` é a célula vazia que alinha o primeiro dia do mês na coluna certa. */
internal data class PickerMonth(val title: String, val weeks: List<List<PickerDayCell?>>)

internal object GameDateTimePickerTags {
    fun day(isoDate: String) = "game-picker-day-$isoDate"
    fun hour(hour: Int) = "game-picker-hour-$hour"
    fun minute(minute: Int) = "game-picker-minute-$minute"
}

internal object GameDateTimePicker {
    private const val MINUTE_STEP = 15
    private const val DAYS_PER_WEEK = 7

    fun state(
        days: List<PickerDay>,
        selectedDate: String,
        selectedHour: Int,
        selectedMinute: Int,
    ): GameDateTimePickerState {
        val dayIndex = days.indexOfFirst { it.isoDate == selectedDate }.let { if (it < 0) 0 else it }
        val preservedMinute = selectedMinute.coerceIn(0, 59)
        return GameDateTimePickerState(
            days = days,
            hours = (0..23).toList(),
            // Jogo antigo com minuto fora da grade (19h10) mantém o minuto como opção a mais.
            minutes = ((0..59 step MINUTE_STEP) + preservedMinute).distinct().sorted(),
            selectedDayIndex = dayIndex,
            selectedHour = selectedHour.coerceIn(0, 23),
            selectedMinute = preservedMinute,
        )
    }

    fun selectedDate(state: GameDateTimePickerState): String =
        state.days.getOrNull(state.selectedDayIndex)?.isoDate ?: ""

    /** Agrupa os dias por mês (o intervalo pode virar o mês) e alinha cada mês na semana de segunda a domingo. */
    fun months(days: List<PickerDay>, monthLabels: List<String>): List<PickerMonth> {
        val dated = days.mapIndexedNotNull { index, day ->
            runCatching { LocalDate.parse(day.isoDate) }.getOrNull()?.let { Triple(index, it, day.label) }
        }
        return dated.groupBy { (_, date, _) -> date.year to date.monthNumber }.map { (key, entries) ->
            val cells = List(entries.first().second.dayOfWeek.ordinal) { null } +
                entries.map { (index, date, label) -> PickerDayCell(index, date.day, label.lowercase()) }
            PickerMonth(
                title = monthLabels.getOrNull(key.second - 1).orEmpty(),
                weeks = cells.chunked(DAYS_PER_WEEK).map { week -> week + List(DAYS_PER_WEEK - week.size) { null } },
            )
        }
    }
}

@Composable
internal fun GameDateTimePicker(
    state: GameDateTimePickerState,
    monthLabels: List<String>,
    onDayChange: (Int) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
) {
    val metrics = SaqzTheme.metrics
    val months = remember(state.days, monthLabels) { GameDateTimePicker.months(state.days, monthLabels) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        if (showTime) PickerCaption(stringResource(Res.string.game_picker_day))
        WeekdayHeader()
        months.forEach { month ->
            // Só vale nomear o mês quando o intervalo atravessa mais de um.
            if (months.size > 1) PickerCaption(month.title)
            month.weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SaqzChoiceChipDefaults.CompactSpacing),
                ) {
                    week.forEach { cell ->
                        if (cell == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            SaqzChoiceChip(
                                label = cell.dayOfMonth.toString(),
                                selected = cell.index == state.selectedDayIndex,
                                onClick = { onDayChange(cell.index) },
                                compact = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(GameDateTimePickerTags.day(state.days[cell.index].isoDate))
                                    .semantics { contentDescription = cell.spokenLabel },
                            )
                        }
                    }
                }
            }
        }
        if (showTime) {
            PickerCaption(stringResource(Res.string.game_picker_hour))
            state.hours.chunked(HOURS_PER_ROW).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SaqzChoiceChipDefaults.CompactSpacing),
                ) {
                    row.forEach { hour ->
                        val spoken = if (hour == 1) {
                            stringResource(Res.string.game_picker_hour_spoken_one)
                        } else {
                            stringResource(Res.string.game_picker_hour_spoken, hour)
                        }
                        SaqzChoiceChip(
                            label = hour.toString().padStart(2, '0'),
                            selected = hour == state.selectedHour,
                            onClick = { onHourChange(hour) },
                            compact = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(GameDateTimePickerTags.hour(hour))
                                .semantics { contentDescription = spoken },
                        )
                    }
                }
            }
            PickerCaption(stringResource(Res.string.game_picker_minute))
            SaqzSegmented(
                options = state.minutes.map { it.toString().padStart(2, '0') },
                selected = state.minutes.indexOf(state.selectedMinute).coerceAtLeast(0),
                onSelect = { index -> state.minutes.getOrNull(index)?.let(onMinuteChange) },
                optionTags = state.minutes.map(GameDateTimePickerTags::minute),
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val names = listOf(
        stringResource(Res.string.group_weekday_short_monday),
        stringResource(Res.string.group_weekday_short_tuesday),
        stringResource(Res.string.group_weekday_short_wednesday),
        stringResource(Res.string.group_weekday_short_thursday),
        stringResource(Res.string.group_weekday_short_friday),
        stringResource(Res.string.group_weekday_short_saturday),
        stringResource(Res.string.group_weekday_short_sunday),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzChoiceChipDefaults.CompactSpacing),
    ) {
        names.forEach { name ->
            Text(
                text = name,
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PickerCaption(text: String) =
    Text(text, style = SaqzTheme.typography.eyebrow, color = SaqzTheme.colors.textSecondary)

internal fun buildPickerDays(
    start: LocalDate,
    count: Int,
    weekdayLabels: List<String>,
    monthLabels: List<String>,
): List<PickerDay> = (0 until count).map { offset ->
    val date = start.plus(DatePeriod(days = offset))
    PickerDay(
        isoDate = date.toString(),
        label = "${weekdayLabels[date.dayOfWeek.ordinal]}, ${date.day} de ${monthLabels[date.monthNumber - 1]}",
    )
}

private const val HOURS_PER_ROW = 6
