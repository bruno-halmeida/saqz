package br.com.saqz.composeapp.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.authenticated_home_title
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal const val AuthenticatedHomeTag = "authenticated-home"

@Composable
internal fun AuthenticatedHomeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().testTag(AuthenticatedHomeTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.authenticated_home_title),
            style = SaqzTheme.typography.displayMedium,
            color = SaqzTheme.colors.textPrimary,
        )
    }
}
