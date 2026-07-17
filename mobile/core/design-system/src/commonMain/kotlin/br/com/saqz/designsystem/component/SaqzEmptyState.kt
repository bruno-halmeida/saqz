package br.com.saqz.designsystem.component

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.state_empty
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

// Default empty view: the pt-BR empty message. The host centres it.
@Composable
fun SaqzEmptyState(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.state_empty),
        style = SaqzTheme.typography.body,
        color = SaqzTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SaqzEmptyStatePreview() = SaqzTheme { SaqzEmptyState() }
