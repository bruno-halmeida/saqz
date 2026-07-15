package br.com.saqz.designsystem.component

import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.state_loading
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

// Default loading view: a spinner carrying the pt-BR accessible name. The host centres it.
@Composable
fun SaqzLoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.state_loading)
    CircularProgressIndicator(
        color = SaqzTheme.colors.primary,
        modifier = modifier.semantics { contentDescription = label },
    )
}
