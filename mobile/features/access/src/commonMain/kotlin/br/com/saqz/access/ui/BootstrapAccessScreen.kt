package br.com.saqz.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
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
    autoRetryIntervalMillis: Long = AUTO_RETRY_INTERVAL_MILLIS,
    autoRetryMaxAttempts: Int = AUTO_RETRY_MAX_ATTEMPTS,
) {
    // Sem monitor de conectividade, a volta da rede se descobre tentando de novo. O erro do
    // bootstrap é "não deu para carregar" (queda de rede ou backend fora; identidade morta já
    // sai da conta), então vale o mesmo que o AutoRetry das telas de grupos: a cada 5 s, com
    // teto. O toque manual zera a conta.
    var autoAttempts by remember { mutableIntStateOf(0) }
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(state, autoAttempts) {
        if (state == SessionAccessState.BootstrapError && autoAttempts < autoRetryMaxAttempts) {
            delay(autoRetryIntervalMillis)
            autoAttempts++
            currentOnIntent(SessionIntent.RetryBootstrap)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            SessionAccessState.Bootstrapping -> SaqzSpinner(Modifier.testTag("bootstrap-loading"))
            SessionAccessState.BootstrapError -> {
                androidx.compose.material.Text(
                    stringResource(Res.string.bootstrap_error),
                    color = SaqzTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(20.dp))
                SaqzButton(
                    stringResource(Res.string.action_retry),
                    {
                        autoAttempts = 0
                        onIntent(SessionIntent.RetryBootstrap)
                    },
                )
            }
            else -> Unit
        }
    }
}

private const val AUTO_RETRY_INTERVAL_MILLIS = 5_000L
private const val AUTO_RETRY_MAX_ATTEMPTS = 24
