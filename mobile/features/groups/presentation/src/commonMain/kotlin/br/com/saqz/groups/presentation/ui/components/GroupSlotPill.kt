package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_remove_slot
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GroupSlotPill(
    slot: GroupRegularSlotForm,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(SaqzTheme.colors.surfaceSoft, CircleShape)
            .padding(start = SaqzTheme.metrics.blockGap, end = SaqzTheme.metrics.subGrid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        SaqzIcon(SaqzIcons.Calendar, tint = SaqzTheme.colors.primary)
        Text(
            text = "${slot.weekday.label()} · ${slot.startTime.replace(':', 'h')}",
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        SaqzIconButton(
            onClick = onRemove,
            contentDescription = stringResource(Res.string.group_setup_remove_slot),
            size = SaqzTheme.metrics.sectionGap,
        ) {
            SaqzIcon(
                SaqzIcons.Close,
                tint = SaqzTheme.colors.textSecondary,
                size = SaqzTheme.metrics.sectionGap,
            )
        }
    }
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
        onRemove = {},
        modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
    )
}
