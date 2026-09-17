package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeUpcomingGameUi
import br.com.saqz.groups.presentation.home.HomeUpcomingStatus
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_upcoming_title
import org.jetbrains.compose.resources.stringResource

internal object HomeUpcomingTags {
    const val Section = "home-upcoming"

    fun row(gameId: String) = "home-upcoming-$gameId"
}

/**
 * VUL-221 — os jogos depois do hero, em todos os grupos: data, grupo e hora, quantos
 * confirmaram e o estado do próprio usuário. Toque abre o jogo. Some sem jogos: a Home
 * é do "agora", e uma seção vazia não é informação.
 */
@Composable
internal fun HomeUpcomingSection(
    games: List<HomeUpcomingGameUi>,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) return
    Column(
        modifier = modifier.testTag(HomeUpcomingTags.Section),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_upcoming_title))
        SaqzCard(padded = false) {
            games.forEachIndexed { index, game ->
                if (index > 0) SaqzDivider()
                HomeUpcomingRow(game = game, onClick = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) })
            }
        }
    }
}

@Composable
private fun HomeUpcomingRow(game: HomeUpcomingGameUi, onClick: () -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clickable(onClickLabel = game.contentDescription, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = game.contentDescription }
            .testTag(HomeUpcomingTags.row(game.gameId))
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Column(
            modifier = Modifier
                .size(DateBoxSize)
                .background(colors.surfaceSoft, RoundedCornerShape(metrics.inputRadius)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = game.day, style = SaqzTheme.typography.dateDay, color = colors.textPrimary)
            Text(text = game.month, style = SaqzTheme.typography.dateMonth, color = colors.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = game.title, style = SaqzTheme.typography.compactTitle, color = colors.textPrimary)
            Text(text = game.meta, style = SaqzTheme.typography.compactMeta, color = colors.textSecondary)
        }
        SaqzStatusChip(
            text = game.statusLabel,
            tone = when (game.status) {
                HomeUpcomingStatus.Going -> SaqzChipTone.Success
                HomeUpcomingStatus.Waitlisted -> SaqzChipTone.Warning
                HomeUpcomingStatus.Pending, HomeUpcomingStatus.Out -> SaqzChipTone.Neutral
            },
            dot = game.status == HomeUpcomingStatus.Going || game.status == HomeUpcomingStatus.Waitlisted,
        )
    }
}

private val DateBoxSize = 44.dp
