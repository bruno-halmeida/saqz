package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.saqzTapTarget
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.AttendanceSummaryUi
import br.com.saqz.groups.presentation.details.CourtSpotUi
import br.com.saqz.groups.presentation.details.GroupCourtUi
import br.com.saqz.groups.presentation.ui.pluralLabel
import br.com.saqz.groups.presentation.ui.singularLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_court_all_complete
import br.com.saqz.groups.resources.group_court_count
import br.com.saqz.groups.resources.group_court_empty
import br.com.saqz.groups.resources.group_court_legend_complete
import br.com.saqz.groups.resources.group_court_legend_open
import br.com.saqz.groups.resources.group_court_missing
import br.com.saqz.groups.resources.group_court_title
import br.com.saqz.groups.resources.group_court_waiting
import br.com.saqz.groups.resources.group_court_waiting_item
import br.com.saqz.groups.resources.home_admin_cd_scoreboard_open
import br.com.saqz.groups.resources.home_admin_score_going
import br.com.saqz.groups.resources.home_admin_score_out
import br.com.saqz.groups.resources.home_admin_score_waiting
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GroupPositionsPanelTags {
    const val Panel = "group-positions-panel"
    const val Score = "group-positions-score"
    const val AllComplete = "group-positions-all-complete"
    const val Waiting = "group-positions-waiting"

    fun spot(index: Int) = "group-positions-spot-$index"
}

/**
 * Quadros `Grupo` e `GrupoCompleto` (DEB-109): no cartão do próximo jogo de quem administra, em jogo com posições, o
 * placar vira uma linha compacta (sem "Sem resposta", que já está em "Esperando você") e, abaixo, a meia quadra do
 * 5-1 com o que falta em cada posição.
 */
@Composable
internal fun GroupPositionsPanel(
    attendance: AttendanceSummaryUi,
    court: GroupCourtUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val muted = colors.onPrimary.copy(alpha = MutedAlpha)
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.onPrimary.copy(alpha = PanelAlpha))
            // Como o placar de 4 colunas: o painel inteiro abre a lista de presença do jogo.
            .saqzTapTarget(stringResource(Res.string.home_admin_cd_scoreboard_open), onClick)
            .padding(metrics.blockGap)
            .testTag(GroupPositionsPanelTags.Panel),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        CompactScore(attendance)
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.onPrimary.copy(alpha = LineAlpha)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.group_court_title), style = SaqzTheme.typography.eyebrow, color = muted)
            if (court.allComplete) {
                Row(
                    Modifier.testTag(GroupPositionsPanelTags.AllComplete),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
                ) {
                    SaqzIcon(SaqzIcons.Check, tint = colors.onPrimary, size = SmallIcon)
                    Text(
                        stringResource(Res.string.group_court_all_complete),
                        style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight(600)),
                        color = colors.onPrimary,
                    )
                }
            }
        }
        HalfCourt(court)
        Legend(court)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactScore(attendance: AttendanceSummaryUi) {
    val colors = SaqzTheme.colors
    val items = listOf(
        Triple(attendance.going, Res.string.home_admin_score_going, colors.accent),
        Triple(attendance.notGoing, Res.string.home_admin_score_out, colors.onPrimary),
        Triple(attendance.waiting, Res.string.home_admin_score_waiting, colors.onPrimary),
    )
    FlowRow(
        Modifier.fillMaxWidth().testTag(GroupPositionsPanelTags.Score),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        items.forEach { (value, label, numberColor) ->
            val text = stringResource(label)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight(700))) { append(value.toString()) }
                    append(" ")
                    append(text)
                },
                style = SaqzTheme.typography.support,
                color = colors.onPrimary.copy(alpha = MutedAlpha),
            )
        }
    }
}

