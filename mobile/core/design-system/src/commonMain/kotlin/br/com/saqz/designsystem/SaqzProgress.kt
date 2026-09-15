package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.state_loading
import br.com.saqz.designsystem.resources.saqz_volleyball
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import org.jetbrains.compose.resources.painterResource
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/** Bola de vôlei girando, com nome acessível e suporte a movimento reduzido. */
@Composable
fun SaqzSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    onDark: Boolean = false,
    animating: Boolean = true,
) {
    val label = stringResource(Res.string.state_loading)
    val rotation = if (!animating || SaqzTheme.motion == SaqzMotionPolicy.Reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "volleyballLoading").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
            label = "volleyballRotation",
        )
    }
    Icon(
        painter = painterResource(Res.drawable.saqz_volleyball),
        contentDescription = label,
        tint = SaqzTheme.colors.primary,
        modifier = modifier
            .size(size)
            .then(if (onDark) Modifier.background(SaqzTheme.colors.surface, CircleShape) else Modifier)
            .graphicsLayer { rotationZ = rotation?.value ?: 0f }
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    )
}

/**
 * 10o — bloco de placeholder. `circle = true` faz o avatar; o resto é linha ou bloco.
 *
 * ponytail: sem shimmer — o bloco estático já diz "carregando" e não gasta uma
 * animação infinita por item de lista. Shimmer entra se alguém sentir falta.
 */
@Composable
fun SaqzSkeleton(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 16.dp,
    radius: Dp = 8.dp,
    circle: Boolean = false,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.size(width, height) else Modifier.fillMaxWidth().height(height))
            .background(SaqzTheme.colors.border, shape),
    )
}

/**
 * 10o — barra de progresso. [value] nulo é o indeterminado.
 */
@Composable
fun SaqzProgressBar(
    modifier: Modifier = Modifier,
    value: Float? = null,
) {
    val colors = SaqzTheme.colors
    val bar = modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
    if (value == null) {
        LinearProgressIndicator(modifier = bar, color = colors.primary, backgroundColor = colors.border)
    } else {
        LinearProgressIndicator(
            progress = value.coerceIn(0f, 1f),
            modifier = bar,
            color = colors.primary,
            backgroundColor = colors.border,
        )
    }
}

@Preview
@Composable
private fun SaqzProgressPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzSpinner(size = 16.dp)
            SaqzSpinner(size = 20.dp)
            SaqzSpinner(size = 30.dp)
        }
        SaqzProgressBar(value = 0.4f)
        SaqzProgressBar()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzSkeleton(width = 40.dp, height = 40.dp, circle = true)
            SaqzSkeleton(width = 160.dp, height = 14.dp)
        }
        SaqzSkeleton(height = 72.dp, radius = 12.dp)
    }
}
