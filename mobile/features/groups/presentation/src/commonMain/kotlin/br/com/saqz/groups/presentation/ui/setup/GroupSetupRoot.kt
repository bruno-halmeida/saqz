package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    onGroupCreate: (groupId: String, photoFailed: Boolean) -> Unit,
    onGroupSave: () -> Unit,
    onGroupDelete: () -> Unit,
    onDraftSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupSetupViewModel = koinViewModel(
        key = "group-setup/$mode",
        parameters = { parametersOf(mode) },
    ),
    photoViewModel: GroupPhotoViewModel = koinViewModel(
        key = "group-photo/${(mode as? GroupSetupMode.Edit)?.groupId ?: "create"}",
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoState by photoViewModel.state.collectAsStateWithLifecycle()
    var photoSheetOpen by rememberSaveable { mutableStateOf(false) }
    var pendingLeave by remember { mutableStateOf<GroupPhotoLeave?>(null) }
    val createdGroup by rememberUpdatedState(onGroupCreate)
    val savedGroup by rememberUpdatedState(onGroupSave)
    val photos by rememberUpdatedState(photoViewModel)
    val boundGroupId = (mode as? GroupSetupMode.Edit)?.groupId

    LaunchedEffect(boundGroupId) {
        photos.onIntent(GroupPhotoIntent.BindGroup(boundGroupId))
    }
    LaunchedEffect(photoState.error, photoState.changeVersion, pendingLeave) {
        when {
            photoState.error != null && pendingLeave !is GroupPhotoLeave.Created -> photoSheetOpen = true
            photoState.changeVersion > 0 -> photoSheetOpen = false
        }
    }
    // Só `isLoading` não basta: no quadro em que o create/save dispara, a foto ainda
    // está retida (`hasPending`) e o PUT nem começou. Sair aí derruba a ViewModel e
    // o `onCleared` apaga o arquivo — criar e editar pareciam não gravar a imagem.
    // Se o encode ainda roda, Commit/BindGroup no efeito seria no-op; o efeito
    // dispara de novo quando `hasPending` fica true.
    //
    // Create + foto falhou: o grupo já existe. Sair avisa a lista e abre o detalhe
    // com o aviso. Ficar no 2a fazia o retry disparar outro POST. Edit pode ficar:
    // o grupo já estava na lista e a foto ainda pode ser tentada aqui.
    LaunchedEffect(pendingLeave, photoState.isLoading, photoState.hasPending, photoState.error) {
        val leave = pendingLeave ?: return@LaunchedEffect
        if (photoState.isLoading) return@LaunchedEffect
        if (photoState.hasPending) {
            when (leave) {
                is GroupPhotoLeave.Created -> photos.onIntent(GroupPhotoIntent.BindGroup(leave.groupId))
                GroupPhotoLeave.Saved -> photos.onIntent(GroupPhotoIntent.Commit)
            }
            return@LaunchedEffect
        }
        pendingLeave = null
        val photoFailed = photoState.error != null
        if (photoFailed && leave is GroupPhotoLeave.Saved) return@LaunchedEffect
        when (leave) {
            is GroupPhotoLeave.Created -> createdGroup(leave.groupId, photoFailed)
            GroupPhotoLeave.Saved -> savedGroup()
        }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupSetupEffect.Created -> {
                if (photoViewModel.state.value.hasPending || photoViewModel.state.value.isLoading) {
                    pendingLeave = GroupPhotoLeave.Created(effect.groupId)
                } else {
                    onGroupCreate(effect.groupId, false)
                }
            }
            GroupSetupEffect.Saved -> {
                if (photoViewModel.state.value.hasPending || photoViewModel.state.value.isLoading) {
                    pendingLeave = GroupPhotoLeave.Saved
                } else {
                    onGroupSave()
                }
            }
            GroupSetupEffect.Deleted -> onGroupDelete()
            GroupSetupEffect.DraftSaved -> onDraftSave()
            GroupSetupEffect.PickPhoto -> photoSheetOpen = true
        }
    }
    val screenState = state.copy(
        photoUrl = photoState.photoUrl,
        photo = photoState.preview,
        isSaving = state.isSaving || pendingLeave != null,
    )
    Box(modifier.fillMaxSize()) {
        when (state.step) {
            GroupSetupStep.Form -> GroupSetupScreen(
                state = screenState,
                onIntent = viewModel::onIntent,
                onBack = onBack,
            )

            GroupSetupStep.Review -> GroupReviewScreen(state = screenState, onIntent = viewModel::onIntent)
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

private sealed interface GroupPhotoLeave {
    data class Created(val groupId: String) : GroupPhotoLeave

    data object Saved : GroupPhotoLeave
}
