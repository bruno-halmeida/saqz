package br.com.saqz.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.state_loading
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

enum class SaqzButtonVariant { Primary, Secondary, Ghost, Destructive }

// Container/content/border a variant paints, all pulled from the token registry.
// accent/on-accent never appear here: accent is a non-clickable status hue.
@Immutable
internal data class SaqzButtonColors(
    val container: Color,
    val content: Color,
    val border: Color?,
)

internal fun SaqzColorTokens.buttonColors(variant: SaqzButtonVariant): SaqzButtonColors =
    when (variant) {
        SaqzButtonVariant.Primary -> SaqzButtonColors(primary, onPrimary, border = null)
        SaqzButtonVariant.Secondary -> SaqzButtonColors(surface, primary, border = controlBorder)
        SaqzButtonVariant.Ghost -> SaqzButtonColors(Color.Transparent, primary, border = null)
        SaqzButtonVariant.Destructive -> SaqzButtonColors(errorForeground, onPrimary, border = null)
    }

// Spatial press response: shrinks to the policy scale while held, 1f at rest.
// Reduced motion pins pressScale at 1f, so this returns 1f and only opacity moves.
internal fun saqzPressScale(pressed: Boolean, motion: SaqzMotionPolicy): Float =
    if (pressed) motion.pressScale else 1f

// ponytail: live press feedback is published to a custom semantics key because the
// design-system suite runs on iosSimulatorArm64Test, which has no screenshot capture —
// this is the only way a black-box test can observe scale/opacity feedback on press.
@Immutable
internal data class SaqzPressFeedback(val scale: Float, val alpha: Float)

internal val SaqzPressFeedbackKey = SemanticsPropertyKey<SaqzPressFeedback>("SaqzPressFeedback")
internal var SemanticsPropertyReceiver.saqzPressFeedback by SaqzPressFeedbackKey

@Composable
fun SaqzButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SaqzButtonVariant = SaqzButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    labelStyle: TextStyle? = null,
    contentColor: Color? = null,
    leadingContent: (@Composable (Color) -> Unit)? = null,
    trailingContent: (@Composable (Color) -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val motion = SaqzTheme.motion
    val shape = RoundedCornerShape(metrics.primaryButtonRadius)
    val resolved = colors.buttonColors(variant)
    val active = enabled && !loading

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = saqzPressScale(pressed, motion),
        animationSpec = tween(motion.pressDurationMillis),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(motion.opacityFeedbackDurationMillis),
        label = "pressAlpha",
    )

    val loadingLabel = stringResource(Res.string.state_loading)
    val container = if (active) resolved.container else colors.disabledSurface
    val content = if (active) contentColor ?: resolved.content else colors.disabledForeground

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = active,
                onClickLabel = label,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .semantics {
                saqzPressFeedback = SaqzPressFeedback(scale, alpha)
                if (loading) stateDescription = loadingLabel
            }
            .sizeIn(minWidth = metrics.minimumTouchTarget, minHeight = metrics.minimumTouchTarget)
            .clip(shape)
            .background(container, shape)
            .then(
                resolved.border?.let { Modifier.border(1.dp, it, shape) } ?: Modifier,
            )
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.subGrid),
        contentAlignment = Alignment.Center,
    ) {
        // Label always reserves its width; loading only hides it behind the spinner
        // (alpha 0 keeps it measured and keeps its accessible name in the tree).
        Row(
            modifier = Modifier.alpha(if (loading) 0f else 1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke(content)
            Text(
                text = label,
                color = content,
                style = labelStyle ?: SaqzTheme.typography.bodyStrong,
            )
            trailingContent?.invoke(content)
        }
        if (loading) {
            CircularProgressIndicator(
                color = content,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SaqzButtonPreview() = SaqzTheme { SaqzButton(label = "Continuar", onClick = {}) }