/** Borda a 24%, a rede (topo 4dp a 88%) e a linha de ataque tracejada no meio. */
@Composable
private fun HalfCourt(court: GroupCourtUi) {
    val colors = SaqzTheme.colors
    val line = colors.onPrimary.copy(alpha = LineAlpha)
    val net = colors.onPrimary.copy(alpha = MutedAlpha)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(CourtRadius))
            .border(CourtStroke, line, RoundedCornerShape(CourtRadius))
            .drawBehind {
                drawRect(net, size = size.copy(height = NetHeight.toPx()))
                val y = size.height / 2
                drawLine(
                    line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            .padding(top = NetHeight + SaqzTheme.metrics.grid, bottom = SaqzTheme.metrics.grid)
            .padding(horizontal = SaqzTheme.metrics.subGrid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        listOf(court.front, court.back).forEachIndexed { row, spots ->
            Row(Modifier.fillMaxWidth()) {
                spots.forEachIndexed { index, spot ->
                    CourtSpot(spot, Modifier.weight(1f).testTag(GroupPositionsPanelTags.spot(row * 3 + index)))
                }
            }
        }
    }
}

@Composable
private fun CourtSpot(spot: CourtSpotUi, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val muted = colors.onPrimary.copy(alpha = MutedAlpha)
    Column(
        modifier.alpha(if (spot.offered) 1f else EmptyAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        if (spot.offered) SpotMark(spot.complete, Modifier.size(MarkSize))
        Text(
            spot.position.singularLabel(),
            style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight(600)),
            color = colors.onPrimary,
            textAlign = TextAlign.Center,
        )
        when {
            !spot.offered -> Text(stringResource(Res.string.group_court_empty), style = SaqzTheme.typography.caption, color = muted)
            spot.first -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            ) {
                Text(
                    stringResource(Res.string.group_court_count, spot.confirmed, spot.slots),
                    style = SaqzTheme.typography.caption,
                    color = muted,
                )
                if (!spot.complete) {
                    SaqzStatusChip(
                        pluralStringResource(Res.plurals.group_court_missing, spot.missing, spot.missing),
                        tone = SaqzChipTone.WarningInverse,
                    )
                }
            }
        }
    }
}

/** Completa = cheia em lima com ✓ azul-escuro; tem vaga = só a borda tracejada em lima. Decorativa: o texto diz o mesmo. */
@Composable
private fun SpotMark(complete: Boolean, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    if (complete) {
        Box(modifier.background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            SaqzIcon(SaqzIcons.Check, tint = colors.textPrimary, size = MarkIcon)
        }
    } else {
        DashedCircle(colors.accent, modifier)
    }
}

@Composable
private fun DashedCircle(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val stroke = MarkStroke.toPx()
            drawCircle(
                color,
                radius = (size.minDimension - stroke) / 2,
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(court: GroupCourtUi) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val muted = colors.onPrimary.copy(alpha = MutedAlpha)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Box(Modifier.size(LegendSize).background(colors.accent, CircleShape))
            Text(stringResource(Res.string.group_court_legend_complete), style = SaqzTheme.typography.compactMeta, color = muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            DashedCircle(colors.accent, Modifier.size(LegendSize))
            Text(stringResource(Res.string.group_court_legend_open), style = SaqzTheme.typography.compactMeta, color = muted)
        }
        if (court.waiting.isNotEmpty()) {
            val items = court.waiting.map { (position, count) ->
                stringResource(
                    Res.string.group_court_waiting_item,
                    count,
                    if (count == 1) position.singularLabel() else position.pluralLabel(),
                )
            }
            Text(
                stringResource(Res.string.group_court_waiting, items.joinToString(" · ")),
                style = SaqzTheme.typography.compactMeta,
                color = muted,
                modifier = Modifier.testTag(GroupPositionsPanelTags.Waiting),
            )
        }
    }
}

private const val MutedAlpha = 0.88f
private const val PanelAlpha = 0.12f
private const val LineAlpha = 0.24f
private const val EmptyAlpha = 0.55f
private val CourtRadius = 4.dp
private val CourtStroke = 2.dp
private val NetHeight = 4.dp
private val MarkSize = 24.dp
private val MarkIcon = 14.dp
private val MarkStroke = 2.dp
private val LegendSize = 12.dp
private val SmallIcon = 14.dp
