package br.com.saqz.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.game_stat_going
import br.com.saqz.designsystem.resources.game_stat_maybe
import br.com.saqz.designsystem.resources.game_stat_out
import br.com.saqz.designsystem.resources.material_sports_volleyball
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
                // O export zera o que vier nulo desde que uma das três contagens exista;
                // a linha inteira só some quando as três são nulas.
                AttendanceStats(going ?: 0, maybe ?: 0, out ?: 0)
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

/**
 * A linha de presenças do export: três colunas de largura igual entre uma divisória
 * horizontal em cima e outra embaixo, separadas por traços verticais de 1px. Número
 * grande e colorido em cima, rótulo pequeno e muted embaixo.
 */
@Composable
private fun AttendanceStats(going: Int, maybe: Int, out: Int) {
    val colors = SaqzTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        SaqzDivider()
        Row(
            // IntrinsicSize.Min dá altura aos traços verticais: sem isso o
            // fillMaxHeight() deles resolve para zero dentro da Row.
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 16.dp),
        ) {
            AttendanceStat(going, stringResource(Res.string.game_stat_going), colors.success)
            StatSeparator()
            AttendanceStat(maybe, stringResource(Res.string.game_stat_maybe), colors.warning)
            StatSeparator()
            AttendanceStat(out, stringResource(Res.string.game_stat_out), colors.errorForeground)
        }
        SaqzDivider()
    }
}

@Composable
private fun RowScope.AttendanceStat(value: Int, label: String, color: Color) = Column(
    modifier = Modifier.weight(1f),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    // lineHeight colado no glifo: o export usa `line-height:1` no número, e o leading
    // de 27 do `title` empurraria o rótulo 5sp para baixo do gap de 6 que ele pede.
    Text(
        text = "$value",
        style = SaqzTheme.typography.title.copy(lineHeight = SaqzTheme.typography.title.fontSize),
        color = color,
    )
    Text(text = label, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
}

// Local de propósito: a divisória vertical só existe nesta linha, e SaqzCard.kt é de
// outro dono. Sobe para lá no dia em que um segundo componente precisar dela.
@Composable
private fun StatSeparator() = Box(
    modifier = Modifier
        .width(1.dp)
        .fillMaxHeight()
        .background(SaqzTheme.colors.border),
)

@Preview
@Composable
private fun SaqzGameSummaryCardPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzGameSummaryCard(
            eyebrow = "PRÓXIMO JOGO",
            title = "Ter, 28/07 · 19h30",
            venue = "CERET — Quadra 2",
            address = "Tatuapé, São Paulo",
            going = 9,
            maybe = 2,
            out = 1,
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
