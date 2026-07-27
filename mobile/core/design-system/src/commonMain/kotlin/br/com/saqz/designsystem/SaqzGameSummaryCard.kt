package br.com.saqz.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.material_sports_volleyball
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.painterResource

/**
 * 10l — o bloco de destaque do próximo jogo: eyebrow, título, local, contagem de
 * presenças e o que quer que a tela queira encaixar embaixo. A bola entra como
 * marca d'água a 5,5% no canto superior direito.
 */
@Composable
fun SaqzGameSummaryCard(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    venue: String? = null,
    address: String? = null,
    going: Int? = null,
    maybe: Int? = null,
    out: Int? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    // A marca d'água entra DEPOIS do card: o fundo ice é opaco e engoliria a bola se
    // ela fosse desenhada antes. O clip no raio do card corta o que passa da borda.
    Box(modifier = modifier.clip(RoundedCornerShape(SaqzTheme.metrics.cardRadius))) {
        SaqzCard(tone = SaqzCardTone.Soft) {
            Text(text = eyebrow, style = SaqzTheme.typography.eyebrow, color = colors.textSecondary)
            Text(text = title, style = SaqzTheme.typography.title, color = colors.textPrimary)
            if (venue != null) {
                Text(text = venue, style = SaqzTheme.typography.body, color = colors.textPrimary)
            }
            if (address != null) {
                Text(text = address, style = SaqzTheme.typography.support, color = colors.textSecondary)
            }
            if (going != null || maybe != null || out != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    going?.let { SaqzStatusChip("$it confirmados", tone = SaqzChipTone.Success, dot = true) }
                    maybe?.let { SaqzStatusChip("$it talvez", tone = SaqzChipTone.Accent) }
                    out?.let { SaqzStatusChip("$it fora", tone = SaqzChipTone.Neutral) }
                }
            }
            content?.invoke(this)
        }
        Image(
            painter = painterResource(Res.drawable.material_sports_volleyball),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.primary),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = (-28).dp)
                .size(140.dp)
                .alpha(0.055f)
                .clearAndSetSemantics {},
        )
    }
}

@Preview
@Composable
private fun SaqzGameSummaryCardPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzGameSummaryCard(
            eyebrow = "PRÓXIMO JOGO",
            title = "Ter, 28/07 · 19h30",
            venue = "CERET — Quadra 2",
            address = "Tatuapé, São Paulo",
            going = 12,
            maybe = 3,
            out = 2,
        ) {
            SaqzButton(label = "Confirmar presença", onClick = {}, fullWidth = true)
        }
        SaqzGameSummaryCard(
            eyebrow = "PRÓXIMO JOGO",
            title = "Sem jogo marcado",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
