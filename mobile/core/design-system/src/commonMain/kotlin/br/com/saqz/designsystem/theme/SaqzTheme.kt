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

object SaqzTheme {
    val colors: SaqzColorTokens
        @Composable @ReadOnlyComposable get() = LocalSaqzColors.current
    val metrics: SaqzMetrics
        @Composable @ReadOnlyComposable get() = LocalSaqzMetrics.current
    val typography: SaqzTypography
        @Composable @ReadOnlyComposable get() = LocalSaqzTypography.current
    val motion: SaqzMotionPolicy
        @Composable @ReadOnlyComposable get() = LocalSaqzMotion.current
}

@Composable
fun SaqzTheme(content: @Composable () -> Unit) {
    val colors = SaqzColorTokens.Light
    val metrics = SaqzMetrics.Default
    val motion = SaqzMotionPolicy.Normal
    val typography = SaqzTypography.Default.withFontFamily(saqzFontFamily())
    CompositionLocalProvider(
        LocalSaqzColors provides colors,
        LocalSaqzMetrics provides metrics,
        LocalSaqzTypography provides typography,
        LocalSaqzMotion provides motion,
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
    heroDisplay = heroDisplay.copy(fontFamily = family),
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    lead = lead.copy(fontFamily = family),
    body = body.copy(fontFamily = family),
    bodyStrong = bodyStrong.copy(fontFamily = family),
    caption = caption.copy(fontFamily = family),
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
    h1 = heroDisplay,
    h2 = displayLarge,
    h3 = displayMedium,
    subtitle1 = lead,
    body1 = body,
    body2 = bodyStrong,
    caption = caption,
    button = navigation,
    overline = navigation,
)

private fun SaqzMetrics.toMaterialShapes() = Shapes(
    small = RoundedCornerShape(compactControlRadius),
    medium = RoundedCornerShape(primaryButtonRadius),
    large = RoundedCornerShape(cardRadius),
)
