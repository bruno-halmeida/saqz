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
 * `:compose-app`. [onCreateGroup] é o formulário 2a — o "+" de 2n atalha para ele quando
 * há plano ativo com vaga de grupo; [onOpenPlans] é o Fluxo 8 · Planos, o destino quando
 * não há plano entitulador.
 */
@Composable
fun GroupListRoot(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenPlans: () -> Unit,
    viewModel: GroupListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupListEffect.OpenGroup -> onOpenGroup(effect.id)
            GroupListEffect.OpenCreateGroup -> onCreateGroup()
            GroupListEffect.OpenPlans -> onOpenPlans()
        }
    }
    GroupListScreen(state = state, onIntent = viewModel::onIntent)
}
