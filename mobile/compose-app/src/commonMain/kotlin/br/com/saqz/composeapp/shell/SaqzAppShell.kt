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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.composeapp.catalog.SaqzCatalogScreen
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_logout
import br.com.saqz.composeapp.resources.shell_open_catalog
import br.com.saqz.composeapp.resources.shell_signed_in
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal const val SaqzShellContentTag = "saqz-shell-content"
internal const val SaqzShellCatalogTag = "saqz-shell-catalog"

/**
 * C1: the empty authenticated shell — no bottom nav, no product destination (the design
 * system catalog below is dev-only tooling, not a product screen).
 * It exists so the session gate has somewhere to land once bootstrap reaches `Ready`, and
 * carries the one action that still crosses back to the gate: logout.
 *
 * ponytail: no ViewModel — the shell owns no state (AD-031: "ViewModel só quando há
 * estado assíncrono, persistência ou comportamento real"). Product screens land here
 * from C2 onward.
 *
 * [catalogEnabled] liga a entrada do catálogo do design system (VUL-51). Vem do
 * ambiente que a plataforma já declara em `SaqzPlatformDependencies.environment`, então
 * o flavor prod nunca mostra a entrada.
 *
 * ponytail: o catálogo troca o conteúdo do shell em vez de virar destino do
 * `NavDisplay` — `reconcileAccessStack` deriva o stack do estado de sessão e o mantém
 * com exatamente uma entrada, então um destino a mais seria apagado na próxima emissão.
 * É estado de composição, que o AGENTS.md permite em `remember`. Vira destino de
 * verdade no dia em que o stack tiver profundidade real.
 */
@Composable
internal fun SaqzAppShell(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    catalogEnabled: Boolean = false,
) {
    var catalogOpen by remember { mutableStateOf(false) }
    if (catalogEnabled && catalogOpen) {
        SaqzCatalogScreen(onBack = { catalogOpen = false }, modifier = modifier)
        return
    }
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
            style = SaqzTheme.typography.headline,
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzButton(
            label = stringResource(Res.string.shell_logout),
            onClick = onLogout,
        )
        if (catalogEnabled) {
            SaqzButton(
                label = stringResource(Res.string.shell_open_catalog),
                onClick = { catalogOpen = true },
                modifier = Modifier.testTag(SaqzShellCatalogTag),
                variant = SaqzButtonVariant.Secondary,
            )
        }
    }
}

@Preview
@Composable
private fun SaqzAppShellPreview() = SaqzTheme { SaqzAppShell(onLogout = {}) }

@Preview
@Composable
private fun SaqzAppShellDevPreview() = SaqzTheme {
    SaqzAppShell(onLogout = {}, catalogEnabled = true)
}
