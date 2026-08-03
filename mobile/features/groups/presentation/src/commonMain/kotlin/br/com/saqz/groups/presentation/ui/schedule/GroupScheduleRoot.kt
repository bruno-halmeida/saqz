package br.com.saqz.groups.presentation.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.schedule.GroupScheduleEffect
import br.com.saqz.groups.presentation.schedule.GroupScheduleIntent
import br.com.saqz.groups.presentation.schedule.GroupScheduleViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [groupId] é o `GroupsRoute.Schedule.groupId` que o `NavDisplay` já carrega, e daqui sai
 * como parâmetro de Koin porque a ViewModel o exige no construtor. A definição em si
 * (`viewModel { params -> GroupScheduleViewModel(params.get()) }`) é do VUL-72, dono do
 * grafo — e precisa morar dentro deste módulo, porque a ViewModel é `internal`.
 *
 * ponytail: o ViewModel se resolve no corpo em vez de entrar como parâmetro com default
 * `koinViewModel()`. O State carrega o `SlotDraft`, que é `internal` ao módulo, então o
 * tipo do ViewModel não pode aparecer na assinatura pública que o `:compose-app` chama.
 * Preview e teste usam `GroupScheduleScreen`, que é a função pura.
 */
@Composable
fun GroupScheduleRoot(
    groupId: String,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
    refreshVersion: Int = 0,
) {
    val viewModel: GroupScheduleViewModel = koinViewModel(parameters = { parametersOf(groupId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0) viewModel.onIntent(GroupScheduleIntent.Retry)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            // Salvar fecha a tela: o 2m é uma folha do Gerenciar do 2f.
            GroupScheduleEffect.Saved -> onBack()
            is GroupScheduleEffect.OpenGame -> onOpenGame(effect.gameId)
        }
    }
    GroupScheduleScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
