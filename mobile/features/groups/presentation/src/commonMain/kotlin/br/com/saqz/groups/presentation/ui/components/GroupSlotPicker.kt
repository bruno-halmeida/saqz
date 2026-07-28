package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.ui.shortLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_slot_time
import br.com.saqz.groups.resources.group_slot_weekday
import org.jetbrains.compose.resources.stringResource

internal data class SlotDraft(
    val weekday: GroupWeekday,
    val hour: Int,
    val minute: Int,
)

@Composable
internal fun GroupSlotPicker(
    draft: SlotDraft,
    onDayPick: (GroupWeekday) -> Unit,
    onTimePick: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val options = timeOptions(draft)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        PickerLabel(stringResource(Res.string.group_slot_weekday))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            PICKER_WEEKDAYS.forEach { day ->
                SaqzChoiceChip(
                    label = day.shortLabel(),
                    selected = day == draft.weekday,
                    onClick = { onDayPick(day) },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PickerLabel(stringResource(Res.string.group_slot_time))
        Column {
            options.forEachIndexed { index, option ->
                val selected = option.hour == draft.hour && option.minute == draft.minute
                val shape = RoundedCornerShape(metrics.cardRadius)
                Text(
                    text = option.formattedTime(),
                    style = if (selected) {
                        SaqzTheme.typography.title.copy(fontWeight = FontWeight.ExtraBold)
                    } else {
                        SaqzTheme.typography.body
                    },
                    color = when {
                        selected -> SaqzTheme.colors.primary
                        index == 0 -> SaqzTheme.colors.textPlaceholder
                        else -> SaqzTheme.colors.textSecondary
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = metrics.minimumTouchTarget)
                        .clip(shape)
                        .then(
                            if (selected) {
                                Modifier
                                    .background(SaqzTheme.colors.surfaceSoft, shape)
                                    .border(metrics.subGrid / BORDER_DIVISOR, SaqzTheme.colors.primary, shape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            role = Role.RadioButton,
                            onClickLabel = option.formattedTime(),
                        ) { onTimePick(option.hour, option.minute) }
                        .padding(vertical = metrics.blockGap),
                )
            }
        }
    }
}

@Composable
private fun PickerLabel(text: String) = Text(
    text = text,
    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Bold),
    color = SaqzTheme.colors.textSecondary,
)

private fun timeOptions(draft: SlotDraft): List<SlotDraft> = listOf(-2, -1, 0, 1).map { offset ->
    val totalMinutes = (
        draft.hour * MINUTES_PER_HOUR + draft.minute + offset * PICKER_STEP_MINUTES + MINUTES_PER_DAY
    ) % MINUTES_PER_DAY
    draft.copy(
        hour = totalMinutes / MINUTES_PER_HOUR,
        minute = totalMinutes % MINUTES_PER_HOUR,
    )
}

private fun SlotDraft.formattedTime(): String =
    "${hour.toString().padStart(2, '0')}h${minute.toString().padStart(2, '0')}"

private val PICKER_WEEKDAYS = listOf(
    GroupWeekday.SUNDAY,
    GroupWeekday.MONDAY,
    GroupWeekday.TUESDAY,
    GroupWeekday.WEDNESDAY,
    GroupWeekday.THURSDAY,
    GroupWeekday.FRIDAY,
    GroupWeekday.SATURDAY,
)
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 1_440
private const val PICKER_STEP_MINUTES = 30
private const val BORDER_DIVISOR = 4

@Preview
@Composable
private fun GroupSlotPickerPreview() = SaqzTheme {
    GroupSlotPicker(
        draft = SlotDraft(GroupWeekday.TUESDAY, hour = 19, minute = 30),
        onDayPick = {},
        onTimePick = { _, _ -> },
        modifier = Modifier
            .background(SaqzTheme.colors.surface)
            .padding(SaqzTheme.metrics.horizontalPadding),
    )
}
