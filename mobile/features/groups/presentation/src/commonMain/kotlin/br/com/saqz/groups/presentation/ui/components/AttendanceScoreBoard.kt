package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_cd_score_going
import br.com.saqz.groups.resources.home_admin_cd_score_out
import br.com.saqz.groups.resources.home_admin_cd_score_pending
import br.com.saqz.groups.resources.home_admin_cd_scoreboard_open
import br.com.saqz.groups.resources.home_admin_score_going
import br.com.saqz.groups.resources.home_admin_score_out
import br.com.saqz.groups.resources.home_admin_score_pending
import br.com.saqz.groups.resources.home_admin_score_value
import org.jetbrains.compose.resources.stringResource

/** `testTag` das três colunas do placar: cada tela responde pelo próprio inventário. */
@Immutable
internal data class AttendanceScoreBoardTags(
    val going: String,
    val out: String,
    val pending: String,
)

/**
 * Placar em 3 colunas sobre o hero azul: painel branco a 10%, traços brancos a 18%,
 * números em `display` 28sp — Vão em lima, Não vão em branco a 70%, Sem resposta em
 * warning. SEM Talvez (decisão do projeto). Nasceu no hero do gestor da Início (VUL-192);
 * rótulos e descrições continuam nas chaves `home_admin_score_*`/`home_admin_cd_*`.
 */
@Composable
internal fun AttendanceScoreBoard(
    going: Int,
    out: Int,
    pending: Int,
    onClick: () -> Unit,
    tags: AttendanceScoreBoardTags,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.inputRadius))
            .background(colors.onPrimary.copy(alpha = ScorePanelAlpha))
            .clickable(
                onClickLabel = stringResource(Res.string.home_admin_cd_scoreboard_open),
                role = Role.Button,
                onClick = onClick,
            )
            // Funde as descrições das três colunas num nó só: o placar inteiro é um botão.
            .semantics(mergeDescendants = true) {}
            .height(IntrinsicSize.Min)
            .padding(vertical = metrics.blockGap),
    ) {
        ScoreColumn(
            value = going,
            label = stringResource(Res.string.home_admin_score_going),
            color = colors.accent,
            contentDescription = stringResource(Res.string.home_admin_cd_score_going, going),
            modifier = Modifier.testTag(tags.going),
        )
        ScoreDivider()
        ScoreColumn(
            value = out,
            label = stringResource(Res.string.home_admin_score_out),
            color = colors.onPrimary.copy(alpha = ScoreOutAlpha),
            contentDescription = stringResource(Res.string.home_admin_cd_score_out, out),
            modifier = Modifier.testTag(tags.out),
        )
        ScoreDivider()
        ScoreColumn(
            value = pending,
            label = stringResource(Res.string.home_admin_score_pending),
            color = colors.warning,
            contentDescription = stringResource(Res.string.home_admin_cd_score_pending, pending),
            modifier = Modifier.testTag(tags.pending),
        )
    }
}

@Composable
private fun ScoreDivider() = Box(
    modifier = Modifier
        .width(ScoreDividerWidth)
        .fillMaxHeight()
        .background(SaqzTheme.colors.onPrimary.copy(alpha = ScoreDividerAlpha)),
)

@Composable
private fun RowScope.ScoreColumn(
    value: Int,
    label: String,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .weight(1f)
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(
            text = stringResource(Res.string.home_admin_score_value, value),
            style = SaqzTheme.typography.display.copy(fontSize = ScoreValueSize, lineHeight = ScoreValueSize),
            color = color,
        )
        Text(
            text = label,
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.onPrimary.copy(alpha = ScoreLabelAlpha),
        )
    }
}

private const val ScorePanelAlpha = 0.10f
private const val ScoreDividerAlpha = 0.18f
private const val ScoreOutAlpha = 0.7f
private const val ScoreLabelAlpha = 0.72f
private val ScoreValueSize = 28.sp
private val ScoreDividerWidth = 1.dp
