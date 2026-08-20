package br.com.saqz.groups.presentation.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.list.GroupListEffect
import br.com.saqz.groups.presentation.list.GroupListIntent
import br.com.saqz.groups.presentation.list.GroupListViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navegação entre features é callback (AGENTS.md §6): quem conhece o `NavDisplay` é o
 * `:compose-app`. [onCreateGroup] é o formulário 2a — o "+" de 2n atalha para ele quando
 * há plano ativo com vaga de grupo; [onOpenPlans] é o Fluxo 8 · Planos, o destino quando
 * não há plano entitulador. [isPlanOwner] muda o vazio do 2o: quem já paga o plano é
 * owner, mesmo sem grupo criado.
 */
@Composable
fun GroupListRoot(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenPlans: () -> Unit,
    isPlanOwner: Boolean = false,
    refreshVersion: Int = 0,
    viewModel: GroupListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // VUL-205: só recarrega se o contador mudou desde que esta ViewModel nasceu. O 2n
    // mora no shell, então a ViewModel sobrevive ao 2a empilhado; sem o bump, criar um
    // grupo e voltar mostrava a lista vazia de quando a aba montou.
    val loadedVersion = rememberSaveable(viewModel) { refreshVersion }
    LaunchedEffect(viewModel, refreshVersion) {
        if (refreshVersion != loadedVersion) viewModel.onIntent(GroupListIntent.Refresh)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupListEffect.OpenGroup -> onOpenGroup(effect.id)
            GroupListEffect.OpenCreateGroup -> onCreateGroup()
            GroupListEffect.OpenPlans -> onOpenPlans()
        }
    }
    GroupListScreen(state = state, onIntent = viewModel::onIntent, isPlanOwner = isPlanOwner)
}
