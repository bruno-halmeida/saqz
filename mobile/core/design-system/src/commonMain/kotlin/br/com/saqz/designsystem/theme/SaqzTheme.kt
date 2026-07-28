package br.com.saqz.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

internal val LocalSaqzColors = staticCompositionLocalOf { SaqzColorTokens.Light }
internal val LocalSaqzMetrics = staticCompositionLocalOf { SaqzMetrics.Default }
internal val LocalSaqzTypography = staticCompositionLocalOf { SaqzTypography.Default }
internal val LocalSaqzMotion = staticCompositionLocalOf { SaqzMotionPolicy.Normal }
internal val LocalSaqzShadows = staticCompositionLocalOf { SaqzShadows.Default }

object SaqzTheme {
    val colors: SaqzColorTokens
        @Composable @ReadOnlyComposable get() = LocalSaqzColors.current
    val metrics: SaqzMetrics
        @Composable @ReadOnlyComposable get() = LocalSaqzMetrics.current
    val typography: SaqzTypography
        @Composable @ReadOnlyComposable get() = LocalSaqzTypography.current
    val motion: SaqzMotionPolicy
        @Composable @ReadOnlyComposable get() = LocalSaqzMotion.current
    val shadows: SaqzShadows
        @Composable @ReadOnlyComposable get() = LocalSaqzShadows.current
}

@Composable
fun SaqzTheme(
    preferences: SaqzAccessibilityPreferences = SaqzAccessibilityPreferences(),
    content: @Composable () -> Unit,
) {
    // Reduce Transparency chega do iOS por SaqzAccessibilityPreferences: o chrome
    // translúcido vira superfície opaca em vez de cada componente decidir sozinho.
    val colors = SaqzColorTokens.Light.let {
        if (preferences.reduceTransparency) it.copy(chrome = it.surface) else it
    }
    val metrics = SaqzMetrics.Default
    val motion = if (preferences.reduceMotion) SaqzMotionPolicy.Reduced else SaqzMotionPolicy.Normal
    val typography = SaqzTypography.Default.withFontFamily(saqzFontFamily())
    CompositionLocalProvider(
        LocalSaqzColors provides colors,
        LocalSaqzMetrics provides metrics,
        LocalSaqzTypography provides typography,
        LocalSaqzMotion provides motion,
        LocalSaqzShadows provides SaqzShadows.Default,
    ) {
        MaterialTheme(
            colors = colors.toMaterialColors(),
            typography = typography.toMaterialTypography(),
            shapes = metrics.toMaterialShapes(),
            content = content,
        )
    }
}

private fun SaqzTypography.withFontFamily(family: FontFamily) = SaqzTypography(
    headline = headline.copy(fontFamily = family),
    title = title.copy(fontFamily = family),
    subtitle = subtitle.copy(fontFamily = family),
    body = body.copy(fontFamily = family),
    support = support.copy(fontFamily = family),
    label = label.copy(fontFamily = family),
    caption = caption.copy(fontFamily = family),
    eyebrow = eyebrow.copy(fontFamily = family),
    navigation = navigation.copy(fontFamily = family),
)

// Material 2 stays a primitive: its color/type/shape subsets are derived from the
// Saqz registries, never maintained as parallel values.
private fun SaqzColorTokens.toMaterialColors() = lightColors(
    primary = primary,
    onPrimary = onPrimary,
    secondary = primary,
    onSecondary = onPrimary,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    error = errorForeground,
    onError = onPrimary,
)

private fun SaqzTypography.toMaterialTypography() = Typography(
    h3 = headline,
    h4 = title,
    subtitle1 = subtitle,
    body1 = body,
    body2 = support,
    button = label,
    caption = caption,
    overline = eyebrow,
)

private fun SaqzMetrics.toMaterialShapes() = Shapes(
    small = RoundedCornerShape(inputRadius),
    medium = RoundedCornerShape(cardRadius),
    large = RoundedCornerShape(blockRadius),
)
