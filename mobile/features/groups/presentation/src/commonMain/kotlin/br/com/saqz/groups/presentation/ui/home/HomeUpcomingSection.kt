package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeUpcomingGameUi
import br.com.saqz.groups.presentation.home.HomeUpcomingStatus
import br.com.saqz.groups.presentation.ui.components.UpcomingGameRow
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
                UpcomingGameRow(
                    day = game.day,
                    month = game.month,
                    title = game.title,
                    meta = game.meta,
                    contentDescription = game.contentDescription,
                    onClick = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
                    tag = HomeUpcomingTags.row(game.gameId),
                ) {
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
        }
    }
}
