package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import br.com.saqz.composeapp.home.SaqzHomeScreen
import br.com.saqz.designsystem.component.SaqzStateHost
import br.com.saqz.designsystem.theme.SaqzTheme

// Native boundary: exactly two accessibility booleans, no core type and no font scale.
@Composable
fun SaqzApp(
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
) {
    SaqzApp(
        environment = SaqzAppEnvironment(
            reduceMotion = reduceMotion,
            reduceTransparency = reduceTransparency,
        ),
    )
}

@Composable
internal fun SaqzApp(environment: SaqzAppEnvironment) {
    SaqzTheme(preferences = environment.toPreferences()) {
        SaqzStateHost(
            state = environment.startupState,
            content = { SaqzHomeScreen(onExploreComponents = {}) },
        )
    }
}
