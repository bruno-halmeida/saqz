package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gameeditor.GameEditorIntent
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.ui.GroupLoadFailure

internal object GameEditorTags {
    const val Screen = "game-editor"
}

/**
 * 4 · editor de jogo — scaffold do VUL-151. Carregando e falha; o formulário é VUL-153.
 */
@Composable
internal fun GameEditorScreen(
    state: GameEditorState,
    onBack: () -> Unit,
    onIntent: (GameEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag(GameEditorTags.Screen)) {
        SaqzTopAppBar(title = null, onBack = onBack)
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
        } else if (state.loadFailed) {
            GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(GameEditorIntent.Retry) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview
@Composable
private fun GameEditorLoadingPreview() = SaqzTheme {
    GameEditorScreen(state = GameEditorState(isLoading = true), onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GameEditorLoadedPreview() = SaqzTheme {
    GameEditorScreen(state = GameEditorState(isLoading = false), onBack = {}, onIntent = {})
}
