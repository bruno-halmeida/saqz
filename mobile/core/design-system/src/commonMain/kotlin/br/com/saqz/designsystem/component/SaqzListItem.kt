package br.com.saqz.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.theme.SaqzTheme

@Composable
fun SaqzListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val motion = SaqzTheme.motion

    // Interaction modifier stays empty when static: no optional slot ever becomes
    // clickable on its own, only the whole row when onClick is given.
    val interaction = if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = saqzPressScale(pressed, motion),
            animationSpec = tween(motion.pressDurationMillis),
            label = "listPressScale",
        )
        val alpha by animateFloatAsState(
            targetValue = if (pressed) 0.85f else 1f,
            animationSpec = tween(motion.opacityFeedbackDurationMillis),
            label = "listPressAlpha",
        )
        Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .semantics { saqzPressFeedback = SaqzPressFeedback(scale, alpha) }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(interaction)
            .fillMaxWidth()
            .sizeIn(minHeight = metrics.minimumTouchTarget)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.subGrid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        // Layout order == reading order: leading, then headline/supporting, then trailing.
        leadingContent?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text(text = headline, style = SaqzTheme.typography.body, color = colors.textPrimary)
            supportingContent?.invoke()
        }
        trailingContent?.invoke()
    }
}
