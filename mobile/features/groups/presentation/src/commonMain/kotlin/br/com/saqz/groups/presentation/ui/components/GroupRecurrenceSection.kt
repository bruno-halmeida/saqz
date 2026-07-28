package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_recurrence_hint
import br.com.saqz.groups.resources.group_setup_recurrence_label
import br.com.saqz.groups.resources.group_setup_recurring_off
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GroupRecurrenceSection(
    recurring: Boolean,
    slots: List<GroupRegularSlotForm>,
    onRecurringChange: (Boolean) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (GroupRegularSlotForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_setup_recurrence_label)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
                Text(
                    stringResource(Res.string.group_setup_recurrence_hint),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            SaqzSwitch(
                checked = recurring,
                onCheckedChange = onRecurringChange,
                contentDescription = label,
            )
        }
        if (recurring) {
            slots.forEach { slot ->
                GroupSlotPill(slot = slot, onRemove = { onRemoveSlot(slot) })
            }
            GroupAddSlotButton(onClick = onAddSlot)
        } else {
            Text(
                stringResource(Res.string.group_setup_recurring_off),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

@Preview
@Composable
private fun GroupRecurrenceSectionPreview() = SaqzTheme {
    GroupRecurrenceSection(
        recurring = true,
        slots = listOf(
            GroupRegularSlotForm(
                weekday = GroupWeekday.TUESDAY,
                startTime = "19:30",
                durationMinutes = 120,
            ),
        ),
        onRecurringChange = {},
        onAddSlot = {},
        onRemoveSlot = {},
        modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
    )
}
