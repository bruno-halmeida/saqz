package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzEditablePill
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_remove_slot
import br.com.saqz.groups.resources.group_slot_edit
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GroupSlotPill(
    slot: GroupRegularSlotForm,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = slot.startTime.take(5).replace(':', 'h')
    SaqzEditablePill(
        icon = SaqzIcons.Calendar,
        label = "${slot.weekday.label()} · $time",
        clickDescription = stringResource(Res.string.group_slot_edit, slot.weekday.label().lowercase(), time),
        onClick = onEdit,
        removeDescription = stringResource(Res.string.group_setup_remove_slot),
        onRemove = onRemove,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun GroupSlotPillPreview() = SaqzTheme {
    GroupSlotPill(
        slot = GroupRegularSlotForm(
            weekday = GroupWeekday.TUESDAY,
            startTime = "19:30",
            durationMinutes = 120,
        ),
        onEdit = {},
        onRemove = {},
        modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
    )
}
