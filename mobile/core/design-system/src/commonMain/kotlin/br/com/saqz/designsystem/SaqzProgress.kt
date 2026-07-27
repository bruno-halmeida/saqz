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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.state_loading
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/**
 * 10o — o spinner do design system, com o nome acessível em pt-BR. `onDark` troca
 * para o tom que sobrevive em cima do azul.
 */
@Composable
fun SaqzSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    onDark: Boolean = false,
) {
    val label = stringResource(Res.string.state_loading)
    CircularProgressIndicator(
        color = if (onDark) SaqzTheme.colors.onPrimary else SaqzTheme.colors.primary,
        strokeWidth = if (size <= 20.dp) 2.dp else 3.dp,
        modifier = modifier.size(size).semantics { contentDescription = label },
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
