package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.composeapp.navigation.AccessUiState
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.composeapp.navigation.SaqzNavHost
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * C1 composition root: theme + session gate + the acesso→shell back stack. Everything it
 * needs comes from Koin, which the platform launcher starts with `SaqzPlatformDependencies`
 * before this composes.
 */
@Composable
fun SaqzApp(
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
) {
    SaqzTheme(
        preferences = SaqzAppEnvironment(
            reduceMotion = reduceMotion,
            reduceTransparency = reduceTransparency,
        ).toPreferences(),
    ) {
        AccessGate()
    }
}

/**
 * O catálogo do design system (VUL-51) só existe onde o app roda como dev. O ambiente já
 * chega pela plataforma em `SaqzPlatformDependencies.environment` e vive no
 * [NetworkConfig] do Koin — não há segunda fonte de verdade a inventar aqui, e o flavor
 * prod manda `"prod"`, então a entrada não aparece.
 */
@Composable
private fun AccessGate(
    viewModel: AccessViewModel = koinViewModel(),
    config: NetworkConfig = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    // Latch, e não uma condição lida a cada quadro: depois que a sessão decidiu uma vez, um
    // `Bootstrapping` posterior — o que vem logo depois do login — é carregamento de dentro
    // do app e tem a tela dele. Tela cheia só na abertura.
    //
    // `rememberSaveable` porque girar o aparelho não é abrir o app de novo: com `remember` a
    // rotação devolveria a splash por cima de uma sessão já resolvida.
    var settled by rememberSaveable { mutableStateOf(false) }
    if (state.settledForOpening) settled = true
    if (settled) {
        SaqzNavHost(
            state = state,
            onIntent = viewModel::onIntent,
            catalogEnabled = config.environment == NetworkEnvironment.Dev,
        )
    } else {
        SaqzSplash()
    }
}

/**
 * A sessão respondeu o suficiente para a abertura escolher um destino.
 *
 * São duas esperas em sequência, e a splash cobre as duas: o `observe` do provedor dizer se
 * há alguém autenticado, e — quando há — o bootstrap trocar a sessão pelo estado real. Parar
 * só na primeira devolveria a tela de bootstrap a quem abre o app logado, que é a segunda
 * tela cheia que o fluxo 9 proíbe.
 *
 * `BootstrapError` e `CompletingIdentity` contam como resposta: as duas têm tela própria e
 * pedem ação de quem está olhando.
 */
internal val AccessUiState.settledForOpening: Boolean
    get() = authObserved && session != SessionAccessState.Bootstrapping
