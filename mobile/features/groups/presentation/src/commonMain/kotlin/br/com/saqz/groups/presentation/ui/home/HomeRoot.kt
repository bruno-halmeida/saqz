package br.com.saqz.groups.presentation.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.home.HomeEffect
import br.com.saqz.groups.presentation.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoot(
    onOpenGroup: (String) -> Unit = {},
    onOpenGroups: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            HomeEffect.OpenGroups -> onOpenGroups()
            is HomeEffect.OpenGroup -> onOpenGroup(effect.groupId)
        }
    }
    HomeScreen(state = state, onIntent = viewModel::onIntent)
}
