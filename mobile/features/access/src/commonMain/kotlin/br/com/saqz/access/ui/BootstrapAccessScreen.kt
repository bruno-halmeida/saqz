package br.com.saqz.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_retry
import br.com.saqz.access.resources.bootstrap_error
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun BootstrapAccessScreen(
    state: SessionAccessState,
    onIntent: (SessionIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background),
    ) {
        when (state) {
            SessionAccessState.Bootstrapping -> SaqzSpinner(Modifier.testTag("bootstrap-loading"))
            SessionAccessState.BootstrapError -> {
                androidx.compose.material.Text(
                    stringResource(Res.string.bootstrap_error),
                    color = SaqzTheme.colors.textPrimary,
                )
                SaqzButton(stringResource(Res.string.action_retry), { onIntent(SessionIntent.RetryBootstrap) })
            }
            else -> Unit
        }
    }
}
