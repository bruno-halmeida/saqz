package br.com.saqz.groups.presentation.ui.gamedetail

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
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.ui.GroupLoadFailure

internal object GameDetailTags {
    const val Screen = "game-detail"
}
@Composable
internal fun GameDetailScreen(
    state: GameDetailState,
    onBack: () -> Unit,
    onIntent: (GameDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag(GameDetailTags.Screen)) {
        SaqzTopAppBar(title = null, onBack = onBack)
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
        } else if (state.loadFailed) {
            GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(GameDetailIntent.Retry) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview
@Composable
private fun GameDetailPreview() = SaqzTheme {
    GameDetailScreen(state = GameDetailState(), onBack = {}, onIntent = {})
}
