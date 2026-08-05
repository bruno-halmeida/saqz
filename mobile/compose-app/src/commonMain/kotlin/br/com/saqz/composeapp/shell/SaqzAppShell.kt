package br.com.saqz.composeapp.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.composeapp.catalog.SaqzCatalogScreen
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_nav_finance
import br.com.saqz.composeapp.resources.shell_nav_groups
import br.com.saqz.composeapp.resources.shell_nav_home
import br.com.saqz.composeapp.resources.shell_nav_profile
import br.com.saqz.composeapp.resources.shell_open_catalog
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
internal const val SaqzShellHomeTab = "inicio"
private const val SaqzShellFinanceTab = "financeiro"

/**
 * O shell autenticado: a barra do 10q e o conteúdo da aba ativa. A barra é do shell e de
 * mais ninguém (VUL-72) — nenhuma das cinco telas de grupo a desenha, e o `GroupListScreen`
 * só a monta nas próprias previews, para o print bater com a célula do export.
 *
 * [groupsTab] é a aba Grupos — o `:compose-app` passa o `GroupListRoot` já com as lambdas
 * de navegação ligadas, porque quem conhece o `NavDisplay` é ele (AGENTS.md §6).
 *
 * [banner] é a faixa persistente acima do conteúdo, e hoje só a de e-mail não confirmado
 * (VUL-91) mora lá. Entra por slot e não por `Boolean`: quem sabe se ela tem o que dizer é
 * quem tem o estado da sessão, e o shell não tem — nem precisa ter, porque a faixa não
 * decide nada aqui dentro.
 *
 * Início é a aba inicial do shell (VUL-193): o login cai aqui, e [homeTab] recebe a Home do
 * Fluxo 6. Perfil recebe o conteúdo real por [profileTab]; Financeiro recebe o caixa geral
 * por [financeTab]; a saída de sessão pertence à 7a/7e, e o shell só continua dono da barra
 * e da entrada opcional do catálogo de desenvolvimento.
 *
 * A barra tem **três abas para membro comum** e **quatro para quem administra grupo**
 * (VUL-200): [financeTabVisible] acende a aba Financeiro. Quem decide é o host — o shell não
 * conhece papel nenhum, só desenha o que recebe. Jogos saiu de vez: a agenda mora no detalhe
 * do grupo e o próximo jogo já está na Home.
 *
 * ponytail: sem ViewModel — a aba ativa cabe em `rememberSaveable` (AD-031: "ViewModel
 * só quando há estado assíncrono, persistência ou comportamento real"). Vira rota de
 * verdade quando houver mais de uma aba com tela.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SaqzAppShell(
    modifier: Modifier = Modifier,
    catalogEnabled: Boolean = false,
    initialTab: String = SaqzShellHomeTab,
    financeTabVisible: Boolean = false,
    groupsTab: @Composable () -> Unit = {},
    homeTab: @Composable (onOpenGroups: () -> Unit) -> Unit = {},
    profileTab: @Composable () -> Unit = {},
    financeTab: @Composable () -> Unit = {},
    banner: @Composable () -> Unit = {},
) {
    // `rememberSaveable`, não `remember`: aba ativa e catálogo aberto são estado de
    // navegação, e o AGENTS.md §5 proíbe `remember` para estado de aplicação. Com
    // `remember` a rotação devolvia o usuário a Grupos e fechava o catálogo sozinha.
    //
    // Não sobem para o `NavBackStack` de propósito: trocar de aba não é empilhar destino
    // — o back do sistema não deve desfazer a troca —, e o gate de sessão colapsa o stack
    // fora de `Ready`, o que apagaria a aba escolhida a cada emissão. Viram rota no dia em
    // que houver mais de uma aba com tela de verdade.
    //
    // VUL-193: Início é a aba inicial — o login cai na Home do Fluxo 6, não em Grupos.
    // [initialTab] deixa o invite landing pedir a aba Grupos explicitamente: os callbacks
    // dele prometem a lista de grupos, e o `resetTo` do shell agora carrega essa intenção.
    // `rememberSaveable` semeia só na primeira composição — trocar `initialTab` depois não
    // sobrescreve a aba que a pessoa escolheu, e a rotação devolve a aba em uso.
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    val navItems = shellNavItems(financeTabVisible)
    // VUL-200: a aba ativa é sempre uma aba que a barra desenha. Aba que sumiu da barra
    // cai na Início — vale para a Caixa de quem deixou de administrar grupo (o papel pode
    // mudar debaixo da tela), para um `initialTab` desconhecido e para o "jogos" que um
    // `rememberSaveable` de versão anterior ainda possa restaurar. Derivado em vez de
    // efeito: assim não existe um quadro sequer com a aba ativa apontando para item que
    // não está lá.
    val activeTab = if (navItems.any { it.id == selectedTab }) selectedTab else SaqzShellHomeTab
    // Uma saída, dois gatilhos: a seta da barra e o back do sistema (botão no Android,
    // gesto no iOS) chamam o mesmo fechamento. Sem isto o back agiria no shell por baixo
    // — ou sairia do app — com o catálogo ainda na tela.
    BackHandler(enabled = catalogEnabled && catalogOpen) { catalogOpen = false }
    if (catalogEnabled && catalogOpen) {
        SaqzCatalogScreen(onBack = { catalogOpen = false }, modifier = modifier)
        return
    }
    Column(
        // O inset do topo é do contêiner que não tem `SaqzTopAppBar`. O `MainActivity` chama
        // `enableEdgeToEdge()`: a lista de grupos (2n) começa no `GroupListHeader`, então o
        // shell aplica o inset nessa aba; a 7a começa com `SaqzTopAppBar`, que já o aplica.
        // A faixa recebe o inset no próprio slot quando Perfil está ativa. O rodapé fica com
        // o `SaqzBottomNav`, que já trata `navigationBars`.
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .then(
                if (activeTab == SaqzShellGroupsTab) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    Modifier
                },
            ),
    ) {
        // Acima do conteúdo e fora do `Box` com peso: a faixa empurra a aba para baixo em
        // vez de flutuar sobre ela — nada do que a pessoa ia tocar fica coberto.
        Box(
            modifier = if (activeTab == SaqzShellGroupsTab) {
                Modifier
            } else {
                Modifier.windowInsetsPadding(WindowInsets.statusBars)
            },
        ) {
            banner()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .testTag(SaqzShellTabContentTag),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SaqzShellContentTag),
            ) {
                when (activeTab) {
                    SaqzShellGroupsTab -> groupsTab()
                    SaqzShellHomeTab -> homeTab { selectedTab = SaqzShellGroupsTab }
                    SaqzShellProfileTab -> profileTab()
                    SaqzShellFinanceTab -> financeTab()
                    else -> Unit
                }
            }
        }
        if (catalogEnabled && activeTab == SaqzShellProfileTab) {
            SaqzButton(
                label = stringResource(Res.string.shell_open_catalog),
                onClick = { catalogOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SaqzTheme.metrics.horizontalPadding)
                    .testTag(SaqzShellCatalogTag),
                variant = SaqzButtonVariant.Secondary,
            )
        }
        SaqzBottomNav(
            // Sem `when`: a barra só emite id que ela mesma desenha, e todo item da barra
            // tem tela. O despacho é a atribuição.
            items = navItems,
            activeId = activeTab,
            onSelect = { selectedTab = it },
        )
    }
}

@Composable
private fun shellNavItems(financeTabVisible: Boolean) = listOfNotNull(
    SaqzNavItem(SaqzShellHomeTab, stringResource(Res.string.shell_nav_home), SaqzIcons.Home),
    SaqzNavItem(SaqzShellGroupsTab, stringResource(Res.string.shell_nav_groups), SaqzIcons.Users),
    SaqzNavItem(SaqzShellFinanceTab, stringResource(Res.string.shell_nav_finance), SaqzIcons.CreditCard)
        .takeIf { financeTabVisible },
    SaqzNavItem(SaqzShellProfileTab, stringResource(Res.string.shell_nav_profile), SaqzIcons.User),
)

/** Membro comum: três abas. */
@Preview
@Composable
private fun SaqzAppShellPreview() = SaqzTheme {
    SaqzAppShell(
        homeTab = { Box(Modifier.fillMaxWidth().fillMaxSize()) },
        groupsTab = { Box(Modifier.fillMaxWidth().fillMaxSize()) },
    )
}

/** Quem administra algum grupo: as mesmas três mais a Caixa. */
@Preview
@Composable
private fun SaqzAppShellAdminPreview() = SaqzTheme {
    SaqzAppShell(
        financeTabVisible = true,
        homeTab = { Box(Modifier.fillMaxWidth().fillMaxSize()) },
        groupsTab = { Box(Modifier.fillMaxWidth().fillMaxSize()) },
    )
}

@Preview
@Composable
private fun SaqzAppShellDevPreview() = SaqzTheme {
    SaqzAppShell(catalogEnabled = true)
}
