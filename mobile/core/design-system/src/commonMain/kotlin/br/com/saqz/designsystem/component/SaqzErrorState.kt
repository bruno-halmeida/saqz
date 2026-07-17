package br.com.saqz.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_retry
import br.com.saqz.designsystem.resources.state_error
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

// Default error view: message first, then the retry action. Retry is delegated — this
// view never knows about network or policy, it only forwards the click.
@Composable
fun SaqzErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        Text(
            text = stringResource(Res.string.state_error),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
        )
        SaqzButton(
            label = stringResource(Res.string.action_retry),
            onClick = onRetry,
        )
    }
}

@Preview
@Composable
private fun SaqzErrorStatePreview() = SaqzTheme { SaqzErrorState(onRetry = {}) }
