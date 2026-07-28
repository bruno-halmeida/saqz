package br.com.saqz.composeapp.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.composeapp.catalog.SaqzCatalogScreen
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_logout
import br.com.saqz.composeapp.resources.shell_nav_games
import br.com.saqz.composeapp.resources.shell_nav_groups
import br.com.saqz.composeapp.resources.shell_nav_home
import br.com.saqz.composeapp.resources.shell_nav_profile
import br.com.saqz.composeapp.resources.shell_open_catalog
import br.com.saqz.composeapp.resources.shell_signed_in
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzNavItem
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal const val SaqzShellContentTag = "saqz-shell-content"
internal const val SaqzShellCatalogTag = "saqz-shell-catalog"
internal const val SaqzShellTabContentTag = "saqz-shell-tab-content"

internal const val SaqzShellGroupsTab = "grupos"
internal const val SaqzShellProfileTab = "perfil"
private const val SaqzShellHomeTab = "inicio"
private const val SaqzShellGamesTab = "jogos"

/**
 * O shell autenticado: a barra do 10q e o conteúdo da aba ativa. A barra é do shell e de
 * mais ninguém (VUL-72) — nenhuma das cinco telas de grupo a desenha, e o `GroupListScreen`
 * só a monta nas próprias previews, para o print bater com a célula do export.
 *
 * [groupsTab] é a aba Grupos — o `:compose-app` passa o `GroupListRoot` já com as lambdas
 * de navegação ligadas, porque quem conhece o `NavDisplay` é ele (AGENTS.md §6).
 *
 * **Início e Jogos ficam inertes**, como manda o VUL-72: o toque não leva a lugar nenhum
 * enquanto os fluxos 6 e 4 não existirem. **Perfil é a exceção deliberada**: o ticket o
 * pede inerte também, mas o botão de sair vive no shell desde o C1 e o fluxo 7 · Perfil é
 * exatamente onde ele vai morar. Deixar Perfil inerte apagaria a única saída de sessão do
 * app — e é ela que fecha a jornada `Ready → SignedOut`. Então Perfil abre o placeholder
 * que o shell já era, com o "Sair" (e, em dev, a entrada do catálogo).
 *
 * ponytail: sem ViewModel — a aba ativa cabe em `rememberSaveable` (AD-031: "ViewModel
 * só quando há estado assíncrono, persistência ou comportamento real"). Vira rota de
 * verdade quando houver mais de uma aba com tela.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SaqzAppShell(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    catalogEnabled: Boolean = false,
    groupsTab: @Composable () -> Unit = {},
) {
    // `rememberSaveable`, não `remember`: aba ativa e catálogo aberto são estado de
    // navegação, e o AGENTS.md §5 proíbe `remember` para estado de aplicação. Com
    // `remember` a rotação devolvia o usuário a Grupos e fechava o catálogo sozinha.
    //
    // Não sobem para o `NavBackStack` de propósito: trocar de aba não é empilhar destino
    // — o back do sistema não deve desfazer a troca —, e o gate de sessão colapsa o stack
    // fora de `Ready`, o que apagaria a aba escolhida a cada emissão. Viram rota no dia em
    // que houver mais de uma aba com tela de verdade.
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf(SaqzShellGroupsTab) }
    // Uma saída, dois gatilhos: a seta da barra e o back do sistema (botão no Android,
    // gesto no iOS) chamam o mesmo fechamento. Sem isto o back agiria no shell por baixo
    // — ou sairia do app — com o catálogo ainda na tela.
    BackHandler(enabled = catalogEnabled && catalogOpen) { catalogOpen = false }
    if (catalogEnabled && catalogOpen) {
        SaqzCatalogScreen(onBack = { catalogOpen = false }, modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background),
    ) {
        // O inset do topo é do shell, não da tela. O `MainActivity` chama
        // `enableEdgeToEdge()`, e as telas de grupo empilhadas escapam porque começam com
        // `SaqzTopAppBar`, que já aplica o seu `WindowInsets.statusBars`. A lista (2n) não
        // usa barra — começa no `GroupListHeader` —, então o "Grupos" ficava sob o relógio.
        // Resolver aqui e não lá vale para a próxima aba sem barra também. O rodapé fica
        // com o `SaqzBottomNav`, que já trata `navigationBars`.
        Box(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .testTag(SaqzShellTabContentTag),
        ) {
            when (activeTab) {
                SaqzShellGroupsTab -> groupsTab()
                else -> ShellPlaceholder(
                    onLogout = onLogout,
                    catalogEnabled = catalogEnabled,
                    onOpenCatalog = { catalogOpen = true },
                )
            }
        }
        SaqzBottomNav(
            items = shellNavItems(),
            activeId = activeTab,
            onSelect = { id ->
                when (id) {
                    SaqzShellGroupsTab, SaqzShellProfileTab -> activeTab = id
                    // TODO(Fluxo 6 · Home) e TODO(Fluxo 4 · Jogos): sem tela, sem destino.
                    SaqzShellHomeTab, SaqzShellGamesTab -> Unit
                    else -> Unit
                }
            },
        )
    }
}

@Composable
private fun shellNavItems() = listOf(
    SaqzNavItem(SaqzShellHomeTab, stringResource(Res.string.shell_nav_home), SaqzIcons.Home),
    SaqzNavItem(SaqzShellGamesTab, stringResource(Res.string.shell_nav_games), SaqzIcons.Calendar),
    SaqzNavItem(SaqzShellGroupsTab, stringResource(Res.string.shell_nav_groups), SaqzIcons.Users),
    SaqzNavItem(SaqzShellProfileTab, stringResource(Res.string.shell_nav_profile), SaqzIcons.User),
)

/** O shell vazio do C1, agora como conteúdo da aba Perfil. TODO(Fluxo 7 · Perfil). */
@Composable
private fun ShellPlaceholder(
    onLogout: () -> Unit,
    catalogEnabled: Boolean,
    onOpenCatalog: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Column(
        // Sem `safeDrawing` aqui: o topo agora vem do contêiner de aba e o rodapé é do
        // `SaqzBottomNav` — repetir os dois daria padding em dobro.
        modifier = Modifier
            .fillMaxSize()
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
                onClick = onOpenCatalog,
                modifier = Modifier.testTag(SaqzShellCatalogTag),
                variant = SaqzButtonVariant.Secondary,
            )
        }
    }
}

@Preview
@Composable
private fun SaqzAppShellPreview() = SaqzTheme {
    SaqzAppShell(
        onLogout = {},
        groupsTab = { Box(Modifier.fillMaxWidth().fillMaxSize()) },
    )
}

@Preview
@Composable
private fun SaqzAppShellDevPreview() = SaqzTheme {
    SaqzAppShell(onLogout = {}, catalogEnabled = true)
}
