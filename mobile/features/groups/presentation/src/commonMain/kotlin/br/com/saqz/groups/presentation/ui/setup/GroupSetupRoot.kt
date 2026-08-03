package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.setup.GroupSetupEffect
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupStep
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A tela não navega e não conhece plataforma: cada efeito de saída sai por um callback que
 * o `:compose-app` liga (AGENTS.md §6). A seleção de foto fica neste root porque é estado
 * visual local, como na edição de perfil, e usa as portas nativas via Koin.
 *
 * Os quatro callbacks disparam **depois** do efeito correspondente — o nome está no
 * presente porque é o que a regra de nomes do Compose exige, não porque pedem a ação.
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupSetupViewModel = koinViewModel(parameters = { parametersOf(mode) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoViewModel: GroupPhotoViewModel = koinViewModel()
    val photoState by photoViewModel.state.collectAsStateWithLifecycle()
    var photoSheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(mode) {
        photoViewModel.onIntent(
            GroupPhotoIntent.BindGroup((mode as? GroupSetupMode.Edit)?.groupId),
        )
    }
    LaunchedEffect(photoState.error, photoState.changeVersion) {
        when {
            photoState.error != null -> photoSheetOpen = true
            photoState.changeVersion > 0 -> photoSheetOpen = false
        }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupSetupEffect.Created -> onGroupCreate(effect.groupId)
            GroupSetupEffect.Saved -> onGroupSave()
            GroupSetupEffect.Deleted -> onGroupDelete()
            GroupSetupEffect.DraftSaved -> onDraftSave()
            GroupSetupEffect.PickPhoto -> photoSheetOpen = true
        }
    }
    Box(modifier.fillMaxSize()) {
        when (state.step) {
            GroupSetupStep.Form -> GroupSetupScreen(
                state = state.copy(photoUrl = photoState.photoUrl),
                onIntent = viewModel::onIntent,
                onBack = onBack,
            )

            GroupSetupStep.Review -> GroupReviewScreen(state = state, onIntent = viewModel::onIntent)
        }
        GroupPhotoSelectionSheet(
            open = photoSheetOpen,
            photoUrl = photoState.photoUrl,
            onClose = { photoSheetOpen = false },
            onTakePhoto = {
                photoSheetOpen = false
                photoViewModel.onIntent(GroupPhotoIntent.ChooseCamera)
            },
            onChooseFromGallery = {
                photoSheetOpen = false
                photoViewModel.onIntent(GroupPhotoIntent.ChooseLibrary)
            },
            onRemovePhoto = { photoViewModel.onIntent(GroupPhotoIntent.Remove) },
            error = photoState.error,
        )
    }
}
