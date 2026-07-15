package br.com.saqz.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzTheme

enum class SaqzBadgeVariant { Neutral, Accent, Info, Success, Warning, Error }

// Each variant is a semantic surface/foreground pair from the token registry —
// never a colour approximation. Accent pairs accent with on-accent.
@Immutable
internal data class SaqzBadgeColors(val surface: Color, val foreground: Color)

internal fun SaqzColorTokens.badgeColors(variant: SaqzBadgeVariant): SaqzBadgeColors =
    when (variant) {
        SaqzBadgeVariant.Neutral -> SaqzBadgeColors(surfaceSubtle, textPrimary)
        SaqzBadgeVariant.Accent -> SaqzBadgeColors(accent, onAccent)
        SaqzBadgeVariant.Info -> SaqzBadgeColors(infoSurface, infoForeground)
        SaqzBadgeVariant.Success -> SaqzBadgeColors(successSurface, successForeground)
        SaqzBadgeVariant.Warning -> SaqzBadgeColors(warningSurface, warningForeground)
        SaqzBadgeVariant.Error -> SaqzBadgeColors(errorSurface, errorForeground)
    }

@Composable
fun SaqzBadge(
    label: String,
    variant: SaqzBadgeVariant,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val pair = colors.badgeColors(variant)
    val shape = RoundedCornerShape(metrics.compactControlRadius)
    // Not clickable: a badge is status, never an affordance. The label carries the
    // meaning, so colour is never the sole signal.
    Box(
        modifier = modifier
            .clip(shape)
            .background(pair.surface, shape)
            .padding(horizontal = metrics.grid, vertical = metrics.subGrid),
    ) {
        Text(text = label, color = pair.foreground, style = SaqzTheme.typography.caption)
    }
}
