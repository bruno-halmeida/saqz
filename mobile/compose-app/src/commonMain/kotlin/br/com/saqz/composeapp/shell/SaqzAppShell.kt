package br.com.saqz.composeapp.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_logout
import br.com.saqz.composeapp.resources.shell_signed_in
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal const val SaqzShellContentTag = "saqz-shell-content"

/**
 * C1: the empty authenticated shell — no bottom nav, no catalog, no product destination.
 * It exists so the session gate has somewhere to land once bootstrap reaches `Ready`, and
 * carries the one action that still crosses back to the gate: logout.
 *
 * ponytail: no ViewModel — the shell owns no state (AGENTS.md §15, "tela sem estado
 * próprio não ganha ViewModel"). Product screens land here from C2 onward.
 */
@Composable
internal fun SaqzAppShell(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = metrics.horizontalPadding)
            .testTag(SaqzShellContentTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            metrics.sectionVerticalPadding,
            Alignment.CenterVertically,
        ),
    ) {
        Text(
            text = stringResource(Res.string.shell_signed_in),
            style = SaqzTheme.typography.displayMedium,
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzButton(
            label = stringResource(Res.string.shell_logout),
            onClick = onLogout,
        )
    }
}

@Preview
@Composable
private fun SaqzAppShellPreview() = SaqzTheme { SaqzAppShell(onLogout = {}) }
