package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionIntent
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * A 1c não tem saída por callback: o voltar dela desiste da conta recém-autenticada, e
 * quem troca de tela nos dois casos — desistiu, concluiu — é o estado de sessão, que o
 * gate de rota do `SaqzNavHost` lê.
 *
 * A folha de foto fica neste root porque é estado visual local, como na edição de perfil:
 * o toque abre a escolha entre câmera e galeria; a porta nativa só entra depois.
 */
@Composable
fun IdentityCompletionRoot(
    modifier: Modifier = Modifier,
    viewModel: IdentityCompletionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var photoSheetOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        IdentityCompletionScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onPickPhoto = { photoSheetOpen = true },
        )
        IdentityPhotoSelectionSheet(
            open = photoSheetOpen,
            onClose = { photoSheetOpen = false },
            onTakePhoto = {
                photoSheetOpen = false
                viewModel.onIntent(IdentityCompletionIntent.ChooseCamera)
            },
            onChooseFromGallery = {
                photoSheetOpen = false
                viewModel.onIntent(IdentityCompletionIntent.ChooseLibrary)
            },
        )
    }
}
