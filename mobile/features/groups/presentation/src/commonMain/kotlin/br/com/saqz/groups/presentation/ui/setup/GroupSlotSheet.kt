package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import br.com.saqz.designsystem.SaqzMultiSegmented
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.setup.GroupSlotDraft
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.presentation.ui.shortLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_slot_hour
import br.com.saqz.groups.resources.group_slot_hour_after
import br.com.saqz.groups.resources.group_slot_hour_before
import br.com.saqz.groups.resources.group_slot_hour_value
import br.com.saqz.groups.resources.group_slot_minutes
import br.com.saqz.groups.resources.group_slot_summary
import br.com.saqz.groups.resources.group_slot_summary_and
import br.com.saqz.groups.resources.group_slot_summary_every_f
import br.com.saqz.groups.resources.group_slot_summary_every_m
import br.com.saqz.groups.resources.group_slot_time_with_minutes
import br.com.saqz.groups.resources.group_slot_sheet_weekday
import br.com.saqz.groups.resources.group_slot_sheet_weekday
import br.com.saqz.groups.resources.group_slot_weekday_hint
import org.jetbrains.compose.resources.stringResource

internal object GroupSlotSheetTags {
    const val Weekdays = "group-slot-weekdays"
    const val Hour = "group-slot-hour"
    const val Minutes = "group-slot-minutes"
    const val Summary = "group-slot-summary"

    fun weekday(day: GroupWeekday) = "group-slot-weekday-${day.name}"
    fun minute(minute: Int) = "group-slot-minute-$minute"
}

// Domingo a sábado, a ordem do segmentado da folha.
internal val SlotSheetWeekdays = listOf(
    GroupWeekday.SUNDAY,
    GroupWeekday.MONDAY,
    GroupWeekday.TUESDAY,
    GroupWeekday.WEDNESDAY,
    GroupWeekday.THURSDAY,
    GroupWeekday.FRIDAY,
    GroupWeekday.SATURDAY,
)

internal val SlotSheetMinutes = listOf(0, 15, 30, 45)

private const val MaxHour = 23

/**
 * Corpo da folha "Dias e horário". Ao adicionar ([multiple]) os dias são de seleção múltipla; ao editar um horário que
 * já existe, um dia só. O minuto de um horário salvo que não está no segmentado (20, por exemplo) fica sem marca e é
 * mantido até a pessoa escolher outro.
 */
@Composable
internal fun GroupSlotSheetContent(
    draft: GroupSlotDraft,
    multiple: Boolean,
    onToggleDay: (GroupWeekday) -> Unit,
    onPickDay: (GroupWeekday) -> Unit,
    onTimePick: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        Column {
            Text(
                text = stringResource(Res.string.group_slot_sheet_weekday),
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
            )
            if (multiple) {
                Text(
                    text = stringResource(Res.string.group_slot_weekday_hint),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
        val labels = SlotSheetWeekdays.map { it.shortLabel() }
        val tags = SlotSheetWeekdays.map(GroupSlotSheetTags::weekday)
        if (multiple) {
            SaqzMultiSegmented(
                options = labels,
                selected = SlotSheetWeekdays.indices.filter { SlotSheetWeekdays[it] in draft.weekdays }.toSet(),
                onToggle = { onToggleDay(SlotSheetWeekdays[it]) },
                optionTags = tags,
                modifier = Modifier.testTag(GroupSlotSheetTags.Weekdays),
            )
        } else {
            SaqzSegmented(
                options = labels,
                selected = SlotSheetWeekdays.indexOf(draft.weekdays.firstOrNull()),
                onSelect = { onPickDay(SlotSheetWeekdays[it]) },
                optionTags = tags,
                modifier = Modifier.testTag(GroupSlotSheetTags.Weekdays),
            )
        }
        SaqzStepper(
            value = draft.hour,
            onValueChange = { onTimePick(it, draft.minute) },
            min = 0,
            max = MaxHour,
            label = stringResource(Res.string.group_slot_hour),
            labelStyle = SaqzTheme.typography.label,
            format = { "${it}h" },
            decreaseDescription = stringResource(Res.string.group_slot_hour_before),
            increaseDescription = stringResource(Res.string.group_slot_hour_after),
            modifier = Modifier.testTag(GroupSlotSheetTags.Hour),
        )
        Text(
            text = stringResource(Res.string.group_slot_minutes),
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzSegmented(
            options = SlotSheetMinutes.map { it.toString().padStart(2, '0') },
            selected = SlotSheetMinutes.indexOf(draft.minute),
            onSelect = { onTimePick(draft.hour, SlotSheetMinutes[it]) },
            optionTags = SlotSheetMinutes.map(GroupSlotSheetTags::minute),
            modifier = Modifier.testTag(GroupSlotSheetTags.Minutes),
        )
        GroupSlotSummary(draft)
    }
}

/** "Toda terça e quinta, às 19h30." — some quando nenhum dia está marcado. */
@Composable
private fun GroupSlotSummary(draft: GroupSlotDraft) {
    val days = SlotSheetWeekdays.filter { it in draft.weekdays }
    if (days.isEmpty()) return
    val names = days.map { it.label().lowercase() }
    val and = stringResource(Res.string.group_slot_summary_and)
    val list = if (names.size == 1) names.first() else names.dropLast(1).joinToString(", ") + " $and " + names.last()
    // Sábado e domingo são masculinos: "todo sábado"; os outros dias, "toda terça".
    val everyMasculine = days.all { it == GroupWeekday.SATURDAY || it == GroupWeekday.SUNDAY }
    val every = stringResource(if (everyMasculine) Res.string.group_slot_summary_every_m else Res.string.group_slot_summary_every_f)
    val time = if (draft.minute == 0) {
        stringResource(Res.string.group_slot_hour_value, draft.hour)
    } else {
        stringResource(Res.string.group_slot_time_with_minutes, draft.hour, draft.minute.toString().padStart(2, '0'))
    }
    Text(
        text = stringResource(Res.string.group_slot_summary, "$every $list", time),
        style = SaqzTheme.typography.body,
        color = SaqzTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag(GroupSlotSheetTags.Summary),
    )
}
