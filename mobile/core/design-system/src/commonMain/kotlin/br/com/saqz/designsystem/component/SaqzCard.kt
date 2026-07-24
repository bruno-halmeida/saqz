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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.Text
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

    // Estado de interação sempre criado (barato e fica ocioso no cartão estático);
    // isso mantém um único content() para o slot preservar estado interno.
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

    // Static: no clickable, so no click role/action and no press feedback.
    val pressFeedback = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .semantics { saqzPressFeedback = SaqzPressFeedback(scale, alpha) }
            .sizeIn(minWidth = metrics.minimumTouchTarget, minHeight = metrics.minimumTouchTarget)
    }
    // clickable depois de clip/background para a área de toque seguir a forma.
    val press = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    }

    // No shadow/gradient: a flat surface with a hairline is the whole affordance.
    Box(
        modifier = modifier
            .then(pressFeedback)
            .clip(shape)
            .background(colors.surface, shape)
            .then(press)
            .padding(metrics.utilityCardPadding),
    ) { content() }
}

@Preview
@Composable
private fun SaqzCardPreview() = SaqzTheme { SaqzCard { Text("Resumo do grupo") } }
