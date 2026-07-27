package br.com.saqz.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.state_loading
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

enum class SaqzButtonVariant { Primary, Secondary, Danger, Ghost }

enum class SaqzButtonSize { Sm, Md }

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
        SaqzButtonVariant.Secondary -> SaqzButtonColors(surface, primary, border = border)
        SaqzButtonVariant.Danger -> SaqzButtonColors(errorForeground, onPrimary, border = null)
        SaqzButtonVariant.Ghost -> SaqzButtonColors(Color.Transparent, primary, border = null)
    }

// Spatial press response: shrinks to the policy scale while held, 1f at rest.
// Reduced motion pins pressScale at 1f, so this returns 1f and only opacity moves.
internal fun saqzPressScale(pressed: Boolean, motion: SaqzMotionPolicy): Float =
    if (pressed) motion.pressScale else 1f

// ponytail: live press feedback is published to a custom semantics key because the
// suite runs on iosSimulatorArm64Test, which has no screenshot capture — this is the
// only way a black-box test can observe scale/opacity feedback on press.
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
    size: SaqzButtonSize = SaqzButtonSize.Md,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    labelStyle: TextStyle? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    leadingContent: (@Composable (Color) -> Unit)? = null,
    trailingContent: (@Composable (Color) -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val motion = SaqzTheme.motion
    // Pílula em toda variante e tamanho (10d): não existe botão de canto reto.
    val shape = CircleShape
    val resolved = colors.buttonColors(variant)
    val active = enabled && !loading

    // ponytail: Sm fica nos 44dp do mock em vez dos 48 do token — é a variante
    // secundária, e o alvo continua acima do mínimo da WCAG. Md carrega os 48.
    val minHeight = if (size == SaqzButtonSize.Sm) 44.dp else metrics.minimumTouchTarget
    val horizontalPadding = if (size == SaqzButtonSize.Sm) 16.dp else 20.dp
    val defaultStyle =
        if (size == SaqzButtonSize.Sm) SaqzTheme.typography.support.copy(fontWeight = SaqzTheme.typography.label.fontWeight)
        else SaqzTheme.typography.label

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
        // clickable fica depois de clip/background para a área de toque respeitar a
        // forma; graphicsLayer segue antes do clip para o feedback escalar o fundo junto.
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .semantics {
                saqzPressFeedback = SaqzPressFeedback(scale, alpha)
                if (loading) stateDescription = loadingLabel
            }
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .sizeIn(minWidth = minHeight, minHeight = minHeight)
            .clip(shape)
            .background(container, shape)
            .then(
                (borderColor ?: resolved.border)?.let { Modifier.border(1.dp, it, shape) } ?: Modifier,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = active,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = metrics.subGrid),
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
                style = labelStyle ?: defaultStyle,
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

/**
 * 10e — alvo quadrado do tamanho do toque mínimo, o glifo desenhado em [content].
 * `soft` pinta o fundo ice; `dot` marca pendência com o ponto lime.
 */
@Composable
fun SaqzIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    dot: Boolean = false,
    enabled: Boolean = true,
    size: Dp = SaqzTheme.metrics.minimumTouchTarget,
    content: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (soft) Modifier.background(colors.surfaceSoft, CircleShape) else Modifier)
            .clickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.clearAndSetSemantics {}) { content() }
        if (dot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 10.dp)
                    .size(8.dp)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

@Preview
@Composable
private fun SaqzButtonPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzButton(label = "Confirmar presença", onClick = {})
        SaqzButton(label = "Editar", onClick = {}, variant = SaqzButtonVariant.Secondary)
        SaqzButton(label = "Excluir grupo", onClick = {}, variant = SaqzButtonVariant.Danger)
        SaqzButton(label = "Cancelar", onClick = {}, variant = SaqzButtonVariant.Ghost)
        SaqzButton(label = "Criar jogo", onClick = {}, size = SaqzButtonSize.Sm)
        SaqzButton(label = "Criando grupo", onClick = {}, loading = true, fullWidth = true)
        SaqzButton(label = "Criar grupo", onClick = {}, enabled = false, fullWidth = true)
    }
}

@Preview
@Composable
private fun SaqzIconButtonPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzIconButton(onClick = {}, contentDescription = "Voltar") { SaqzIcon(SaqzIcons.ChevronLeft) }
            SaqzIconButton(onClick = {}, contentDescription = "Notificações", dot = true) {
                SaqzIcon(SaqzIcons.Bell)
            }
            SaqzIconButton(onClick = {}, contentDescription = "Buscar", soft = true) {
                SaqzIcon(SaqzIcons.Search)
            }
        }
    }
}
