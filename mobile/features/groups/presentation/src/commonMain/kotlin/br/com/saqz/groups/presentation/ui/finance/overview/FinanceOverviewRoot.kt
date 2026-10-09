package br.com.saqz.groups.presentation.ui.finance.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewEffect
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewIntent
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FinanceOverviewRoot(
    onOpenGroup: (String) -> Unit,
    viewModel: FinanceOverviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A cada vez que a aba aparece; a primeira ativação é ignorada pela ViewModel (o `init` já carregou).
    RefreshWhenVisible { viewModel.onIntent(FinanceOverviewIntent.TabActive) }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is FinanceOverviewEffect.OpenGroup -> onOpenGroup(effect.groupId)
        }
    }
    FinanceOverviewScreen(state = state, onIntent = viewModel::onIntent)
}
