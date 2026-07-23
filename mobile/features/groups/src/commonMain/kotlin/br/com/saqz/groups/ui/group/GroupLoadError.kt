package br.com.saqz.groups.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_load_error
import br.com.saqz.groups.resources.groups_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun GroupLoadError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.groups_load_error),
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzButton(stringResource(Res.string.groups_retry), onRetry)
    }
}

@Preview
@Composable
private fun GroupLoadErrorPreview() = SaqzTheme {
    GroupLoadError(onRetry = {})
}