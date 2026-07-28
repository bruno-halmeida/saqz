package br.com.saqz.groups.presentation.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.presentation.schedule.GroupScheduleEffect
import br.com.saqz.groups.presentation.presentation.schedule.GroupScheduleViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * ponytail: o ViewModel se resolve no corpo em vez de entrar como parâmetro com default
 * `koinViewModel()`. O State carrega o `SlotDraft`, que é `internal` ao módulo, então o
 * tipo do ViewModel não pode aparecer na assinatura pública que o `:compose-app` chama.
 * Preview e teste usam `GroupScheduleScreen`, que é a função pura.
 */
@Composable
fun GroupScheduleRoot(
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val viewModel: GroupScheduleViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            // Salvar fecha a tela: o 2m é uma folha do Gerenciar do 2f.
            GroupScheduleEffect.Saved -> onBack()
            is GroupScheduleEffect.OpenGame -> onOpenGame(effect.gameId)
        }
    }
    GroupScheduleScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
