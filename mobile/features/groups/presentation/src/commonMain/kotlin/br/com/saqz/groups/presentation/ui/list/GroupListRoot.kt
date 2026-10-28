package br.com.saqz.groups.presentation.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
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
 * owner, mesmo sem grupo criado. [onPickPosition] é a escolha da posição do "Vou" da folha do
 * Confirmar em jogo com vagas por posição, o mesmo destino da Início.
 */
@Composable
fun GroupListRoot(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onOpenPlans: () -> Unit,
    isPlanOwner: Boolean = false,
    onAcceptInviteCode: (String) -> Unit = {},
    onPickPosition: (groupId: String, gameId: String) -> Unit = { _, _ -> },
    viewModel: GroupListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // O 2n mora no shell e a ViewModel sobrevive a tudo o que empilha por cima: toda vez que a
    // lista reaparece (volta do 2a ou de um grupo, troca de aba, volta do segundo plano) ela
    // recarrega sem esqueleto. Um contador de "mudou" deixava de fora qualquer caminho que
    // esquecesse de incrementar, e a lista ficava com o estado antigo.
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(GroupListIntent.Appeared)
        onPauseOrDispose { }
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupListEffect.OpenGroup -> onOpenGroup(effect.id)
            GroupListEffect.OpenCreateGroup -> onCreateGroup()
            GroupListEffect.OpenPlans -> onOpenPlans()
            is GroupListEffect.AcceptInviteCode -> onAcceptInviteCode(effect.code)
            is GroupListEffect.PickPosition -> onPickPosition(effect.groupId, effect.gameId)
        }
    }
    GroupListScreen(state = state, onIntent = viewModel::onIntent, isPlanOwner = isPlanOwner)
}
