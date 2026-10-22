package br.com.saqz.groups.presentation.ui.pastgames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import br.com.saqz.groups.presentation.pastgames.PastGamesEffect
import br.com.saqz.groups.presentation.pastgames.PastGamesIntent
import br.com.saqz.groups.presentation.pastgames.PastGamesViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** JOGO-011 · "Jogos anteriores" do gestor. Toque abre o detalhe do jogo que já existe. */
@Composable
fun PastGamesRoot(
    groupId: String,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val viewModel: PastGamesViewModel =
        koinViewModel(key = "past-games/$groupId", parameters = { parametersOf(groupId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Volta do detalhe (acertou, marcou falta): a linha reflete o acerto pendente sem tirar a lista.
    RefreshWhenVisible { viewModel.onIntent(PastGamesIntent.Refresh) }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is PastGamesEffect.OpenGame -> onOpenGame(effect.gameId)
        }
    }
    PastGamesScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
