package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    SaqzNavHost(
        state = state,
        onIntent = viewModel::onIntent,
        catalogEnabled = config.environment == NetworkEnvironment.Dev,
    )
}
