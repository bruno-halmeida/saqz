package br.com.saqz.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.saqz_volleyball
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Bloco de destaque da Home nova: azul (`primary`), kicker com ponto lima, título em
 * `display`, meta opcional e o conteúdo que a tela quiser embaixo. A bola do
 * `SaqzSpinner` entra como marca d'água branca a 20% no canto superior direito.
 *
 * SPEC_DEVIATION: componente fora do export oficial.
 * Reason: redesenho da Home aprovado pelo usuário em 2026-09-16 (VUL-217). O
 * `SaqzGameSummaryCard` do export continua intacto para o detalhe do grupo e o convite.
 */
@Composable
fun SaqzHeroCard(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    meta: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.blockRadius)
    Box(modifier = modifier.fillMaxWidth().clip(shape).background(colors.primary, shape)) {
        // Marca d'água antes do conteúdo: quem vem depois desenha por cima dela.
        Image(
            painter = painterResource(Res.drawable.saqz_volleyball),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.onPrimary),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = HERO_BALL_OFFSET_X, y = HERO_BALL_OFFSET_Y)
                .size(HERO_BALL_SIZE)
                .alpha(HERO_BALL_ALPHA)
                .clearAndSetSemantics {},
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(HERO_PADDING),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.grid),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(HERO_DOT_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Ponto lima com halo: o kicker da landing.
                    Box(
                        modifier = Modifier
                            .size(HERO_DOT_SIZE + HERO_DOT_HALO * 2)
                            .background(colors.accent.copy(alpha = HERO_DOT_HALO_ALPHA), CircleShape)
                            .padding(HERO_DOT_HALO)
                            .background(colors.accent, CircleShape),
                    )
                    Text(
                        text = kicker,
                        style = SaqzTheme.typography.eyebrow,
                        color = colors.onPrimary.copy(alpha = HERO_KICKER_ALPHA),
                    )
                }
                trailing?.invoke()
            }
            Text(text = title, style = SaqzTheme.typography.display, color = colors.onPrimary)
            if (meta != null) {
                Text(
                    text = meta,
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Medium),
                    color = colors.onPrimary.copy(alpha = HERO_META_ALPHA),
                )
            }
            content?.invoke(this)
        }
    }
}

private val HERO_PADDING = 20.dp
private val HERO_BALL_SIZE = 210.dp
private val HERO_BALL_OFFSET_X = 58.dp
private val HERO_BALL_OFFSET_Y = (-62).dp
private const val HERO_BALL_ALPHA = 0.20f
private val HERO_DOT_SIZE = 8.dp
private val HERO_DOT_HALO = 4.dp
private val HERO_DOT_GAP = 9.dp
private const val HERO_DOT_HALO_ALPHA = 0.18f
private const val HERO_KICKER_ALPHA = 0.82f
private const val HERO_META_ALPHA = 0.82f

@Preview
@Composable
private fun SaqzHeroCardPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzHeroCard(
            kicker = "PRÓXIMO JOGO",
            title = "Terça, 19h30",
            meta = "28 de julho · CERET — Quadra 2 · Tatuapé",
            trailing = { SaqzStatusChip("Vôlei do CERET", tone = SaqzChipTone.Inverse) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaqzButton("Vou", onClick = {}, variant = SaqzButtonVariant.Accent, modifier = Modifier.weight(1f))
                SaqzButton(
                    "Não vou",
                    onClick = {},
                    variant = SaqzButtonVariant.Ghost,
                    contentColor = SaqzTheme.colors.onPrimary,
                    borderColor = SaqzTheme.colors.onPrimary.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SaqzHeroCard(kicker = "PRÓXIMO JOGO", title = "Sem jogo marcado", modifier = Modifier.padding(top = 8.dp))
    }
}
