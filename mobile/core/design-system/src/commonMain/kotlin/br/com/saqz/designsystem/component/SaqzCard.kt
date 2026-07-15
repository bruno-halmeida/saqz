package br.com.saqz.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.theme.SaqzTheme

@Composable
fun SaqzCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val motion = SaqzTheme.motion
    val shape = RoundedCornerShape(metrics.cardRadius)

    // No shadow/gradient: a flat surface with a hairline is the whole affordance.
    val surface = Modifier
        .clip(shape)
        .background(colors.surface, shape)
        .padding(metrics.utilityCardPadding)

    if (onClick == null) {
        // Static: no clickable, so no click role/action and no press feedback.
        Box(modifier = modifier.then(surface)) { content() }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = saqzPressScale(pressed, motion),
        animationSpec = tween(motion.pressDurationMillis),
        label = "cardPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(motion.opacityFeedbackDurationMillis),
        label = "cardPressAlpha",
    )
    Box(
        modifier = modifier
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
            .sizeIn(minWidth = metrics.minimumTouchTarget, minHeight = metrics.minimumTouchTarget)
            .then(surface),
    ) { content() }
}
