package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.setup.GroupSetupEffect
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupStep
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A tela não navega e não conhece plataforma: cada efeito sai por um callback que o
 * `:compose-app` liga (AGENTS.md §6). `PickPhoto` sai pela mesma porta.
 *
 * Os quatro primeiros callbacks disparam **depois** do efeito correspondente — o nome
 * está no presente porque é o que a regra de nomes do Compose exige, não porque pedem
 * a ação.
 *
 * [mode] entra como parâmetro de Koin porque a ViewModel exige o estado inicial no
 * construtor, e é ele que carrega o `groupId` do `GroupsRoute.Edit`. `GroupsRoute.Create`
 * não tem argumento e vira `GroupSetupMode.Create`. A definição em si é do VUL-72, dono
 * do grafo, e tem esta forma:
 *
 * ```kotlin
 * viewModel { params -> GroupSetupViewModel(GroupSetupState(mode = params.get()), get()) }
 * ```
 *
 * Daqui sai só o argumento, para que registrar as duas rotas não precise mexer neste
 * arquivo.
 */
@Composable
fun GroupSetupRoot(
    mode: GroupSetupMode,
    onGroupCreate: (String) -> Unit,
    onGroupSave: () -> Unit,
    onGroupDelete: () -> Unit,
    onDraftSave: () -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit,
    viewModel: GroupSetupViewModel = koinViewModel(parameters = { parametersOf(mode) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupSetupEffect.Created -> onGroupCreate(effect.groupId)
            GroupSetupEffect.Saved -> onGroupSave()
            GroupSetupEffect.Deleted -> onGroupDelete()
            GroupSetupEffect.DraftSaved -> onDraftSave()
            GroupSetupEffect.PickPhoto -> onPickPhoto()
        }
    }
    when (state.step) {
        GroupSetupStep.Form -> GroupSetupScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = onBack,
        )

        GroupSetupStep.Review -> GroupReviewScreen(state = state, onIntent = viewModel::onIntent)
    }
}
