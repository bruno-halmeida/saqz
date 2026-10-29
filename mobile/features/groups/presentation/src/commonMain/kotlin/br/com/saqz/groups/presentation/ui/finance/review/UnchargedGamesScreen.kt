package br.com.saqz.groups.presentation.ui.finance.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.CashGroupHeader
import br.com.saqz.groups.presentation.ui.finance.CashIconTile
import br.com.saqz.groups.presentation.ui.finance.CashListRow
import br.com.saqz.groups.presentation.ui.finance.CashRowText
import br.com.saqz.groups.presentation.ui.finance.CashTileTone
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

internal object UnchargedGamesTags {
    const val Screen = "uncharged-games"
    fun game(gameId: String) = "uncharged-games-$gameId"
}

/** A lista de jogos da pendência do caixa (FIN-016), do mais recente ao mais antigo; cada um abre a revisão. */
@Composable
internal fun UnchargedGamesScreen(
    state: UnchargedGamesState,
    onBack: () -> Unit,
    onIntent: (UnchargedGamesIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(UnchargedGamesTags.Screen)) {
    SaqzTopAppBar(title = stringResource(Res.string.retro_games_title), onBack = onBack)
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
        state.loadFailed -> SaqzEmptyState(
            title = stringResource(Res.string.retro_load_failed),
            description = null,
            icon = SaqzIcons.CircleAlert,
            action = stringResource(Res.string.retro_retry),
            onAction = { onIntent(UnchargedGamesIntent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        )
        state.games.isEmpty() -> SaqzEmptyState(
            title = stringResource(Res.string.retro_games_empty),
            description = null,
            icon = SaqzIcons.Users,
            modifier = Modifier.fillMaxSize(),
        )
        else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CashGroupHeader(title = stringResource(Res.string.cash_uncharged_title, state.games.sumOf { it.peopleCount }))
            state.games.forEachIndexed { index, game ->
                CashListRow(first = index == 0, modifier = Modifier.testTag(UnchargedGamesTags.game(game.gameId))) {
                    CashIconTile(SaqzIcons.Calendar, CashTileTone.Brand)
                    CashRowText(
                        game.title,
                        if (game.peopleCount == 1) {
                            stringResource(Res.string.retro_games_people_one)
                        } else {
                            stringResource(Res.string.retro_games_people, game.peopleCount)
                        },
                    )
                    SaqzButton(
                        label = stringResource(Res.string.cash_review),
                        onClick = { onIntent(UnchargedGamesIntent.Open(game.gameId)) },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                    )
                }
            }
        }
    }
}
