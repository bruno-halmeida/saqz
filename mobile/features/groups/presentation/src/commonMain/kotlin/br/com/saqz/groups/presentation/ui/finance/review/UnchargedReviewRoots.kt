package br.com.saqz.groups.presentation.ui.finance.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun UnchargedReviewRoot(
    groupId: String,
    gameId: String,
    onBack: () -> Unit,
    /** Lote confirmado: volta para quem abriu (acerto ou caixa), que recarrega. */
    onDone: () -> Unit,
    viewModel: UnchargedReviewViewModel = koinViewModel(
        key = "uncharged-review/$groupId/$gameId",
        parameters = { parametersOf(groupId, gameId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            UnchargedReviewEffect.Done -> onDone()
        }
    }
    UnchargedReviewScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}

@Composable
fun UnchargedGamesRoot(
    groupId: String,
    onBack: () -> Unit,
    onOpenReview: (groupId: String, gameId: String) -> Unit,
    refreshVersion: Int = 0,
    viewModel: UnchargedGamesViewModel = koinViewModel(
        key = "uncharged-games/$groupId",
        parameters = { parametersOf(groupId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshWhenVisible(refreshVersion) { viewModel.onIntent(UnchargedGamesIntent.Refresh) }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is UnchargedGamesEffect.OpenReview -> onOpenReview(effect.groupId, effect.gameId)
        }
    }
    UnchargedGamesScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
