package br.com.saqz.groups.presentation.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.list.GroupListEffect
import br.com.saqz.groups.presentation.list.GroupListViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navegação entre features é callback (AGENTS.md §6): quem conhece o `NavDisplay` é o
 * `:compose-app`. [onOpenPlans] é o Fluxo 8 · Planos — não o formulário de criação.
 */
@Composable
fun GroupListRoot(
    onOpenGroup: (String) -> Unit,
    onOpenPlans: () -> Unit,
    onJoinWithCode: () -> Unit,
    viewModel: GroupListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupListEffect.OpenGroup -> onOpenGroup(effect.id)
            GroupListEffect.OpenPlans -> onOpenPlans()
            GroupListEffect.OpenJoinWithCode -> onJoinWithCode()
        }
    }
    GroupListScreen(state = state, onIntent = viewModel::onIntent)
}
