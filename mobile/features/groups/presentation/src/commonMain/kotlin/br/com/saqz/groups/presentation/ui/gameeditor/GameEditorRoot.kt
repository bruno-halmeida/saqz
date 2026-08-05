package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.gameeditor.GameEditorEffect
import br.com.saqz.groups.presentation.gameeditor.GameEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GameEditorRoot(
    groupId: String,
    gameId: String?,
    onBack: () -> Unit,
    onOpenGameDetail: (String) -> Unit,
    onSave: () -> Unit = {},
    viewModel: GameEditorViewModel? = null,
) {
    // VUL-204: chave determinística. Antes era `"…-${Random.nextLong()}"` guardado num
    // `rememberSaveable` (VUL-160): como o `LocalViewModelStoreOwner` era a Activity, o
    // store era do processo e reabrir "criar jogo" devolvia o editor da vez anterior, com
    // o rascunho antigo — o sorteio era o que forçava instância nova. Com o escopo por
    // destino do `NavDisplay` o store morre junto com a entrada, então o sorteio virou
    // ruído: ele fazia o `key` depender de sorte para uma identidade que a rota já dá.
    val resolvedViewModel = viewModel ?: koinViewModel(
        key = "game-editor/$groupId/${gameId ?: "create"}",
        parameters = { parametersOf(groupId, gameId) },
    )
    val state by resolvedViewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(resolvedViewModel.effects) { effect ->
        when (effect) {
            GameEditorEffect.Saved -> {
                onSave()
                onBack()
            }
            is GameEditorEffect.OpenGameDetail -> onOpenGameDetail(effect.gameId)
        }
    }
    GameEditorScreen(state = state, onBack = onBack, onIntent = resolvedViewModel::onIntent)
}
