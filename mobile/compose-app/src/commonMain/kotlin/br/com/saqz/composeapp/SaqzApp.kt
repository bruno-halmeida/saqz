package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.composeapp.navigation.SaqzNavHost
import br.com.saqz.access.ui.theme.SaqzTheme
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

@Composable
private fun AccessGate(viewModel: AccessViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    SaqzNavHost(state = state, onIntent = viewModel::onIntent)
}
