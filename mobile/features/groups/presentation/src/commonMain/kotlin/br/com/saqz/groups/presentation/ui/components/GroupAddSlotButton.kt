package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_add_slot
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GroupAddSlotButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_setup_add_slot)
    val metrics = SaqzTheme.metrics
    val borderColor = SaqzTheme.colors.primary
    val dash = metrics.grid
    val shape = RoundedCornerShape(metrics.cardRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.buttonHeight)
            .clip(shape)
            .background(SaqzTheme.colors.surfaceSoft, shape)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        metrics.cardRadius.toPx(),
                    ),
                    style = Stroke(
                        width = metrics.subGrid.toPx() / BORDER_DIVISOR,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
                    ),
                )
            }
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.grid, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.Plus, tint = SaqzTheme.colors.primary)
        Text(label, style = SaqzTheme.typography.label, color = SaqzTheme.colors.primary)
    }
}

private const val BORDER_DIVISOR = 4

@Preview
@Composable
private fun GroupAddSlotButtonPreview() = SaqzTheme {
    GroupAddSlotButton(
        onClick = {},
        modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
    )
}
