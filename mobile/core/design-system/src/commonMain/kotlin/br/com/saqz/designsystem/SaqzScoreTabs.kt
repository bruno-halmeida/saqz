package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Cor do número de uma aba do placar: lima (quem vai), branco (espera), branco apagado (quem não vai) ou
 * vermelho claro (o Saiu do caixa, DEB-148).
 */
enum class SaqzScoreTabTone { Accent, Plain, Muted, Out }

/** Uma aba do placar: "9" + "/12" sobre "Vão". */
data class SaqzScoreTab(
    val number: String,
    val label: String,
    val suffix: String? = null,
    val tone: SaqzScoreTabTone = SaqzScoreTabTone.Plain,
    val testTag: String? = null,
)

/**
 * Placar que também é aba, no fim do cabeçalho azul da tela do jogo (DEB-116): faixa branca a 8% com
 * linha branca a 18% em cima, de borda a borda; número em `display` 28, rótulo `caption`, o selecionado
 * em branco semibold e os outros a 72%; indicador lima 44×4 colado embaixo do selecionado.
 *
 * SPEC_DEVIATION: componente fora do export oficial.
 * Reason: redesenho da tela do jogo aprovado pelo dono em 2026-10-20; controle de toque nasce no design system.
 */
@Composable
fun SaqzScoreTabs(
    tabs: List<SaqzScoreTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val line = colors.onPrimary.copy(alpha = ScoreLineAlpha)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.onPrimary.copy(alpha = ScoreBandAlpha))
            .drawBehind { drawRect(line, size = Size(size.width, 1.dp.toPx())) }
            .selectableGroup(),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(tab.testTag?.let { Modifier.testTag(it) } ?: Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = tab.number,
                            style = SaqzTheme.typography.display.copy(fontSize = 28.sp, lineHeight = 28.sp),
                            color = when (tab.tone) {
                                SaqzScoreTabTone.Accent -> colors.accent
                                SaqzScoreTabTone.Plain -> colors.onPrimary
                                SaqzScoreTabTone.Muted -> colors.onPrimary.copy(alpha = ScoreMutedAlpha)
                                SaqzScoreTabTone.Out -> colors.outOnPrimary
                            },
                            maxLines = 1,
                        )
                        if (tab.suffix != null) {
                            Text(
                                text = tab.suffix,
                                style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onPrimary.copy(alpha = ScoreSuffixAlpha),
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = tab.label,
                        style = SaqzTheme.typography.caption.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (selected) colors.onPrimary else colors.onPrimary.copy(alpha = ScoreLabelAlpha),
                        textAlign = TextAlign.Center,
                    )
                }
                if (selected) {
                    Box(
                        Modifier
                            .size(width = IndicatorWidth, height = IndicatorHeight)
                            .background(colors.accent, RoundedCornerShape(topStart = IndicatorHeight, topEnd = IndicatorHeight)),
                    )
                }
            }
        }
    }
}

private const val ScoreBandAlpha = 0.08f
private const val ScoreLineAlpha = 0.18f
private const val ScoreMutedAlpha = 0.70f
private const val ScoreSuffixAlpha = 0.60f
private const val ScoreLabelAlpha = 0.72f
private val IndicatorWidth = 44.dp
private val IndicatorHeight = 4.dp

@Preview
@Composable
private fun SaqzScoreTabsPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Box(Modifier.background(SaqzTheme.colors.primary)) {
            SaqzScoreTabs(
                tabs = listOf(
                    SaqzScoreTab("9", "Vão", "/12", SaqzScoreTabTone.Accent),
                    SaqzScoreTab("2", "Na espera"),
                    SaqzScoreTab("3", "Não vão", tone = SaqzScoreTabTone.Muted),
                ),
                selectedIndex = 0,
                onSelect = {},
            )
        }
    }
}
